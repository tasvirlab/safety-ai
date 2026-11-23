package com.azizbek.safetyai.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.azizbek.safetyai.analysis.*
import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.VideoMetadata
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CaseStudyExportInstrumentedTest {
    private fun assessment() = Assessment(
        id = "export-contract-test", name = "Lifting", projectOrSite = "Controlled lab",
        workerReference = "TEST", taskType = TaskType.MATERIAL_LIFTING,
        customTaskDescription = null, loadKg = 0.0, suddenForce = false,
        coupling = CouplingQuality.GOOD, source = AssessmentSource.CAMERA,
        video = VideoMetadata("/tmp/test.mp4", 3000, 720, 1280, 100),
        status = AssessmentStatus.COMPLETED, createdAtEpochMs = 0, updatedAtEpochMs = 0
    )

    private fun result(provenance: PipelineProvenance?): SafetyAnalysisResult {
        val timeline = List(3) { index ->
            val score = when (index) { 0 -> 2; 2 -> 9; else -> null }
            FrameRebaResult(
                frameIndex = index, timestampUs = index * 1_000_000L, durationUs = 1_000_000L,
                valid = score != null, scoreA = score, scoreB = score, tableCScore = score,
                activityScore = 0, finalScore = score,
                riskLevel = when (index) { 0 -> RiskLevel.LOW; 2 -> RiskLevel.HIGH; else -> null },
                neckScore = null, trunkScore = null, legsScore = null,
                upperArmRScore = null, upperArmLScore = null, lowerArmRScore = null,
                lowerArmLScore = null, wristRScore = null, wristLScore = null, loadScore = 0,
                fullyEvaluated = score != null,
                invalidReasons = if (score == null) listOf("invalid_3d_pose") else emptyList()
            )
        }
        return SafetyAnalysisResult(
            schemaVersion = if (provenance == null) 6 else ANALYSIS_SCHEMA_VERSION,
            assessmentId = assessment().id, processingStartedAtEpochMs = 0,
            processingCompletedAtEpochMs = 100, inputQualityChecks = emptyList(),
            frames = List(3) { FrameAnalysis(it * 1000L, emptyList(), emptyList(), emptyMap()) },
            reba = RebaComponents(1, 1, 1, 1, 1, 1, 0, 9, RiskLevel.HIGH),
            bodyRegionFindings = emptyList(),
            activity = ActivityFindings(emptyList(), emptyList(), 0, emptyList(), false),
            overallRiskLevel = RiskLevel.HIGH, highestRiskTimestampMs = 2000L,
            coveragePercent = 66.6667f, recommendations = emptyList(), warnings = emptyList(),
            processingErrors = emptyList(), frameRebaResults = timeline, provenance = provenance
        )
    }

    private fun exportedSummary(result: SafetyAnalysisResult): JSONObject {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = CaseStudyExport.generate(context, assessment(), result)
        return ZipFile(file).use { zip ->
            assertNotNull(zip.getEntry("frames.csv"))
            assertNotNull(zip.getEntry("joints.csv"))
            assertNotNull(zip.getEntry("activity_events.csv"))
            JSONObject(zip.getInputStream(zip.getEntry("summary.json")).bufferedReader().use { it.readText() })
        }
    }

    @Test fun storedModelIdentityIsUsedInsteadOfTheCurrentlyInstalledModel() {
        val config = FrozenPipeline.configuration()
        val provenance = PipelineProvenance("captured-test-version", config,
            FrozenPipeline.configurationHash(config), mapOf("posemamba" to "b".repeat(64)),
            "a".repeat(64), mapOf("device_model" to "test-device"), mapOf("rtmpose_2d" to 10L))
        val json = exportedSummary(result(provenance))
        assertEquals("captured-test-version", json.getString("pipeline_id"))
        assertEquals("b".repeat(64), json.getString("posemamba_model_sha256"))
        assertEquals("a".repeat(64), json.getString("source_video_sha256"))
        assertEquals("60.0", json.getJSONObject("analysis_provenance")
            .getJSONObject("configuration").getString("rapid_min_velocity_deg_s"))
    }

    @Test fun oldResultDoesNotAcquireAFalseModelIdentityAtExportTime() {
        val json = exportedSummary(result(null))
        assertEquals("not_recorded_legacy_result", json.getString("provenance_status"))
        assertTrue(json.isNull("pipeline_id"))
        assertTrue(json.isNull("posemamba_model_sha256"))
    }

    @Test fun unscoredTimeIsKeptInTheRiskDurationDenominator() {
        val json = exportedSummary(result(null))
        assertEquals(3000.0, json.getDouble("video_duration_ms"), 0.001)
        assertEquals(1000.0, json.getDouble("unscored_duration_ms"), 0.001)
        assertEquals(2, json.getInt("minimum_reba"))
        assertEquals(9, json.getInt("maximum_reba"))
        assertEquals(2, json.getInt("maximum_frame_index_zero_based"))
        assertEquals(5.5, json.getDouble("time_weighted_mean_reba"), 0.001)
        assertEquals(100.0 / 3.0, json.getJSONObject("risk_duration")
            .getJSONObject("HIGH").getDouble("percent_of_video"), 0.001)
    }
}
