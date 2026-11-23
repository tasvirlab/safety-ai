package com.azizbek.safetyai.analysis

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PoseTemporalResamplerTest {
    @Test
    fun thirtyFpsFramesAreDuplicatedToSixtyHz() {
        val source = listOf(
            pose(10f),
            pose(20f),
            pose(30f)
        )

        val result = PoseTemporalResampler.resample(
            sourceFrames = source,
            sourceFps = 30,
            frameWidth = 640,
            frameHeight = 360
        )

        assertEquals(30, result.sourceFps)
        assertEquals(60, result.targetFps)
        assertEquals(3, result.sourceFrameCount)
        assertEquals(6, result.frameCount)

        assertArrayEquals(
            intArrayOf(0, 0, 1, 1, 2, 2),
            result.sourceFrameIndices
        )
        assertArrayEquals(
            longArrayOf(
                0L,
                16_666L,
                33_333L,
                50_000L,
                66_666L,
                83_333L
            ),
            result.timestampsUs
        )

        assertSame(source[0], result.frames[0])
        assertSame(source[0], result.frames[1])
        assertSame(source[1], result.frames[2])
        assertSame(source[1], result.frames[3])
        assertSame(source[2], result.frames[4])
        assertSame(source[2], result.frames[5])
    }

    @Test
    fun sixtyFpsFramesRemainOneToOne() {
        val source = listOf(
            pose(10f),
            pose(20f),
            pose(30f)
        )

        val result = PoseTemporalResampler.resample(
            sourceFrames = source,
            sourceFps = 60,
            frameWidth = 1920,
            frameHeight = 1080,
            decodeFailureCount = 1,
            noPersonFrameCount = 2
        )

        assertEquals(3, result.frameCount)
        assertArrayEquals(
            intArrayOf(0, 1, 2),
            result.sourceFrameIndices
        )
        assertArrayEquals(
            longArrayOf(0L, 16_666L, 33_333L),
            result.timestampsUs
        )
        assertEquals(1, result.decodeFailureCount)
        assertEquals(2, result.noPersonFrameCount)

        assertSame(source[0], result.frames[0])
        assertSame(source[1], result.frames[1])
        assertSame(source[2], result.frames[2])
    }

    private fun pose(marker: Float): CanonicalPose2D {
        val coordinates = FloatArray(21 * 2)
        coordinates[0] = marker

        return CanonicalPose2D(
            coordinates = coordinates,
            valid = BooleanArray(21) { true },
            confidence = FloatArray(21) { 1f }
        )
    }
}