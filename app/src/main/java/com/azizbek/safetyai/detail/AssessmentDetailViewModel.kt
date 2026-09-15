package com.azizbek.safetyai.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.analysis.SafetyAnalysisEngine
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssessmentDetailViewModel(
    private val repository: AssessmentRepository,
    private val analysisEngine: SafetyAnalysisEngine,
    assessmentId: String
) : ViewModel() {

    val assessment: StateFlow<Assessment?> = repository.observeById(assessmentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val analysisEngineAvailable: Boolean get() = analysisEngine.isAvailable

    /** No validated engine is installed, so this only ever records the honest [AssessmentStatus.MODEL_UNAVAILABLE] state — never a fabricated result. */
    fun requestAnalysis() {
        val current = assessment.value ?: return
        if (analysisEngine.isAvailable) return // nothing in this build ever reaches here
        viewModelScope.launch {
            repository.save(current.copy(status = AssessmentStatus.MODEL_UNAVAILABLE, updatedAtEpochMs = System.currentTimeMillis()))
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val current = assessment.value ?: return
        viewModelScope.launch {
            repository.delete(current)
            onDeleted()
        }
    }

    companion object {
        fun factory(repository: AssessmentRepository, analysisEngine: SafetyAnalysisEngine, assessmentId: String) = viewModelFactory {
            initializer { AssessmentDetailViewModel(repository, analysisEngine, assessmentId) }
        }
    }
}
