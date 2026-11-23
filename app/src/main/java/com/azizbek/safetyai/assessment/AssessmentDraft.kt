package com.azizbek.safetyai.assessment

/**
 * Immutable, fully-validated output of the New Assessment form — the typed
 * boundary this phase stops at. Nothing downstream of this (camera capture,
 * video upload, REBA scoring) is implemented yet.
 */
data class AssessmentDraft(
    val source: AssessmentSource,
    val name: String,
    val projectOrSite: String?,
    val workerReference: String?,
    val taskType: TaskType,
    val customTaskDescription: String?,
    val loadKg: Double,
    val suddenForce: Boolean,
    val coupling: CouplingQuality,
    val loadIsCategory: Boolean = false
)
