package com.azizbek.safetyai.reports

import com.azizbek.safetyai.analysis.FrameAnalysis
import com.azizbek.safetyai.analysis.Joint3D
import org.junit.Assert.*
import org.junit.Test

class PoseFloorAnchorTest {
    private fun frame(leftZ: Float, rightZ: Float = leftZ, feetValid: Boolean = true): FrameAnalysis {
        val joints = List(21) { index ->
            Joint3D(0f, 0f, when (index) {
                16, 18 -> leftZ
                17, 19 -> rightZ
                else -> 0f
            }, 1f, if (index in 16..19) feetValid else true)
        }
        return FrameAnalysis(0L, emptyList(), joints, emptyMap())
    }

    @Test fun crouchingMovesThePelvisAboveTheFloorInsteadOfFixingItsHeight() {
        val offsets = poseFloorOffsets(List(5) { frame(-1f) } + List(5) { frame(-0.6f) })
        assertEquals(-1f, offsets.first(), 0.0001f)
        assertEquals(-0.6f, offsets.last(), 0.0001f)
    }

    @Test fun liftingOneFootDoesNotRaiseTheGround() {
        val offsets = poseFloorOffsets(List(7) { frame(-1f, -0.4f) })
        assertTrue(offsets.all { it == -1f })
    }

    @Test fun isolatedFootSpikeAndMissingFrameDoNotMoveTheGround() {
        val frames = List(9) { index -> when (index) {
            4 -> frame(-2f)
            6 -> frame(-1f, feetValid = false)
            else -> frame(-1f)
        } }
        assertTrue(poseFloorOffsets(frames).all { it == -1f })
    }
}
