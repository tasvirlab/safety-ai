package com.azizbek.safetyai.analysis

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class PoseMambaTemporalInputInstrumentedTest {

    private val assets
        get() = InstrumentationRegistry.getInstrumentation().context.assets

    private fun readFloats(path: String): FloatArray {
        val bytes = assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()

        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun readInts(path: String): IntArray {
        val bytes = assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asIntBuffer()

        return IntArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun assertFloats(
        name: String,
        actual: FloatArray,
        expected: FloatArray
    ) {
        assertTrue(
            "$name size mismatch: ${actual.size} != ${expected.size}",
            actual.size == expected.size
        )

        var violations = 0
        var maximum = 0f

        for (index in actual.indices) {
            val a = actual[index]
            val e = expected[index]

            if (a.isNaN() && e.isNaN()) continue

            val difference = abs(a - e)
            if (difference > maximum) maximum = difference

            if (!a.isFinite() || !e.isFinite() || difference > 1e-6f) {
                violations++
            }
        }

        assertTrue(
            "$name differs: violations=$violations, max=$maximum",
            violations == 0
        )
    }

    @Test
    fun normalizationAndWindowingMatchBackendGolden() {
        val frameCount = 500
        val valuesPerFrame = 21 * 2
        val positions =
            readFloats("rtmpose_golden/temporal_positions_f32.bin")

        assertTrue(
            "Unexpected position tensor size",
            positions.size == frameCount * valuesPerFrame
        )

        val frames = (0 until frameCount).map { frameIndex ->
            val start = frameIndex * valuesPerFrame
            val coordinates =
                positions.copyOfRange(start, start + valuesPerFrame)

            CanonicalPose2D(
                coordinates = coordinates,
                valid = BooleanArray(21) { joint ->
                    coordinates[joint * 2].isFinite() &&
                        coordinates[joint * 2 + 1].isFinite()
                },
                confidence = FloatArray(21) { 1f }
            )
        }

        val sequence = PoseMambaTemporalInput.normalize(
            frames = frames,
            frameWidth = 1920
        )

        assertFloats(
            "normalized coordinates",
            sequence.coordinates,
            readFloats("rtmpose_golden/temporal_normalized_f32.bin")
        )

        val expectedValid =
            assets.open(
                "rtmpose_golden/temporal_frame_valid_u8.bin"
            ).use { it.readBytes() }

        assertTrue(expectedValid.size == frameCount)
        for (index in 0 until frameCount) {
            assertTrue(
                "frameValid[$index] differs",
                sequence.frameValid[index] ==
                    (expectedValid[index].toInt() != 0)
            )
        }

        val expectedStarts =
            readInts("rtmpose_golden/temporal_window_starts_i32.bin")
        val actualStarts =
            PoseMambaTemporalInput.windowStarts(frameCount)

        assertTrue(
            "Window starts differ: ${actualStarts.contentToString()}",
            actualStarts.contentEquals(expectedStarts)
        )

        val windows = PoseMambaTemporalInput.createWindows(sequence)
        assertTrue(windows.size == expectedStarts.size)

        val expectedWindowValidity =
            booleanArrayOf(true, false, false)

        for (index in windows.indices) {
            val window = windows[index]

            assertTrue(
                "Window $index start differs",
                window.startFrame == expectedStarts[index]
            )
            assertTrue(
                "Window $index validity differs",
                window.valid == expectedWindowValidity[index]
            )

            val expectedStart = window.startFrame * valuesPerFrame
            val expectedEnd =
                expectedStart +
                    PoseMambaTemporalInput.WINDOW_SIZE * valuesPerFrame

            assertFloats(
                "window $index input",
                window.input,
                sequence.coordinates.copyOfRange(
                    expectedStart,
                    expectedEnd
                )
            )
        }

        assertTrue(
            "Videos shorter than 243 frames must be rejected",
            runCatching {
                PoseMambaTemporalInput.windowStarts(242)
            }.isFailure
        )
    }
}