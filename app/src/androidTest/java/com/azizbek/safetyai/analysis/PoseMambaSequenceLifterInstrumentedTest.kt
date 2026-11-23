package com.azizbek.safetyai.analysis

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMambaSequenceLifterInstrumentedTest {

    private class FakeLifter : PoseMambaLifter {
        var calls = 0
        var closed = false

        override suspend fun lift(
            normalized2DWindow: FloatArray
        ): FloatArray {
            val callOffset = calls * 1000f
            calls++

            val output = FloatArray(243 * 21 * 3)

            for (frame in 0 until 243) {
                val frameMarker =
                    normalized2DWindow[frame * 21 * 2]
                val value = frameMarker + callOffset

                for (coordinate in 0 until 21 * 3) {
                    output[frame * 21 * 3 + coordinate] = value
                }
            }

            return output
        }

        override fun close() {
            closed = true
        }
    }

    private fun sequence(
        invalidFrames: Set<Int> = emptySet()
    ): NormalizedPose2DSequence {
        val frameCount = 500
        val coordinates = FloatArray(frameCount * 21 * 2)
        val valid = BooleanArray(frameCount) { true }

        for (frame in 0 until frameCount) {
            // Marker lets the test identify each original global frame.
            coordinates[frame * 21 * 2] = frame.toFloat()
        }

        for (frame in invalidFrames) {
            valid[frame] = false
        }

        return NormalizedPose2DSequence(
            frameCount = frameCount,
            coordinates = coordinates,
            frameValid = valid
        )
    }

    private fun firstValue(
        result: PoseMamba3DSequence,
        frame: Int
    ): Float = result.coordinates[frame * 21 * 3]

    @Test
    fun tailOverlapIsNotWrittenTwice() = runBlocking {
        val fake = FakeLifter()
        val progress = mutableListOf<Pair<Int, Int>>()

        PoseMambaSequenceLifter(fake).use { runner ->
            val result = runner.lift(sequence()) { completed, total ->
                progress += completed to total
            }

            assertTrue(fake.calls == 3)
            assertTrue(result.skippedWindowIndices.isEmpty())
            assertTrue(result.frameValid.all { it })

            // Window 0: frames 0..242, call offset 0.
            assertTrue(firstValue(result, 0) == 0f)
            assertTrue(firstValue(result, 242) == 242f)

            // Window 1: frames 243..485, call offset 1000.
            assertTrue(firstValue(result, 243) == 1243f)
            assertTrue(firstValue(result, 485) == 1485f)

            // Tail starts at 257 but only new frames 486..499 are emitted.
            assertTrue(firstValue(result, 300) == 1300f)
            assertTrue(firstValue(result, 486) == 2486f)
            assertTrue(firstValue(result, 499) == 2499f)

            assertTrue(
                progress == listOf(
                    1 to 3,
                    2 to 3,
                    3 to 3
                )
            )
        }

        assertTrue(fake.closed)
    }

    @Test
    fun invalidWindowsAreSkippedWithoutFake3d() = runBlocking {
        val fake = FakeLifter()

        PoseMambaSequenceLifter(fake).use { runner ->
            // Frame 300 belongs to windows starting at 243 and 257.
            val result = runner.lift(
                sequence(invalidFrames = setOf(300))
            )

            assertTrue(fake.calls == 1)
            assertTrue(
                result.skippedWindowIndices.contentEquals(
                    intArrayOf(1, 2)
                )
            )

            for (frame in 0 until 243) {
                assertTrue(result.frameValid[frame])
                assertTrue(firstValue(result, frame).isFinite())
            }

            for (frame in 243 until 500) {
                assertTrue(!result.frameValid[frame])
                assertTrue(firstValue(result, frame).isNaN())
            }
        }

        assertTrue(fake.closed)
    }
}