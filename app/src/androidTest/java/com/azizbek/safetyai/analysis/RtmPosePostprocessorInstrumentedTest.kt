package com.azizbek.safetyai.analysis

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

class RtmPosePostprocessorInstrumentedTest {

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
    fun detectorAndSimccDecodingMatchHostGolden() {
        val detections =
            readFloats("rtmpose_golden/detector_dets_f32.bin")
        val labels =
            readLongs("rtmpose_golden/detector_labels_i64.bin")
        val expectedBoxes =
            readFloats("rtmpose_golden/detector_boxes_f32.bin")

        // Golden frame is 640 x 360; YOLOX 640 x 640 uses ratio 1.0.
        val boxes = RtmPosePostprocessor.decodeDetector(
            detections = detections,
            labels = labels,
            resizeRatio = 1f
        )

        assertTrue("Expected exactly one detected person", boxes.size == 1)

        val box = boxes.single()
        val actualBox = floatArrayOf(
            box.left,
            box.top,
            box.right,
            box.bottom
        )
        assertClose("detector box", actualBox, expectedBoxes)

        val pose = RtmPosePostprocessor.decodePose(
            simccX = readFloats("rtmpose_golden/simcc_x_f32.bin"),
            simccY = readFloats("rtmpose_golden/simcc_y_f32.bin"),
            boundingBox = box
        )

        assertClose(
            "133 keypoint coordinates",
            pose.coordinates,
            readFloats("rtmpose_golden/keypoints_133_f32.bin")
        )
        assertClose(
            "133 keypoint scores",
            pose.scores,
            readFloats("rtmpose_golden/scores_133_f32.bin")
        )
    }
}