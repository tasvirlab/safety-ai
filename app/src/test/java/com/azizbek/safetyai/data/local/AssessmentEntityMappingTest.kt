package com.azizbek.safetyai.data.local

import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.VideoMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class AssessmentEntityMappingTest {

    private fun sampleAssessment() = Assessment(
        id = "abc-123",
        name = "Floor-to-waist lifting",
        projectOrSite = "Tower A — Level 3",
        workerReference = "Worker 04",
        taskType = TaskType.LIFTING,
        customTaskDescription = null,
        loadKg = 12.5,
        suddenForce = true,
        coupling = CouplingQuality.GOOD,
        source = AssessmentSource.CAMERA,
        video = VideoMetadata(
            filePath = "/data/user/0/com.azizbek.safetyai/files/videos/x.mp4",
            durationMs = 15_000,
            widthPx = 1920,
            heightPx = 1080,
            fileSizeBytes = 4_000_000
        ),
        status = AssessmentStatus.READY_FOR_ANALYSIS,
        createdAtEpochMs = 1_700_000_000_000,
        updatedAtEpochMs = 1_700_000_100_000
    )

    @Test
    fun `domain to entity and back is lossless`() {
        val original = sampleAssessment()
        val roundTripped = original.toEntity().toDomain()
        assertEquals(original, roundTripped)
    }

    @Test
    fun `entity stores video metadata fields flattened`() {
        val entity = sampleAssessment().toEntity()
        assertEquals(1920, entity.videoWidthPx)
        assertEquals(1080, entity.videoHeightPx)
        assertEquals(15_000L, entity.videoDurationMs)
        assertEquals(4_000_000L, entity.videoFileSizeBytes)
    }

    @Test
    fun `Other task type preserves custom description through mapping`() {
        val withCustomTask = sampleAssessment().copy(taskType = TaskType.OTHER, customTaskDescription = "Pallet jack maneuvering")
        val roundTripped = withCustomTask.toEntity().toDomain()
        assertEquals("Pallet jack maneuvering", roundTripped.customTaskDescription)
    }

    @Test
    fun `null optional fields survive mapping`() {
        val withoutOptionals = sampleAssessment().copy(projectOrSite = null, workerReference = null)
        val roundTripped = withoutOptionals.toEntity().toDomain()
        assertEquals(null, roundTripped.projectOrSite)
        assertEquals(null, roundTripped.workerReference)
    }
}
