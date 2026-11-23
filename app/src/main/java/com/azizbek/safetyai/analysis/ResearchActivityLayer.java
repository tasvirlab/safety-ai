package com.azizbek.safetyai.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Android port of the REBA_Engine Activity Layer, 2026-09-28.
 * Accepts one continuous, equally sampled angle sequence; missing samples are NaN.
 * The two manual facts are nullable until the app collects them explicitly.
 * The repetition decision below applies the case-study's version-3 count and
 * whole-video-duration rule; event detection itself follows the frozen code.
 */
public final class ResearchActivityLayer {
    private ResearchActivityLayer() {}

    public static final double FILTER_CUTOFF_HZ = 6.0;
    public static final double STATIC_MIN_DURATION_S = 60.0;
    public static final double STATIC_MAX_RANGE_DEG = 10.0;
    public static final int REPETITION_MIN_CYCLES = 4;
    public static final double REPETITION_MIN_RATE_PER_MIN = 4.0;
    public static final double REPETITION_MERGE_TOLERANCE_S = 0.3;
    public static final double RAPID_MIN_VELOCITY_DEG_S = 60.0;
    public static final double RAPID_MIN_EXCURSION_DEG = 20.0;
    public static final double RAPID_MAX_DURATION_S = 1.0;
    public static final double RAPID_MOTION_FLOOR_DEG_S = 5.0;

    public static final String[] CHANNELS = {
            "trunk_sagittal_deg", "trunk_lateral_deg", "trunk_twist_deg",
            "neck_flex_deg", "neck_lateral_deg", "upper_arm_r_sagittal_deg",
            "upper_arm_l_sagittal_deg", "upper_arm_r_abd_deg", "upper_arm_l_abd_deg",
            "lower_arm_r_deg", "lower_arm_l_deg", "wrist_r_deg", "wrist_l_deg",
            "knee_r_deg", "knee_l_deg", "knee_max_deg"
    };
    public static final String[] RAPID_CHANNELS = {
            "trunk_sagittal_deg", "trunk_lateral_deg", "upper_arm_r_sagittal_deg",
            "upper_arm_l_sagittal_deg", "upper_arm_r_abd_deg", "upper_arm_l_abd_deg"
    };

    public enum Status { POSITIVE, NEGATIVE, INSUFFICIENT, UNAVAILABLE }

    public static final class Event {
        public final String channel;
        public final int startFrame;
        public final int endFrame;
        public final double peakVelocity;
        public final double excursion;

        Event(String channel, int startFrame, int endFrame, double peakVelocity, double excursion) {
            this.channel = channel;
            this.startFrame = startFrame;
            this.endFrame = endFrame;
            this.peakVelocity = peakVelocity;
            this.excursion = excursion;
        }
    }

    public static final class Criterion {
        public final Status status;
        public final String reason;
        public final String channel;
        public final int count;
        public final double ratePerMinute;
        public final List<Event> events;

        Criterion(Status status, String reason, String channel, int count,
                  double ratePerMinute, List<Event> events) {
            this.status = status;
            this.reason = reason;
            this.channel = channel;
            this.count = count;
            this.ratePerMinute = ratePerMinute;
            this.events = Collections.unmodifiableList(new ArrayList<>(events));
        }
    }

    public static final class Result {
        public final Criterion staticHold;
        public final Criterion repetition;
        public final Criterion rapidMotion;
        public final int score;
        public final boolean fullyEvaluated;
        public final int frameCount;
        public final double fps;

        Result(Criterion staticHold, Criterion repetition, Criterion rapidMotion, int frameCount, double fps) {
            this.staticHold = staticHold;
            this.repetition = repetition;
            this.rapidMotion = rapidMotion;
            this.score = (staticHold.status == Status.POSITIVE ? 1 : 0)
                    + (repetition.status == Status.POSITIVE ? 1 : 0)
                    + (rapidMotion.status == Status.POSITIVE ? 1 : 0);
            this.fullyEvaluated = (staticHold.status == Status.POSITIVE || staticHold.status == Status.NEGATIVE)
                    && (repetition.status == Status.POSITIVE || repetition.status == Status.NEGATIVE)
                    && (rapidMotion.status == Status.POSITIVE || rapidMotion.status == Status.NEGATIVE);
            this.frameCount = frameCount;
            this.fps = fps;
        }
    }

    public static Result evaluate(Map<String, double[]> angles, double fps,
                                  Boolean isLocomotionTask, Boolean unstableBase) {
        return evaluate(angles, fps, isLocomotionTask, unstableBase, null);
    }

    /** A task may limit repetition candidates without changing static or rapid detection. */
    public static Result evaluate(Map<String, double[]> angles, double fps,
                                  Boolean isLocomotionTask, Boolean unstableBase,
                                  List<String> repetitionChannels) {
        if (!Double.isFinite(fps) || fps <= 12.0) throw new IllegalArgumentException("fps must exceed 12 Hz");
        if (repetitionChannels != null) {
            if (repetitionChannels.isEmpty()) throw new IllegalArgumentException("Empty repetition channels");
            for (String channel : repetitionChannels) {
                if (!Arrays.asList(CHANNELS).contains(channel) || channel.equals("knee_max_deg"))
                    throw new IllegalArgumentException("Invalid repetition channel: " + channel);
            }
        }
        int n = -1;
        for (String channel : CHANNELS) {
            double[] x = angles.get(channel);
            if (x == null) throw new IllegalArgumentException("Missing angle channel: " + channel);
            if (n < 0) n = x.length;
            if (n != x.length) throw new IllegalArgumentException("Angle channels have different lengths");
        }
        if (n < 1) throw new IllegalArgumentException("Empty angle sequence");
        Map<String, double[]> filtered = new LinkedHashMap<>();
        for (String channel : CHANNELS) filtered.put(channel, causalFilter(angles.get(channel), fps));
        return new Result(staticHold(filtered, fps), repetition(angles, filtered, fps,
                        isLocomotionTask, repetitionChannels),
                rapid(filtered, angles, fps, unstableBase), n, fps);
    }

    /** Retain raw diagnostic cycles while withholding the repetition point. */
    public static Result withUnreliableRepetition(Result result) {
        Criterion observed = result.repetition;
        Criterion withheld = new Criterion(Status.INSUFFICIENT,
                "pose_geometry_unreliable", observed.channel, observed.count,
                observed.ratePerMinute, observed.events);
        return new Result(result.staticHold, withheld, result.rapidMotion,
                result.frameCount, result.fps);
    }

    private static List<int[]> validRuns(double[] x) {
        List<int[]> runs = new ArrayList<>();
        for (int i = 0; i < x.length; ) {
            if (!Double.isFinite(x[i])) { i++; continue; }
            int start = i++;
            while (i < x.length && Double.isFinite(x[i])) i++;
            runs.add(new int[]{start, i});
        }
        return runs;
    }

    /** scipy.signal.butter(2, 6 / (fps/2)) + lfilter initialized at x[0]. */
    public static double[] causalFilter(double[] x, double fps) {
        if (fps <= 12.0) throw new IllegalArgumentException("6 Hz cutoff must be below Nyquist");
        double k = Math.tan(Math.PI * FILTER_CUTOFF_HZ / fps);
        double norm = 1.0 / (1.0 + Math.sqrt(2.0) * k + k * k);
        double b0 = k * k * norm, b1 = 2.0 * b0, b2 = b0;
        double a1 = 2.0 * (k * k - 1.0) * norm;
        double a2 = (1.0 - Math.sqrt(2.0) * k + k * k) * norm;
        double[] out = new double[x.length];
        Arrays.fill(out, Double.NaN);
        for (int[] run : validRuns(x)) {
            int s = run[0], end = run[1];
            if (end - s < 3) { System.arraycopy(x, s, out, s, end - s); continue; }
            double x1 = x[s], x2 = x[s], y1 = x[s], y2 = x[s];
            for (int i = s; i < end; i++) {
                double y = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
                out[i] = y;
                x2 = x1; x1 = x[i]; y2 = y1; y1 = y;
            }
        }
        return out;
    }

    private static Criterion staticHold(Map<String, double[]> filtered, double fps) {
        boolean evaluable = false;
        String bestChannel = null;
        int bestLength = -1;
        List<Event> qualifying = new ArrayList<>();
        for (String channel : CHANNELS) {
            double[] x = filtered.get(channel);
            int channelBestLength = -1;
            Event channelBest = null;
            boolean channelEvaluable = false;
            for (int[] run : validRuns(x)) {
                if ((run[1] - run[0]) / fps > STATIC_MIN_DURATION_S) channelEvaluable = true;
                ArrayDeque<Integer> max = new ArrayDeque<>(), min = new ArrayDeque<>();
                int lo = run[0];
                int sBest = lo, eBest = lo - 1;
                for (int j = lo; j < run[1]; j++) {
                    while (!max.isEmpty() && x[max.peekLast()] <= x[j]) max.removeLast();
                    while (!min.isEmpty() && x[min.peekLast()] >= x[j]) min.removeLast();
                    max.addLast(j); min.addLast(j);
                    while (x[max.peekFirst()] - x[min.peekFirst()] > STATIC_MAX_RANGE_DEG) {
                        lo++;
                        if (max.peekFirst() < lo) max.removeFirst();
                        if (min.peekFirst() < lo) min.removeFirst();
                    }
                    if (j - lo + 1 > eBest - sBest + 1) { sBest = lo; eBest = j; }
                }
                // Python keeps the longest window of each channel; ties keep the first.
                int length = eBest - sBest + 1;
                if (length > channelBestLength) {
                    channelBestLength = length;
                    channelBest = new Event(channel, sBest, eBest, 0.0, 0.0);
                }
            }
            evaluable |= channelEvaluable;
            if (channelBestLength > bestLength) { bestLength = channelBestLength; bestChannel = channel; }
            if (channelEvaluable && channelBest != null && channelBestLength / fps > STATIC_MIN_DURATION_S)
                qualifying.add(channelBest);
        }
        return new Criterion(!qualifying.isEmpty() ? Status.POSITIVE :
                evaluable ? Status.NEGATIVE : Status.INSUFFICIENT,
                evaluable ? null : "insufficient_observation_duration",
                bestChannel, 0, 0.0, qualifying);
    }

    private static double median(double[] a) {
        Arrays.sort(a);
        int m = a.length / 2;
        return a.length % 2 == 0 ? (a[m - 1] + a[m]) / 2.0 : a[m];
    }

    private static double percentile(double[] a, double p) {
        double[] copy = a.clone(); Arrays.sort(copy);
        double index = (copy.length - 1) * p;
        int lo = (int) index;
        return copy[lo] + (copy[Math.min(lo + 1, copy.length - 1)] - copy[lo]) * (index - lo);
    }

    private static double noiseFloor(double[] x) {
        List<Double> finite = new ArrayList<>();
        for (double v : x) if (Double.isFinite(v)) finite.add(v);
        if (finite.size() < 10) return 0.1;
        double[] residual = new double[finite.size() - 4];
        for (int i = 2; i < finite.size() - 2; i++) {
            double mean = 0.0;
            for (int j = i - 2; j <= i + 2; j++) mean += finite.get(j) / 5.0;
            residual[i - 2] = finite.get(i) - mean;
        }
        if (residual.length < 3) return 0.1;
        double centre = median(residual.clone());
        for (int i = 0; i < residual.length; i++) residual[i] = Math.abs(residual[i] - centre);
        return Math.max(0.1, 1.4826 * median(residual));
    }

    private static List<int[]> bouts(double[] filtered, double fps, double noise) {
        int n = filtered.length, window = Math.max(3, (int) Math.round(1.5 * fps));
        boolean[] active = new boolean[n];
        List<int[]> runs = validRuns(filtered);
        for (int[] run : runs) {
            if (run[1] - run[0] < window) continue;
            for (int i = run[0]; i < run[1]; i++) {
                // scipy.ndimage.maximum_filter1d(size=even, origin=0) uses
                // [i-size/2, i+size/2-1] with nearest-edge padding.
                int left = i - window / 2, right = left + window - 1;
                double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
                for (int j = left; j <= right; j++) {
                    double v = filtered[Math.max(run[0], Math.min(run[1] - 1, j))];
                    low = Math.min(low, v); high = Math.max(high, v);
                }
                active[i] = high - low > 3.0 * noise;
            }
        }
        int mergeGap = (int) Math.round(fps);
        for (int[] run : runs) {
            int i = run[0];
            while (i < run[1]) {
                if (active[i]) { i++; continue; }
                int s = i;
                while (i < run[1] && !active[i]) i++;
                if (i - s <= mergeGap && s > run[0] && i < run[1])
                    Arrays.fill(active, s, i, true);
            }
        }
        List<int[]> found = new ArrayList<>();
        for (int i = 0; i < n; ) {
            if (!active[i]) { i++; continue; }
            int s = i++;
            while (i < n && active[i]) i++;
            if (i - s >= Math.round(2.0 * fps)) found.add(new int[]{s, i});
        }
        return found;
    }

    private static final class Evidence {
        final String channel;
        final double score;
        final int count;
        final int validFrames;
        final List<Event> cycles;
        Evidence(String channel, double score, int validFrames, List<Event> cycles) {
            this.channel = channel; this.score = score;
            this.validFrames = validFrames; this.cycles = cycles; this.count = cycles.size();
        }
    }

    private static Evidence repetitionEvidence(String channel, double[] raw, double[] filtered, double fps) {
        int finite = 0;
        for (double v : raw) if (Double.isFinite(v)) finite++;
        if (finite / (double) raw.length < 0.8) return null;
        double noise = noiseFloor(filtered);
        List<int[]> bouts = bouts(filtered, fps, noise);
        if (bouts.isEmpty()) return null;
        List<Event> cycles = new ArrayList<>();
        List<Double> amplitudes = new ArrayList<>();
        for (int[] b : bouts) {
            double[] seg = Arrays.copyOfRange(filtered, b[0], b[1]);
            if (seg.length < 8) continue;
            double lo = percentile(seg, 0.1), hi = percentile(seg, 0.9);
            double l = lo + 0.25 * (hi - lo), h = hi - 0.25 * (hi - lo);
            if (h <= l || h - l < 3.0 * noise) continue;
            amplitudes.add(h - l);
            cycles.addAll(countCycles(channel, seg, b[0], l, h));
        }
        if (cycles.size() < 2) return null;
        double intervalMean = 0.0, variance = 0.0;
        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < cycles.size(); i++) {
            double dt = (cycles.get(i).endFrame - cycles.get(i - 1).endFrame) / fps;
            if (dt > 0.0) intervals.add(dt);
        }
        double regularity = 0.5;
        if (!intervals.isEmpty()) {
            for (double dt : intervals) intervalMean += dt / intervals.size();
            if (intervalMean > 0) {
                for (double dt : intervals) variance += Math.pow(dt - intervalMean, 2) / intervals.size();
                regularity = 1.0 / (1.0 + Math.sqrt(variance) / intervalMean);
            }
        }
        double amplitude = 0.0;
        for (double v : amplitudes) amplitude += v / amplitudes.size();
        return new Evidence(channel, amplitude / Math.max(noise, 0.1) * regularity, finite, cycles);
    }

    private static List<Event> countCycles(String channel, double[] segment, int offset, double low, double high) {
        boolean lowAnchor = false, found = false;
        for (double v : segment) {
            if (v <= low) { lowAnchor = true; found = true; break; }
            if (v >= high) { lowAnchor = false; found = true; break; }
        }
        if (!found) return Collections.emptyList();
        final int SEEK = 0, ANCHOR = 1, EXCURSION = 2, CONFIRMING = 3;
        int state = SEEK, cycleStart = -1, confirmStart = -1;
        boolean oppositeReached = false;
        List<Event> cycles = new ArrayList<>();
        for (int i = 0; i < segment.length; i++) {
            double v = segment[i];
            boolean anchor = lowAnchor ? v <= low : v >= high;
            boolean opposite = lowAnchor ? v >= high : v <= low;
            switch (state) {
                case SEEK: if (anchor) state = ANCHOR; break;
                case ANCHOR:
                    if (!anchor) { state = EXCURSION; cycleStart = i; oppositeReached = false; }
                    break;
                case EXCURSION:
                    if (!oppositeReached && opposite) oppositeReached = true;
                    else if (!oppositeReached && anchor) { state = ANCHOR; cycleStart = -1; }
                    else if (oppositeReached && anchor) { state = CONFIRMING; confirmStart = i; }
                    break;
                case CONFIRMING:
                    if (!anchor) { state = EXCURSION; confirmStart = -1; }
                    else if (i - confirmStart + 1 >= 5) {
                        cycles.add(new Event(channel, offset + cycleStart, offset + confirmStart, 0.0, 0.0));
                        state = ANCHOR; cycleStart = -1; oppositeReached = false; confirmStart = -1;
                    }
                    break;
                default: throw new IllegalStateException();
            }
        }
        return cycles;
    }

    private static Criterion repetition(Map<String, double[]> raw, Map<String, double[]> filtered,
                                        double fps, Boolean isLocomotionTask,
                                        List<String> repetitionChannels) {
        List<Evidence> candidates = new ArrayList<>();
        for (String channel : CHANNELS) {
            if (channel.equals("knee_max_deg") ||
                    (repetitionChannels != null && !repetitionChannels.contains(channel))) continue;
            Evidence evidence = repetitionEvidence(channel, raw.get(channel), filtered.get(channel), fps);
            if (evidence != null) candidates.add(evidence);
        }
        if (candidates.isEmpty()) return new Criterion(Status.INSUFFICIENT,
                "no_candidate_channel_met_minimum_evidence", null, 0, 0.0, Collections.emptyList());
        // Python sort is stable; equal scores retain CHANNELS order.
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        Evidence selected = candidates.get(0);
        List<Event> cycles = new ArrayList<>(selected.cycles);
        if (candidates.size() > 1) {
            for (Event next : candidates.get(1).cycles) {
                boolean duplicate = false;
                for (Event own : selected.cycles) {
                    // Match the frozen Python implementation's division before
                    // subtraction, including the 0.3 s floating-point boundary.
                    if (Math.abs(next.endFrame / fps - own.endFrame / fps) <= REPETITION_MERGE_TOLERANCE_S) {
                        duplicate = true; break;
                    }
                }
                if (!duplicate) cycles.add(next);
            }
            cycles.sort(Comparator.comparingInt(e -> e.endFrame));
        }
        double videoSeconds = raw.get(selected.channel).length / fps;
        double rate = cycles.size() / videoSeconds * 60.0;
        if (isLocomotionTask == null) return new Criterion(Status.UNAVAILABLE,
                "is_locomotion_task_not_provided", selected.channel, cycles.size(), rate, cycles);
        if (isLocomotionTask) return new Criterion(Status.NEGATIVE,
                "locomotion_task_excluded", selected.channel, cycles.size(), rate, cycles);
        return new Criterion(meetsRepetitionRule(cycles.size(), videoSeconds)
                ? Status.POSITIVE : Status.NEGATIVE,
                null, selected.channel, cycles.size(), rate, cycles);
    }

    /** Case-study rule: at least four observed cycles AND at least four per video minute. */
    public static boolean meetsRepetitionRule(int cycleCount, double videoDurationSeconds) {
        if (cycleCount < 0 || !Double.isFinite(videoDurationSeconds) || videoDurationSeconds <= 0.0)
            throw new IllegalArgumentException("Invalid repetition count or video duration");
        return cycleCount >= REPETITION_MIN_CYCLES &&
                cycleCount * 60.0 >= REPETITION_MIN_RATE_PER_MIN * videoDurationSeconds;
    }

    private static double[] trailingRegression(double[] filtered, double fps) {
        double[] velocity = new double[filtered.length];
        Arrays.fill(velocity, Double.NaN);
        for (int[] run : validRuns(filtered)) {
            for (int i = run[0] + 2; i < run[1]; i++) {
                int w = Math.min(i - run[0] + 1, 7);
                int s = i - w + 1;
                double middle = (w - 1) / 2.0, numerator = 0.0, denominator = 0.0;
                for (int j = 0; j < w; j++) {
                    numerator += (j - middle) * filtered[s + j];
                    denominator += (j - middle) * (j - middle);
                }
                velocity[i] = numerator / denominator * fps;
            }
        }
        return velocity;
    }

    private static Criterion rapid(Map<String, double[]> filtered, Map<String, double[]> raw,
                                   double fps, Boolean unstableBase) {
        boolean hasData = false;
        List<Event> events = new ArrayList<>();
        for (String channel : RAPID_CHANNELS) {
            double[] x = filtered.get(channel);
            int finite = 0;
            for (double v : raw.get(channel)) if (Double.isFinite(v)) finite++;
            if (finite >= 2) hasData = true;
            double[] velocity = trailingRegression(x, fps);
            for (int i = 0; i < velocity.length; ) {
                if (!Double.isFinite(velocity[i]) || Math.abs(velocity[i]) < RAPID_MOTION_FLOOR_DEG_S) { i++; continue; }
                int p = i++;
                double peak = Math.abs(velocity[p]);
                while (i < velocity.length && Double.isFinite(velocity[i]) && Math.abs(velocity[i]) >= RAPID_MOTION_FLOOR_DEG_S) {
                    peak = Math.max(peak, Math.abs(velocity[i])); i++;
                }
                int a = p - 1, b = i - 1;
                double excursion = Math.abs(x[b] - x[a]);
                if ((b - a) / fps <= RAPID_MAX_DURATION_S && peak >= RAPID_MIN_VELOCITY_DEG_S && excursion >= RAPID_MIN_EXCURSION_DEG)
                    events.add(new Event(channel, a, b, peak, excursion));
            }
        }
        if (Boolean.TRUE.equals(unstableBase) || !events.isEmpty())
            return new Criterion(Status.POSITIVE, null, events.isEmpty() ? null : events.get(0).channel,
                    events.size(), 0.0, events);
        if (unstableBase == null)
            return new Criterion(Status.UNAVAILABLE, "unstable_base_not_provided", null, 0, 0.0, events);
        if (!hasData)
            return new Criterion(Status.INSUFFICIENT, "no_valid_data", null, 0, 0.0, events);
        return new Criterion(Status.NEGATIVE, null, null, 0, 0.0, events);
    }
}
