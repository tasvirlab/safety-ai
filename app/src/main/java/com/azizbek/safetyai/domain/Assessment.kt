package com.azizbek.safetyai.domain

import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType

/** A saved assessment — the persisted counterpart of [com.azizbek.safetyai.assessment.AssessmentDraft] plus its video and lifecycle state. */
data class Assessment(
    val id: String,
    val name: String,
    val projectOrSite: String?,
    val workerReference: String?,
    val taskType: TaskType,
    val customTaskDescription: String?,
    val loadKg: Double,
    val suddenForce: Boolean,
    val coupling: CouplingQuality,
    val source: AssessmentSource,
    val video: VideoMetadata,
    val status: AssessmentStatus,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    /** New category-only selection; false for legacy assessments with an exact kg value. */
    val loadIsCategory: Boolean = false
)
