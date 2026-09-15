package com.azizbek.safetyai.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AssessmentsUiState {
    data object Loading : AssessmentsUiState
    data object Empty : AssessmentsUiState
    data class Populated(val items: List<Assessment>) : AssessmentsUiState
    data object SearchNoResults : AssessmentsUiState
    data class Error(val message: String) : AssessmentsUiState
}

class AssessmentsViewModel(private val repository: AssessmentRepository) : ViewModel() {
    val searchQuery = MutableStateFlow("")
    val taskTypeFilter = MutableStateFlow<TaskType?>(null)
    val statusFilter = MutableStateFlow<AssessmentStatus?>(null)
    val sortNewestFirst = MutableStateFlow(true)

    private val rawAssessments: StateFlow<List<Assessment>?> = repository.observeAll()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<AssessmentsUiState> = combine(
        rawAssessments, searchQuery, taskTypeFilter, statusFilter, sortNewestFirst
    ) { all, query, taskType, status, newestFirst ->
        if (all == null) return@combine AssessmentsUiState.Loading
        if (all.isEmpty()) return@combine AssessmentsUiState.Empty

        var filtered = all.filter { assessment ->
            val matchesQuery = query.isBlank() || listOfNotNull(
                assessment.name, assessment.projectOrSite, assessment.workerReference
            ).any { it.contains(query, ignoreCase = true) }
            val matchesTaskType = taskType == null || assessment.taskType == taskType
            val matchesStatus = status == null || assessment.status == status
            matchesQuery && matchesTaskType && matchesStatus
        }
        filtered = if (newestFirst) filtered.sortedByDescending { it.createdAtEpochMs } else filtered.sortedBy { it.createdAtEpochMs }

        if (filtered.isEmpty()) AssessmentsUiState.SearchNoResults else AssessmentsUiState.Populated(filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssessmentsUiState.Loading)

    fun delete(assessment: Assessment) {
        viewModelScope.launch { repository.delete(assessment) }
    }

    companion object {
        fun factory(repository: AssessmentRepository) = viewModelFactory {
            initializer { AssessmentsViewModel(repository) }
        }
    }
}
