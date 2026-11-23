package com.azizbek.safetyai.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Pose2DGapRepairTest {
    private fun trajectory(frames: Int): List<CanonicalPose2D> =
        List(frames) { index ->
            CanonicalPose2D(
                coordinates = FloatArray(42) { coordinate ->
                    index.toFloat() * 2f + coordinate
                },
                valid = BooleanArray(21) { true },
                confidence = FloatArray(21) { 0.9f }
            )
        }

    private fun removeJoint(
        source: List<CanonicalPose2D>,
        joint: Int,
        range: IntRange
    ): List<CanonicalPose2D> = source.mapIndexed { index, original ->
        val coordinates = original.coordinates.copyOf()
        val valid = original.valid.copyOf()
        if (index in range) {
            coordinates[joint * 2] = Float.NaN
            coordinates[joint * 2 + 1] = Float.NaN
            valid[joint] = false
        }
        CanonicalPose2D(coordinates, valid, original.confidence.copyOf())
    }

    @Test fun boundedBilateralOcclusionsRecoverWithoutChangingObservations() {
        val full = trajectory(100)
        val masked = removeJoint(removeJoint(full, 8, 20..57), 10, 15..68)
        val repaired = Pose2DGapRepair.repair(masked)
        assertEquals(38, repaired.imputedCounts[8])
        assertEquals(54, repaired.imputedCounts[10])
        assertEquals(0, repaired.remainingIncompleteFrames)
        for (index in full.indices) {
            for (joint in 0 until 21) {
                val expected = full[index].coordinates[joint * 2]
                assertEquals(expected, repaired.frames[index].coordinates[joint * 2], 0.0001f)
                if (masked[index].valid[joint]) {
                    assertEquals(masked[index].confidence[joint], repaired.frames[index].confidence[joint])
                } else {
                    assertEquals(0f, repaired.frames[index].confidence[joint])
                    assertTrue(repaired.imputedMaskByFrame[index] and (1 shl joint) != 0)
                }
            }
        }
    }

    @Test fun longAndUnanchoredGapsRemainInvalid() {
        val full = trajectory(100)
        val longGap = Pose2DGapRepair.repair(removeJoint(full, 8, 10..70))
        assertEquals(0, longGap.imputedCounts[8])
        assertFalse(longGap.frames[40].valid[8])
        val edgeGap = Pose2DGapRepair.repair(removeJoint(full, 10, 0..10))
        assertFalse(edgeGap.frames[0].valid[10])
        val noAnchor = Pose2DGapRepair.repair(removeJoint(full, 9, 0..99))
        assertFalse(noAnchor.frames[50].valid[9])
    }

    @Test fun unoccludedVideoIsUnchangedAndEdgeHoldIsBounded() {
        val full = trajectory(40)
        val clean = Pose2DGapRepair.repair(full)
        assertEquals(0, clean.imputedMaskByFrame.count { it != 0 })
        full.indices.forEach { frame ->
            assertTrue(clean.frames[frame].coordinates.contentEquals(full[frame].coordinates))
            assertTrue(clean.frames[frame].confidence.contentEquals(full[frame].confidence))
        }

        val held = Pose2DGapRepair.repair(removeJoint(full, 16, 0..9))
        assertEquals(10, held.imputedCounts[16])
        assertEquals(full[10].coordinates[32], held.frames[0].coordinates[32], 0f)
        assertEquals(0f, held.frames[0].confidence[16], 0f)
        assertEquals(0, held.remainingIncompleteFrames)
    }
}
