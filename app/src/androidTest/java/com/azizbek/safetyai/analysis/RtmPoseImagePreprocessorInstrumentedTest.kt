package com.azizbek.safetyai.analysis

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

class RtmPoseImagePreprocessorInstrumentedTest {

    private fun readFloats(path: String): FloatArray {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bytes = assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()

        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private data class Difference(
        val maximum: Float,
        val mean: Double
    )

    private fun difference(
        actual: FloatArray,
        expected: FloatArray
    ): Difference {
        assertTrue(
            "Tensor size mismatch: ${actual.size} != ${expected.size}",
            actual.size == expected.size
        )

        var maximum = 0f
        var total = 0.0

        for (index in actual.indices) {
            val value = abs(actual[index] - expected[index])
            maximum = max(maximum, value)
            total += value
        }

        return Difference(
            maximum = maximum,
            mean = total / actual.size
        )
    }

    @Test
    fun bitmapPreprocessingMatchesHostGolden() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bitmap = assets.open("rtmpose_golden/frame_0100.png").use {
            BitmapFactory.decodeStream(it)
        } ?: error("Unable to decode golden frame")

        assertTrue(
            "Unexpected golden frame size",
            bitmap.width == 640 && bitmap.height == 360
        )

        try {
            val detector =
                RtmPoseImagePreprocessor.createDetectorInput(bitmap)
            assertTrue(
                "Unexpected detector resize ratio: ${detector.resizeRatio}",
                detector.resizeRatio == 1f
            )

            val detectorDifference = difference(
                detector.tensor,
                readFloats("rtmpose_golden/detector_input_f32.bin")
            )

            assertTrue(
                "Detector preprocessing differs: " +
                    "max=${detectorDifference.maximum}, " +
                    "mean=${detectorDifference.mean}",
                detectorDifference.maximum == 0f
            )

            val boxValues =
                readFloats("rtmpose_golden/detector_boxes_f32.bin")
            assertTrue("Expected one four-value box", boxValues.size == 4)

            val box = RtmPoseBoundingBox(
                left = boxValues[0],
                top = boxValues[1],
                right = boxValues[2],
                bottom = boxValues[3],
                score = 1f,
                label = 0L
            )

            val poseInput =
                RtmPoseImagePreprocessor.createPoseInput(bitmap, box)
            val poseDifference = difference(
                poseInput,
                readFloats("rtmpose_golden/pose_input_f32.bin")
            )

            println(
                "POSE PREPROCESSING: " +
                    "max=${poseDifference.maximum}, " +
                    "mean=${poseDifference.mean}"
            )

            // Roughly permits at most 3 source intensity levels at isolated
            // interpolation boundaries, with a sub-level average difference.
            assertTrue(
                "Pose preprocessing differs too much: " +
                    "max=${poseDifference.maximum}, " +
                    "mean=${poseDifference.mean}",
                poseDifference.maximum <= 0.071f &&
                    poseDifference.mean <= 0.002
            )
        } finally {
            bitmap.recycle()
        }
    }
}