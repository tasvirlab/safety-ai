package com.azizbek.safetyai.analysis;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResearchActivityLayerTest {
    private Map<String, double[]> flat(int frames) {
        Map<String, double[]> angles = new LinkedHashMap<>();
        for (String channel : ResearchActivityLayer.CHANNELS) angles.put(channel, new double[frames]);
        return angles;
    }

    @Test public void shortClipNeedsFourActualCycles() {
        assertFalse(ResearchActivityLayer.meetsRepetitionRule(2, 10.0));
        assertFalse(ResearchActivityLayer.meetsRepetitionRule(3, 10.0));
        assertTrue(ResearchActivityLayer.meetsRepetitionRule(4, 10.0));
    }

    @Test public void longerClipAlsoNeedsFourPerMinute() {
        assertTrue(ResearchActivityLayer.meetsRepetitionRule(4, 60.0));
        assertFalse(ResearchActivityLayer.meetsRepetitionRule(4, 61.0));
        assertTrue(ResearchActivityLayer.meetsRepetitionRule(5, 70.0));
    }

    @Test public void staticRequiresStrictlyMoreThanSixtySeconds() {
        assertEquals(ResearchActivityLayer.Status.INSUFFICIENT,
                ResearchActivityLayer.evaluate(flat(3600), 60.0, false, false).staticHold.status);
        assertEquals(ResearchActivityLayer.Status.POSITIVE,
                ResearchActivityLayer.evaluate(flat(3601), 60.0, false, false).staticHold.status);
    }

    @Test public void quietSeventySecondClipHasNoRepetitionOrRapidEvent() {
        ResearchActivityLayer.Result result = ResearchActivityLayer.evaluate(flat(4200), 60.0, false, false);
        assertEquals(0, result.repetition.count);
        assertEquals(ResearchActivityLayer.Status.INSUFFICIENT, result.repetition.status);
        assertEquals(ResearchActivityLayer.Status.NEGATIVE, result.rapidMotion.status);
        assertEquals(1, result.score);
    }

    private ResearchActivityLayer.Result ramp(double speed) {
        Map<String, double[]> angles = flat(300);
        double[] x = angles.get("trunk_sagittal_deg");
        for (int i = 0; i < x.length; i++) {
            x[i] = i < 60 ? 0.0 : i < 90 ? (i - 60) * speed / 60.0 : 0.5 * speed;
        }
        return ResearchActivityLayer.evaluate(angles, 60.0, false, false,
                Arrays.asList("trunk_sagittal_deg"));
    }

    @Test public void rapidVelocityUsesTheFrozenSixtyDegreeThreshold() {
        assertEquals(ResearchActivityLayer.Status.NEGATIVE, ramp(50.0).rapidMotion.status);
        assertEquals(ResearchActivityLayer.Status.POSITIVE, ramp(80.0).rapidMotion.status);
        assertEquals(1, ramp(80.0).rapidMotion.count);
    }

    @Test public void unknownGroundStabilityIsNotSilentlyNegative() {
        ResearchActivityLayer.Result result = ResearchActivityLayer.evaluate(flat(4200), 60.0, false, null);
        assertEquals(ResearchActivityLayer.Status.UNAVAILABLE, result.rapidMotion.status);
        assertFalse(result.fullyEvaluated);
    }

    @Test public void taskChannelsExcludeUnrelatedMotionWithoutChangingOtherCriteria() {
        Map<String, double[]> angles = flat(900);
        double[] trunk = angles.get("trunk_sagittal_deg");
        for (int i = 0; i < trunk.length; i++) trunk[i] = 30.0 + 25.0 * Math.sin(2.0 * Math.PI * i / 180.0);
        ResearchActivityLayer.Result generic = ResearchActivityLayer.evaluate(angles, 60.0, false, false);
        ResearchActivityLayer.Result lifting = ResearchActivityLayer.evaluate(angles, 60.0, false, false,
                Arrays.asList("trunk_sagittal_deg"));
        ResearchActivityLayer.Result wristOnly = ResearchActivityLayer.evaluate(angles, 60.0, false, false,
                Arrays.asList("wrist_r_deg", "wrist_l_deg"));
        assertEquals(4, lifting.repetition.count);
        assertEquals(ResearchActivityLayer.Status.POSITIVE, lifting.repetition.status);
        assertEquals(0, wristOnly.repetition.count);
        assertEquals(generic.staticHold.status, wristOnly.staticHold.status);
        assertEquals(generic.rapidMotion.status, wristOnly.rapidMotion.status);
    }

    @Test public void unreliablePoseRetainsDiagnosticCountButWithholdsThePoint() {
        Map<String, double[]> angles = flat(900);
        double[] trunk = angles.get("trunk_sagittal_deg");
        for (int i = 0; i < trunk.length; i++) trunk[i] = 30.0 + 25.0 * Math.sin(2.0 * Math.PI * i / 180.0);
        ResearchActivityLayer.Result original = ResearchActivityLayer.evaluate(angles, 60.0, false, false,
                Arrays.asList("trunk_sagittal_deg"));
        ResearchActivityLayer.Result withheld = ResearchActivityLayer.withUnreliableRepetition(original);
        assertEquals(original.repetition.count, withheld.repetition.count);
        assertEquals(ResearchActivityLayer.Status.INSUFFICIENT, withheld.repetition.status);
        assertEquals(original.score - 1, withheld.score);
        assertFalse(withheld.fullyEvaluated);
    }
}
