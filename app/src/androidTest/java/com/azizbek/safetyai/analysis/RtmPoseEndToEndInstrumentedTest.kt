package com.azizbek.safetyai.analysis

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

class RtmPoseEndToEndInstrumentedTest {

    private data class Difference(
        val maximum: Float,
        val mean: Double
    )

    private fun readFloats(path: String): FloatArray {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bytes = assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()

        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun difference(
        actual: FloatArray,
        expected: FloatArray
    ): Difference {
        assertTrue(
            "Array size mismatch: ${actual.size} != ${expected.size}",
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
    fun realFrameMatchesHostGolden() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets

        val bitmap = assets.open("rtmpose_golden/frame_0100.png").use {
            BitmapFactory.decodeStream(it)
        } ?: error("Unable to decode golden frame")

        try {
            RtmPoseFrameEstimator(instrumentation.targetContext).use { estimator ->
                val started = SystemClock.elapsedRealtime()
                val people = estimator.estimatePeople(bitmap)
                val elapsedMs = SystemClock.elapsedRealtime() - started

                assertTrue(
                    "Expected exactly one person, found ${people.size}",
                    people.size == 1
                )

                val person = people.single()
                val expectedBox =
                    readFloats("rtmpose_golden/detector_boxes_f32.bin")
                val actualBox = floatArrayOf(
                    person.boundingBox.left,
                    person.boundingBox.top,
                    person.boundingBox.right,
                    person.boundingBox.bottom
                )

                val boxDifference = difference(actualBox, expectedBox)
                val coordinateDifference = difference(
                    person.coordinates,
                    readFloats("rtmpose_golden/keypoints_133_f32.bin")
                )
                val scoreDifference = difference(
                    person.scores,
                    readFloats("rtmpose_golden/scores_133_f32.bin")
                )

                println(
                    "RTMPOSE END-TO-END: elapsedMs=$elapsedMs, " +
                        "boxMax=${boxDifference.maximum}, " +
                        "keypointMax=${coordinateDifference.maximum}, " +
                        "keypointMean=${coordinateDifference.mean}, " +
                        "scoreMax=${scoreDifference.maximum}, " +
                        "scoreMean=${scoreDifference.mean}"
                )

                assertTrue(
                    "Detector box differs: max=${boxDifference.maximum}",
                    boxDifference.maximum <= 0.001f
                )
                assertTrue(
                    "2D keypoints differ: " +
                        "max=${coordinateDifference.maximum}, " +
                        "mean=${coordinateDifference.mean}",
                    coordinateDifference.maximum <= 0.75f &&
                        coordinateDifference.mean <= 0.05
                )
                assertTrue(
                    "Keypoint scores differ: " +
                        "max=${scoreDifference.maximum}, " +
                        "mean=${scoreDifference.mean}",
                    scoreDifference.maximum <= 0.005f &&
                        scoreDifference.mean <= 0.001
                )
            }
        } finally {
            bitmap.recycle()
        }
    }
}