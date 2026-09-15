package com.azizbek.safetyai.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.assessment.AssessmentFormState
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.assessment.applyEditsTo
import com.azizbek.safetyai.assessment.toFormState
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.domain.Assessment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Edits a saved assessment's metadata — reuses [AssessmentFormState]'s validation, never touches the video or status. */
class EditAssessmentViewModel(
    private val repository: AssessmentRepository,
    private val assessmentId: String
) : ViewModel() {
    private var original: Assessment? = null

    private val _formState = MutableStateFlow<AssessmentFormState?>(null)
    val formState: StateFlow<AssessmentFormState?> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            val assessment = repository.getById(assessmentId)
            original = assessment
            _formState.value = assessment?.toFormState()
        }
    }

    private fun update(transform: (AssessmentFormState) -> AssessmentFormState) {
        _formState.update { it?.let(transform) }
    }

    fun onNameChange(value: String) {
        if (value.length <= AssessmentFormState.MAX_NAME_LENGTH) update { it.copy(name = value, nameTouched = true) }
    }
    fun onProjectOrSiteChange(value: String) = update { it.copy(projectOrSite = value) }
    fun onWorkerReferenceChange(value: String) = update { it.copy(workerReference = value) }
    fun onTaskTypeSelected(type: TaskType) = update { it.copy(taskType = type, taskTypeTouched = true) }
    fun onCustomTaskDescriptionChange(value: String) = update { it.copy(customTaskDescription = value, customTaskTouched = true) }
    fun onLoadTextChange(value: String) = update { it.withLoadText(value) }
    fun onSuddenForceChange(enabled: Boolean) = update { it.copy(suddenForce = enabled) }
    fun onCouplingSelected(quality: CouplingQuality) = update { it.copy(coupling = quality, couplingTouched = true) }

    fun save(onSaved: () -> Unit) {
        val state = _formState.value ?: return
        val base = original ?: return
        val updated = state.applyEditsTo(base) ?: return
        viewModelScope.launch {
            repository.save(updated)
            onSaved()
        }
    }

    companion object {
        fun factory(repository: AssessmentRepository, assessmentId: String) = viewModelFactory {
            initializer { EditAssessmentViewModel(repository, assessmentId) }
        }
    }
}
