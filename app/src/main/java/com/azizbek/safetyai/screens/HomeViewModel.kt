package com.azizbek.safetyai.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.domain.Assessment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Home only ever needs the 3 most recent assessments — [com.azizbek.safetyai.history.AssessmentsViewModel] owns the full list. */
class HomeViewModel(repository: AssessmentRepository) : ViewModel() {
    val recentAssessments: StateFlow<List<Assessment>> = repository.observeAll()
        .map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(repository: AssessmentRepository) = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
