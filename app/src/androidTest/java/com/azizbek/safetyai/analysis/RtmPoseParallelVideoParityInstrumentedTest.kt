package com.azizbek.safetyai.analysis

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RtmPoseParallelVideoParityInstrumentedTest {
    @Test
    fun compareFullVideoSampledPoses() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val video = File(
            context.filesDir,
            "videos/2a048946-9671-4c3d-b1bf-13de9727a71d.mp4"
        )
        require(video.isFile && video.length() > 0L) {
            "Benchmark video is missing"
        }

        val baselineEstimator = RtmPoseVideoEstimator(context)
        val parallelEstimator = ParallelRunner(context)

        try {
            baselineEstimator.estimate(video)
            parallelEstimator.estimate(video)

            val baselineStarted = SystemClock.elapsedRealtimeNanos()
            val baseline = baselineEstimator.estimate(video)
            val baselineMs =
                (SystemClock.elapsedRealtimeNanos() - baselineStarted) /
                    1_000_000.0

            val parallelStarted = SystemClock.elapsedRealtimeNanos()
            val candidate = parallelEstimator.estimate(video)
            val parallelMs =
                (SystemClock.elapsedRealtimeNanos() - parallelStarted) /
                    1_000_000.0

            require(candidate.samples.size == baseline.sourceFrameCount)

            val stride = baseline.sourceFps / 15
            val repeatFactor = 60 / baseline.sourceFps
            var comparedKeyframes = 0
            var missingKeyframes = 0
            var validityDisagreements = 0
            var maxCoordinateDifference = 0f
            var maxConfidenceDifference = 0f

            for (sourceIndex in 0 until baseline.sourceFrameCount) {
                val isKeyframe =
                    sourceIndex % stride == 0 ||
                        sourceIndex == baseline.sourceFrameCount - 1
                if (!isKeyframe) continue

                val actual = candidate.samples[sourceIndex]
                if (actual == null) {
                    missingKeyframes++
                    continue
                }

                comparedKeyframes++
                val expected = baseline.frames[sourceIndex * repeatFactor]

                for (i in expected.valid.indices) {
                    if (expected.valid[i] != actual.valid[i]) {
                        validityDisagreements++
                    }
                }

                for (i in expected.coordinates.indices) {
                    val a = expected.coordinates[i]
                    val b = actual.coordinates[i]
                    if (a.isNaN() && b.isNaN()) continue
                    if (!a.isFinite() || !b.isFinite()) {
                        validityDisagreements++
                    } else {
                        maxCoordinateDifference = maxOf(
                            maxCoordinateDifference,
                            abs(a - b)
                        )
                    }
                }

                for (i in expected.confidence.indices) {
                    val a = expected.confidence[i]
                    val b = actual.confidence[i]
                    if (a.isNaN() && b.isNaN()) continue
                    if (!a.isFinite() || !b.isFinite()) {
                        validityDisagreements++
                    } else {
                        maxConfidenceDifference = maxOf(
                            maxConfidenceDifference,
                            abs(a - b)
                        )
                    }
                }
            }

            val parityPassed =
                missingKeyframes == 0 &&
                    validityDisagreements == 0 &&
                    maxCoordinateDifference == 0f &&
                    maxConfidenceDifference == 0f &&
                    candidate.decodeFailures == baseline.decodeFailureCount &&
                    candidate.noPersonFrames == baseline.noPersonFrameCount

            val report = buildString {
                appendLine("RTMPOSE FULL-VIDEO PARALLEL EXPERIMENT")
                appendLine("source_frames=${baseline.sourceFrameCount}")
                appendLine("source_fps=${baseline.sourceFps}")
                appendLine("inference_fps=15")
                appendLine("compared_keyframes=$comparedKeyframes")
                appendLine("missing_keyframes=$missingKeyframes")
                appendLine("baseline_warm_ms=$baselineMs")
                appendLine("parallel_warm_ms=$parallelMs")
                appendLine("parallel_pairs=${candidate.parallelPairs}")
                appendLine("second_frame_fallbacks=${candidate.fallbacks}")
                appendLine("parallel_detector_runs=${candidate.detectorRuns}")
                appendLine(
                    "decode_failures=${candidate.decodeFailures}/" +
                        baseline.decodeFailureCount
                )
                appendLine(
                    "no_person_frames=${candidate.noPersonFrames}/" +
                        baseline.noPersonFrameCount
                )
                appendLine("validity_disagreements=$validityDisagreements")
                appendLine(
                    "max_2d_coordinate_abs_px=$maxCoordinateDifference"
                )
                appendLine(
                    "max_2d_confidence_abs=$maxConfidenceDifference"
                )
                appendLine("parity_passed=$parityPassed")
            }

            File(
                context.cacheDir,
                "rtmpose_parallel_video_report.txt"
            ).writeText(report)

            require(parityPassed) {
                "Full-video 2D parity failed; inspect the report"
            }
        } finally {
            baselineEstimator.close()
            parallelEstimator.close()
        }
    }

    private data class CandidateResult(
        val samples: List<CanonicalPose2D?>,
        val decodeFailures: Int,
        val noPersonFrames: Int,
        val detectorRuns: Int,
        val parallelPairs: Int,
        val fallbacks: Int
    )

    private class ParallelRunner(context: Context) : AutoCloseable {
        private val first = RtmPoseOnDeviceSession(
            context,
            useXnnpack = true,
            xnnpackThreads = 4
        )
        private val second = try {
            RtmPoseOnDeviceSession(
                context,
                useXnnpack = true,
                xnnpackThreads = 4
            )
        } catch (error: Throwable) {
            first.close()
            throw error
        }
        private val executor = Executors.newFixedThreadPool(2)

        suspend fun estimate(video: File): CandidateResult {
            val decoder = VideoFrameDecoder()
            val info = decoder.inspect(video)
            require(info.sourceFps == 30 || info.sourceFps == 60)

            val stride = info.sourceFps / 15
            val samples =
                arrayOfNulls<CanonicalPose2D>(info.sourceFrameCount)
            val tracker = RtmPosePersonTracker()
            val pending = mutableListOf<Pair<Int, Bitmap>>()

            var cachedBox: RtmPoseBoundingBox? = null
            var decodeFailures = 0
            var noPersonFrames = 0
            var detectorRuns = 0
            var parallelPairs = 0
            var fallbacks = 0

            fun invalidPose() = CanonicalPose2D(
                coordinates = FloatArray(21 * 2) { Float.NaN },
                valid = BooleanArray(21),
                confidence = FloatArray(21) { Float.NaN }
            )

            fun detect(bitmap: Bitmap): RtmPoseBoundingBox? {
                detectorRuns++
                val input =
                    RtmPoseImagePreprocessor.createDetectorInput(bitmap)
                val output = first.runDetector(input.tensor)
                val boxes = RtmPosePostprocessor.decodeDetector(
                    detections = output.detections,
                    labels = output.labels,
                    resizeRatio = input.resizeRatio,
                    scoreThreshold = 0.3f
                )
                val centerX = bitmap.width * 0.5f
                val centerY = bitmap.height * 0.5f

                return boxes.minByOrNull { box ->
                    val boxCenterX =
                        (box.left + box.right) * 0.5f
                    val boxCenterY =
                        (box.top + box.bottom) * 0.5f
                    val dx =
                        (boxCenterX - centerX) / bitmap.width
                    val dy =
                        (boxCenterY - centerY) / bitmap.height
                    dx * dx + dy * dy
                }
            }

            fun pose(
                bitmap: Bitmap,
                box: RtmPoseBoundingBox,
                session: RtmPoseOnDeviceSession
            ): RtmPosePerson2D {
                val input =
                    RtmPoseImagePreprocessor.createPoseInput(bitmap, box)
                val output = session.runPose(input)
                val decoded = RtmPosePostprocessor.decodePose(
                    simccX = output.simccX,
                    simccY = output.simccY,
                    boundingBox = box
                )
                return RtmPosePerson2D(
                    boundingBox = box,
                    coordinates = decoded.coordinates,
                    scores = decoded.scores
                )
            }

            fun accept(index: Int, person: RtmPosePerson2D?) {
                val people = person?.let { listOf(it) }.orEmpty()
                val selected = tracker.select(people)

                samples[index] = if (selected == null) {
                    noPersonFrames++
                    invalidPose()
                } else {
                    RtmPoseCanonical21.map(selected)
                }

                val confidentJoints = person?.scores
                    ?.take(17)
                    ?.count { it.isFinite() && it >= 0.30f }
                    ?: 0

                if (confidentJoints < 8) {
                    cachedBox = null
                }
            }

            fun processSingle(frame: Pair<Int, Bitmap>) {
                val box = cachedBox
                    ?: detect(frame.second).also { cachedBox = it }

                val person = box?.let {
                    pose(frame.second, it, first)
                }
                accept(frame.first, person)
            }

            fun processPair(
                firstFrame: Pair<Int, Bitmap>,
                secondFrame: Pair<Int, Bitmap>
            ) {
                val box = cachedBox
                    ?: detect(firstFrame.second).also { cachedBox = it }

                if (box == null) {
                    accept(firstFrame.first, null)
                    processSingle(secondFrame)
                    return
                }

                parallelPairs++
                val firstJob = executor.submit(Callable {
                    pose(firstFrame.second, box, first)
                })
                val secondJob = executor.submit(Callable {
                    pose(secondFrame.second, box, second)
                })

                val firstPerson = firstJob.get()
                val speculativeSecondPerson = secondJob.get()
                accept(firstFrame.first, firstPerson)

                if (cachedBox == null) {
                    fallbacks++
                    processSingle(secondFrame)
                } else {
                    accept(secondFrame.first, speculativeSecondPerson)
                }
            }

            fun flush() {
                if (pending.isEmpty()) return
                try {
                    if (pending.size == 2) {
                        processPair(pending[0], pending[1])
                    } else {
                        processSingle(pending[0])
                    }
                } finally {
                    pending.forEach { it.second.recycle() }
                    pending.clear()
                }
            }

            decoder.decode(video) { frame ->
                val selected =
                    frame.sourceIndex % stride == 0 ||
                        frame.sourceIndex == info.sourceFrameCount - 1

                if (frame.bitmap == null) {
                    flush()
                    decodeFailures++
                    if (selected) {
                        tracker.select(emptyList())
                        samples[frame.sourceIndex] = invalidPose()
                    }
                } else if (selected) {
                    val copy = frame.bitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                    ) ?: error("Could not copy a selected frame")

                    pending += frame.sourceIndex to copy
                    if (pending.size == 2) flush()
                }
            }
            flush()

            return CandidateResult(
                samples = samples.toList(),
                decodeFailures = decodeFailures,
                noPersonFrames = noPersonFrames,
                detectorRuns = detectorRuns,
                parallelPairs = parallelPairs,
                fallbacks = fallbacks
            )
        }

        override fun close() {
            executor.shutdown()
            try {
                first.close()
            } finally {
                second.close()
            }
        }
    }
}