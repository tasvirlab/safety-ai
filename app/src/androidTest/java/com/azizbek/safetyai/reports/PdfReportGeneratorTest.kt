package com.azizbek.safetyai.reports

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azizbek.safetyai.analysis.ActivityFindings
import com.azizbek.safetyai.analysis.BodyRegion
import com.azizbek.safetyai.analysis.RebaComponents
import com.azizbek.safetyai.analysis.RiskLevel
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.VideoMetadata
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfReportGeneratorTest {

    private fun assessment(status: AssessmentStatus) = Assessment(
        id = "abc", name = "Floor-to-waist lifting", projectOrSite = "Tower A", workerReference = null,
        taskType = TaskType.LIFTING, customTaskDescription = null, loadKg = 10.0, suddenForce = false,
        coupling = CouplingQuality.GOOD, source = AssessmentSource.CAMERA,
        video = VideoMetadata("/tmp/x.mp4", 5000, 1920, 1080, 100),
        status = status, createdAtEpochMs = 0, updatedAtEpochMs = 0
    )

    private fun fixtureResult() = SafetyAnalysisResult(
        assessmentId = "abc",
        processingStartedAtEpochMs = 0,
        processingCompletedAtEpochMs = 1,
        inputQualityChecks = emptyList(),
        frames = emptyList(),
        reba = RebaComponents(1, 1, 1, 1, 1, 1, 0, 6, RiskLevel.MEDIUM),
        bodyRegionFindings = emptyList(),
        activity = ActivityFindings(emptyList(), emptyList(), 0, emptyList(), false),
        overallRiskLevel = RiskLevel.MEDIUM,
        highestRiskTimestampMs = 0,
        coveragePercent = 1f,
        recommendations = listOf("Reduce trunk flexion"),
        warnings = emptyList(),
        processingErrors = emptyList()
    )

    @Test
    fun generatesAFileForACompletedAssessment() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = PdfReportGenerator.generate(context, assessment(AssessmentStatus.COMPLETED), fixtureResult())
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    fun refusesToGenerateForANonCompletedAssessment() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            PdfReportGenerator.generate(context, assessment(AssessmentStatus.READY_FOR_ANALYSIS), fixtureResult())
            fail("Expected an IllegalArgumentException for a non-completed assessment")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
