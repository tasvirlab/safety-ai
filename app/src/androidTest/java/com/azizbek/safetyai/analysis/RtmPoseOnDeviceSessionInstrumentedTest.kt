package com.azizbek.safetyai.analysis

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

class RtmPoseOnDeviceSessionInstrumentedTest {

    private fun readFloats(path: String): FloatArray {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bytes = assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()

        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun readLongs(path: String): LongArray {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bytes = assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asLongBuffer()

        return LongArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun assertClose(
        name: String,
        actual: FloatArray,
        expected: FloatArray
    ) {
        assertTrue(
            "$name size mismatch: ${actual.size} != ${expected.size}",
            actual.size == expected.size
        )

        var violations = 0
        var maxDifference = 0f

        for (index in actual.indices) {
            val difference = abs(actual[index] - expected[index])
            val tolerance = 1e-4f + 1e-4f * abs(expected[index])

            maxDifference = max(maxDifference, difference)
            if (!actual[index].isFinite() || difference > tolerance) {
                violations++
            }
        }

        assertTrue(
            "$name differs: violations=$violations, maxDifference=$maxDifference",
            violations == 0
        )
    }

    @Test
    fun productionSessionsMatchHostGolden() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        val detectorInput =
            readFloats("rtmpose_golden/detector_input_f32.bin")
        val expectedDetections =
            readFloats("rtmpose_golden/detector_dets_f32.bin")
        val expectedLabels =
            readLongs("rtmpose_golden/detector_labels_i64.bin")

        val poseInput =
            readFloats("rtmpose_golden/pose_input_f32.bin")
        val expectedSimccX =
            readFloats("rtmpose_golden/simcc_x_f32.bin")
        val expectedSimccY =
            readFloats("rtmpose_golden/simcc_y_f32.bin")

        RtmPoseOnDeviceSession(instrumentation.targetContext).use { session ->
            val detector = session.runDetector(detectorInput)

            assertClose(
                "detector detections",
                detector.detections,
                expectedDetections
            )
            assertTrue(
                "detector labels differ",
                detector.labels.contentEquals(expectedLabels)
            )

            // Detector session remains open while the pose session executes.
            val pose = session.runPose(poseInput)

            assertClose("pose simcc_x", pose.simccX, expectedSimccX)
            assertClose("pose simcc_y", pose.simccY, expectedSimccY)
        }
    }
}