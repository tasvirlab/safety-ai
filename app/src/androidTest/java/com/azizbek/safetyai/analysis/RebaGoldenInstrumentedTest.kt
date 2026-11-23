package com.azizbek.safetyai.analysis

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RebaGoldenInstrumentedTest {
    @Test
    fun androidRebaMatchesFrozenPythonGolden() {
        val assets =
            InstrumentationRegistry.getInstrumentation().context.assets

        val fixture = JSONObject(
            assets.open("reba/reba_golden.json")
                .bufferedReader()
                .use { it.readText() }
        )

        assertEquals(1, fixture.getInt("schemaVersion"))
        assertEquals(21, fixture.getJSONArray("jointNames").length())

        val samples = fixture.getJSONArray("samples")
        assertEquals(fixture.getInt("sampleCount"), samples.length())
        assertTrue(samples.length() >= 7)

        var maximumAngleDifference = 0.0

        for (sampleIndex in 0 until samples.length()) {
            val sample = samples.getJSONObject(sampleIndex)
            val coordinatesJson = sample.getJSONArray("coordinates")

            assertEquals(21 * 3, coordinatesJson.length())

            val coordinates = FloatArray(coordinatesJson.length()) {
                coordinatesJson.getDouble(it).toFloat()
            }

            val actualAngles =
                RebaAngleCalculator.computeFrame(coordinates)
            val expectedAngles = sample.getJSONObject("angles")

            val actualAngleMap = linkedMapOf(
                "trunk_sagittal_deg" to
                    actualAngles.trunkSagittalDeg,
                "trunk_lateral_deg" to
                    actualAngles.trunkLateralDeg,
                "trunk_twist_deg" to
                    actualAngles.trunkTwistDeg,
                "neck_flex_deg" to
                    actualAngles.neckFlexDeg,
                "neck_lateral_deg" to
                    actualAngles.neckLateralDeg,
                "upper_arm_r_sagittal_deg" to
                    actualAngles.upperArmRSagittalDeg,
                "upper_arm_l_sagittal_deg" to
                    actualAngles.upperArmLSagittalDeg,
                "upper_arm_r_abd_deg" to
                    actualAngles.upperArmRAbductionDeg,
                "upper_arm_l_abd_deg" to
                    actualAngles.upperArmLAbductionDeg,
                "lower_arm_r_deg" to
                    actualAngles.lowerArmRDeg,
                "lower_arm_l_deg" to
                    actualAngles.lowerArmLDeg,
                "wrist_r_deg" to
                    actualAngles.wristRDeg,
                "wrist_l_deg" to
                    actualAngles.wristLDeg,
                "knee_r_deg" to
                    actualAngles.kneeRDeg,
                "knee_l_deg" to
                    actualAngles.kneeLDeg,
                "knee_max_deg" to
                    actualAngles.kneeMaxDeg
            )

            for ((name, actual) in actualAngleMap) {
                val expected = expectedAngles.getDouble(name)
                val difference = abs(actual - expected)
                maximumAngleDifference =
                    maxOf(maximumAngleDifference, difference)

                assertTrue(
                    "Sample $sampleIndex angle $name differs: " +
                        "expected=$expected actual=$actual " +
                        "difference=$difference",
                    difference <= 1e-4
                )
            }

            val manualJson = sample.getJSONObject("manual")
            val manual = RebaManualInputs(
                loadKg = manualJson.getDouble("loadKg"),
                suddenForce =
                    manualJson.getBoolean("suddenForce"),
                coupling = manualJson.getInt("coupling"),
                activityScore = sample.getInt("activityScore"),
                activityPending =
                    sample.getBoolean("activityPending")
            )

            val actual =
                RebaScorer.scoreFrame(actualAngles, manual)
            val expected = sample.getJSONObject("expected")

            assertTrue(
                "Sample $sampleIndex should be valid",
                actual.valid
            )
            assertTrue(actual.invalidReasons.isEmpty())

            assertEquals(
                "Sample $sampleIndex Score A",
                expected.getInt("score_a"),
                actual.scoreA
            )
            assertEquals(
                "Sample $sampleIndex Score B",
                expected.getInt("score_b"),
                actual.scoreB
            )
            assertEquals(
                "Sample $sampleIndex Table C",
                expected.getInt("table_c_score"),
                actual.tableCScore
            )
            assertEquals(
                "Sample $sampleIndex Final REBA",
                expected.getInt("reba_score"),
                actual.finalRebaScore
            )
            assertEquals(
                "Sample $sampleIndex action level",
                expected.getInt("action_level"),
                actual.actionLevel
            )
            assertEquals(
                "Sample $sampleIndex risk",
                expected.getString("risk_level"),
                actual.riskLabel
            )
            assertEquals(
                "Sample $sampleIndex action text",
                expected.getString("action_text"),
                actual.actionText
            )
            assertEquals(
                "Sample $sampleIndex dominant arm",
                expected.getString("dominant_arm"),
                actual.dominantArm
            )

            assertEquals(
                expected.getBoolean("fully_evaluated"),
                actual.postureFullyEvaluated
            )
            assertTrue(actual.activityPending)
            assertEquals(0, actual.activityScore)
            assertEquals(actual.tableCScore, actual.finalRebaScore)
            assertFalse(actual.fullyEvaluated)

            val expectedUnsupported =
                expected.getJSONArray("unsupported_components")
                    .toStringList()

            assertEquals(
                "Sample $sampleIndex unsupported components",
                expectedUnsupported,
                actual.unsupportedComponents
            )

            val expectedComponents =
                expected.getJSONObject("components")

            val actualComponents = linkedMapOf(
                "trunk" to actual.components.trunk,
                "neck" to actual.components.neck,
                "legs" to actual.components.legs,
                "upper_arm_r" to actual.components.upperArmR,
                "upper_arm_l" to actual.components.upperArmL,
                "lower_arm_r" to actual.components.lowerArmR,
                "lower_arm_l" to actual.components.lowerArmL,
                "wrist_r" to actual.components.wristR,
                "wrist_l" to actual.components.wristL,
                "load" to actual.components.load
            )

            for ((name, component) in actualComponents) {
                val expectedComponent =
                    expectedComponents.getJSONObject(name)

                assertEquals(
                    "Sample $sampleIndex component $name",
                    expectedComponent.getInt("score"),
                    component.score
                )
                assertEquals(
                    "Sample $sampleIndex component $name base",
                    expectedComponent.getInt("base"),
                    component.base
                )
                assertEquals(
                    "Sample $sampleIndex component $name modifier",
                    expectedComponent.getInt("modifier"),
                    component.modifier
                )
            }
        }

        assertTrue(
            "Maximum angle difference was $maximumAngleDifference",
            maximumAngleDifference <= 1e-4
        )
    }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { index -> getString(index) }
}