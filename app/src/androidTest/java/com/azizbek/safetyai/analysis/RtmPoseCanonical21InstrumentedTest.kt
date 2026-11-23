package com.azizbek.safetyai.analysis

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class RtmPoseCanonical21InstrumentedTest {

    private val assets
        get() = InstrumentationRegistry.getInstrumentation().context.assets

    private fun readFloats(path: String): FloatArray {
        val bytes = assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()

        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun readBytes(path: String): ByteArray =
        assets.open(path).use { it.readBytes() }

    private fun assertFloatArrays(
        name: String,
        actual: FloatArray,
        expected: FloatArray,
        tolerance: Float = 1e-4f
    ) {
        assertTrue(
            "$name size mismatch: ${actual.size} != ${expected.size}",
            actual.size == expected.size
        )

        var violations = 0
        var maximum = 0f

        for (index in actual.indices) {
            val actualValue = actual[index]
            val expectedValue = expected[index]

            if (actualValue.isNaN() && expectedValue.isNaN()) continue

            val difference = abs(actualValue - expectedValue)
            if (difference > maximum) maximum = difference

            if (
                !actualValue.isFinite() ||
                !expectedValue.isFinite() ||
                difference > tolerance
            ) {
                violations++
            }
        }

        assertTrue(
            "$name differs: violations=$violations, max=$maximum",
            violations == 0
        )
    }

    @Test
    fun mappingMatchesBackendGolden() {
        val person = RtmPosePerson2D(
            boundingBox = RtmPoseBoundingBox(
                left = 0f,
                top = 0f,
                right = 1f,
                bottom = 1f,
                score = 1f,
                label = 0L
            ),
            coordinates =
                readFloats("rtmpose_golden/keypoints_133_f32.bin"),
            scores =
                readFloats("rtmpose_golden/scores_133_f32.bin")
        )

        val actual = RtmPoseCanonical21.map(person)

        assertTrue(
            "PoseMamba joint order differs",
            RtmPoseCanonical21.jointNames == listOf(
                "HD", "C7", "T10", "PELCEN",
                "LSHO", "RSHO", "LELB", "RELB",
                "LWR", "RWR", "LHAND", "RHAND",
                "LHIP", "RHIP", "LKNE", "RKNE",
                "LANK", "RANK", "LTOE", "RTOE", "L45"
            )
        )
        assertTrue(
            "PELCEN must remain PoseMamba root index 3",
            RtmPoseCanonical21.ROOT_INDEX == 3
        )

        assertFloatArrays(
            "canonical coordinates",
            actual.coordinates,
            readFloats(
                "rtmpose_golden/canonical21_coordinates_f32.bin"
            )
        )
        assertFloatArrays(
            "canonical confidence",
            actual.confidence,
            readFloats(
                "rtmpose_golden/canonical21_confidence_f32.bin"
            )
        )

        val expectedValid =
            readBytes("rtmpose_golden/canonical21_valid_u8.bin")
        assertTrue(expectedValid.size == 21)

        for (index in 0 until 21) {
            assertTrue(
                "valid[$index] differs",
                actual.valid[index] == (expectedValid[index].toInt() != 0)
            )
        }
    }

    private fun syntheticPerson(
        hipX: Float,
        bodyConfidence: Float
    ): RtmPosePerson2D {
        val coordinates = FloatArray(133 * 2)
        val scores = FloatArray(133) { bodyConfidence }

        coordinates[11 * 2] = hipX - 1f
        coordinates[12 * 2] = hipX + 1f
        coordinates[11 * 2 + 1] = 100f
        coordinates[12 * 2 + 1] = 100f

        return RtmPosePerson2D(
            boundingBox = RtmPoseBoundingBox(
                left = hipX - 10f,
                top = 50f,
                right = hipX + 10f,
                bottom = 150f,
                score = bodyConfidence,
                label = 0L
            ),
            coordinates = coordinates,
            scores = scores
        )
    }

    @Test
    fun trackerMatchesDeploymentPriority() {
        val tracker = RtmPosePersonTracker()

        val lowConfidence = syntheticPerson(
            hipX = 100f,
            bodyConfidence = 0.4f
        )
        val highConfidence = syntheticPerson(
            hipX = 300f,
            bodyConfidence = 0.9f
        )

        // First frame: highest mean body confidence.
        assertTrue(
            tracker.select(listOf(lowConfidence, highConfidence)) ===
                highConfidence
        )

        val nearPrevious = syntheticPerson(
            hipX = 305f,
            bodyConfidence = 0.35f
        )
        val farHighConfidence = syntheticPerson(
            hipX = 600f,
            bodyConfidence = 0.99f
        )

        // Later frame: nearest previous hip wins, not highest confidence.
        assertTrue(
            tracker.select(listOf(farHighConfidence, nearPrevious)) ===
                nearPrevious
        )

        // Missing frame resets tracking.
        assertTrue(tracker.select(emptyList()) == null)
        assertTrue(
            tracker.select(listOf(nearPrevious, farHighConfidence)) ===
                farHighConfidence
        )
    }
}