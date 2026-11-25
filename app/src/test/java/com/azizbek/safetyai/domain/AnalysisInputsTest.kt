package com.azizbek.safetyai.domain

import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AnalysisInputsTest {
    private val base = Assessment(
        id = "a", name = "n", projectOrSite = null, workerReference = null,
        taskType = TaskType.LIFTING, customTaskDescription = null, loadKg = 10.0, suddenForce = false,
        coupling = CouplingQuality.GOOD, source = AssessmentSource.CAMERA,
        video = VideoMetadata("/v.mp4", 5000, 1920, 1080, 100),
        status = AssessmentStatus.COMPLETED, createdAtEpochMs = 0, updatedAtEpochMs = 0
    )

    @Test fun `descriptive edits do not change inputs`() {
        assertEquals(base.analysisInputs(), base.copy(name = "x", projectOrSite = "p", workerReference = "w", updatedAtEpochMs = 9).analysisInputs())
    }

    @Test fun `each analysis field changes inputs`() {
        listOf(
            base.copy(loadKg = 20.0), base.copy(suddenForce = true), base.copy(coupling = CouplingQuality.POOR),
            base.copy(taskType = TaskType.CARRYING), base.copy(video = base.video.copy(filePath = "/other.mp4"))
        ).forEach { assertNotEquals(base.analysisInputs(), it.analysisInputs()) }
    }
}
