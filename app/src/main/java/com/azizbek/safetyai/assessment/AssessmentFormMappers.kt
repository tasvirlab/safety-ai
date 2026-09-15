package com.azizbek.safetyai.assessment

import com.azizbek.safetyai.domain.Assessment

/** Loads a saved [Assessment]'s editable fields into an [AssessmentFormState] for [com.azizbek.safetyai.detail.EditAssessmentScreen]. Every field starts "touched" since it already has a value. */
fun Assessment.toFormState(): AssessmentFormState = AssessmentFormState(
    source = source,
    name = name,
    nameTouched = true,
    projectOrSite = projectOrSite.orEmpty(),
    workerReference = workerReference.orEmpty(),
    taskType = taskType,
    taskTypeTouched = true,
    customTaskDescription = customTaskDescription.orEmpty(),
    customTaskTouched = true,
    loadKgText = formatLoadForEditing(loadKg),
    loadTouched = true,
    suddenForce = suddenForce,
    coupling = coupling,
    couplingTouched = true
)

/** Applies a validated [AssessmentFormState]'s edits onto [original], preserving its id/video/status/timestamps except `updatedAtEpochMs`. */
fun AssessmentFormState.applyEditsTo(original: Assessment): Assessment? {
    val draft = toDraftOrNull() ?: return null
    return original.copy(
        name = draft.name,
        projectOrSite = draft.projectOrSite,
        workerReference = draft.workerReference,
        taskType = draft.taskType,
        customTaskDescription = draft.customTaskDescription,
        loadKg = draft.loadKg,
        suddenForce = draft.suddenForce,
        coupling = draft.coupling,
        updatedAtEpochMs = System.currentTimeMillis()
    )
}

private fun formatLoadForEditing(loadKg: Double): String =
    if (loadKg == loadKg.toLong().toDouble()) loadKg.toLong().toString() else loadKg.toString()
