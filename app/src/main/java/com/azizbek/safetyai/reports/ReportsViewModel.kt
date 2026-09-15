package com.azizbek.safetyai.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ReportsUiState {
    data object Loading : ReportsUiState
    data object Empty : ReportsUiState
    data class Populated(val items: List<Assessment>) : ReportsUiState
    data object SearchNoResults : ReportsUiState
}

/** Only [AssessmentStatus.COMPLETED] assessments are report-eligible — never READY_FOR_ANALYSIS or MODEL_UNAVAILABLE. */
class ReportsViewModel(repository: AssessmentRepository) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    private val completed: StateFlow<List<Assessment>?> = repository.observeAll()
        .map { all -> all.filter { it.status == AssessmentStatus.COMPLETED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<ReportsUiState> = combine(completed, searchQuery) { items, query ->
        when {
            items == null -> ReportsUiState.Loading
            items.isEmpty() -> ReportsUiState.Empty
            else -> {
                val filtered = if (query.isBlank()) items else items.filter { it.name.contains(query, ignoreCase = true) }
                if (filtered.isEmpty()) ReportsUiState.SearchNoResults else ReportsUiState.Populated(filtered.sortedByDescending { it.createdAtEpochMs })
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState.Loading)

    companion object {
        fun factory(repository: AssessmentRepository) = viewModelFactory {
            initializer { ReportsViewModel(repository) }
        }
    }
}
