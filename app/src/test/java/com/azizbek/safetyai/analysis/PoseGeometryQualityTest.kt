package com.azizbek.safetyai.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseGeometryQualityTest {
    private fun fixture(collapsedFrames: Int, count: Int = 240): Pair<VideoPose2DSequence, PoseMamba3DSequence> {
        val frames = List(count) { index ->
            val xy = FloatArray(42)
            fun put(joint: Int, x: Float, y: Float) {
                xy[joint * 2] = x
                xy[joint * 2 + 1] = y
            }
            put(3, 360f, 720f)
            put(1, 360f, 550f)
            val halfWidth = if (index < collapsedFrames) 6f else 35f
            put(4, 360f - halfWidth, 550f)
            put(5, 360f + halfWidth, 550f)
            CanonicalPose2D(xy, BooleanArray(21) { true }, FloatArray(21) { 1f })
        }
        val twoD = VideoPose2DSequence(
            sourceFps = 30,
            targetFps = 60,
            sourceFrameCount = count / 2,
            frameWidth = 720,
            frameHeight = 1280,
            frames = frames,
            timestampsUs = LongArray(count) { it * 16_667L },
            sourceFrameIndices = IntArray(count) { it / 2 },
            decodeFailureCount = 0,
            noPersonFrameCount = 0
        )
        val xyz = FloatArray(count * 21 * 3)
        for (frame in 0 until count) {
            val start = frame * 21 * 3
            xyz[start + 1 * 3 + 2] = 0.7f
            val halfWidth = if (frame < collapsedFrames) 0.04f else 0.2f
            xyz[start + 4 * 3] = -halfWidth
            xyz[start + 5 * 3] = halfWidth
        }
        val threeD = PoseMamba3DSequence(
            frameCount = count,
            coordinates = xyz,
            frameValid = BooleanArray(count) { true },
            skippedWindowIndices = intArrayOf()
        )
        return twoD to threeD
    }

    @Test fun repeatedShoulderCollapseIsFlagged() {
        val (twoD, threeD) = fixture(collapsedFrames = 220)
        val result = PoseGeometryQuality.inspect(twoD, threeD)
        assertTrue(result.unreliable)
        assertTrue(result.collapsedFrames >= 220)
    }

    @Test fun clearOrBrieflyOccludedShouldersAreNotFlagged() {
        for (collapsed in listOf(0, 50, 120)) {
            val (twoD, threeD) = fixture(collapsedFrames = collapsed)
            assertFalse(PoseGeometryQuality.inspect(twoD, threeD).unreliable)
        }
    }
}
