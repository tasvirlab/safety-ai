package com.azizbek.safetyai.assessment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Nav-arg key populated automatically into [SavedStateHandle] by Navigation Compose. */
const val ASSESSMENT_SOURCE_ARG = "source"

private const val KEY_FORM_STATE = "assessmentFormState"

/**
 * Holds the New Assessment form above configuration changes — and process
 * death — via [SavedStateHandle]. This class collects and validates form
 * input and hands a typed [AssessmentDraft] to whoever asked for one; it
 * knows nothing about cameras, file pickers, or REBA scoring.
 *
 * Built with the plain reflection-based default factory (a constructor
 * taking only [SavedStateHandle]) rather than a DI framework.
 */
class AssessmentFormViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private val initialSource: AssessmentSource =
        savedStateHandle.get<String>(ASSESSMENT_SOURCE_ARG)
            ?.let { runCatching { AssessmentSource.valueOf(it) }.getOrNull() }
            ?: AssessmentSource.CAMERA

    private val _uiState = MutableStateFlow(
        savedStateHandle.get<AssessmentFormState>(KEY_FORM_STATE)
            ?: AssessmentFormState(source = initialSource)
    )
    val uiState: StateFlow<AssessmentFormState> = _uiState.asStateFlow()

    private fun update(transform: (AssessmentFormState) -> AssessmentFormState) {
        val next = transform(_uiState.value).copy(formEdited = true)
        _uiState.value = next
        savedStateHandle[KEY_FORM_STATE] = next
    }

    fun onProjectOrSiteChange(value: String) = update { it.copy(projectOrSite = value).withAutoName() }

    fun onWorkerReferenceChange(value: String) = update { it.copy(workerReference = value) }

    fun onTaskTypeSelected(type: TaskType) = update { it.copy(taskType = type, taskTypeTouched = true).withAutoName() }

    fun onCustomTaskDescriptionChange(value: String) =
        update { it.copy(customTaskDescription = value, customTaskTouched = true).withAutoName() }

    fun onLoadTextChange(value: String) = update { it.withLoadText(value) }

    fun onLoadCategorySelected(value: String) = update { it.withLoadCategory(value) }

    fun onSuddenForceChange(enabled: Boolean) = update { it.copy(suddenForce = enabled) }

    fun onCouplingSelected(quality: CouplingQuality) =
        update { it.copy(coupling = quality, couplingTouched = true) }
}

/**
 * The wizard never shows a manual "assessment name" field (dropped to match
 * the redesign) — the name is derived from task type + work area instead,
 * recomputed on every change to either.
 */
private fun AssessmentFormState.withAutoName(): AssessmentFormState {
    val base = if (isOtherTaskType) customTaskDescription.trim() else taskType?.label().orEmpty()
    val site = projectOrSite.trim()
    val derived = if (base.isNotEmpty() && site.isNotEmpty()) "$base — $site" else base
    return copy(name = derived, nameTouched = true)
}
