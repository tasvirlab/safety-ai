package com.azizbek.safetyai.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.data.result.AnalysisResultStore
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReportDetailUiState {
    data object Loading : ReportDetailUiState

    data class Ready(
        val assessment: Assessment,
        val result: SafetyAnalysisResult
    ) : ReportDetailUiState

    data class Unavailable(
        val message: String
    ) : ReportDetailUiState
}

class ReportDetailViewModel(
    private val repository: AssessmentRepository,
    private val resultStore: AnalysisResultStore,
    private val assessmentId: String
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ReportDetailUiState>(
            ReportDetailUiState.Loading
        )

    val uiState: StateFlow<ReportDetailUiState> =
        _uiState.asStateFlow()

    init {
        loadReport()
    }

    private fun loadReport() {
        viewModelScope.launch {
            _uiState.value = try {
                val assessment = repository.getById(assessmentId)

                when {
                    assessment == null ->
                        ReportDetailUiState.Unavailable(
                            "Assessment was not found."
                        )

                    assessment.status != AssessmentStatus.COMPLETED ->
                        ReportDetailUiState.Unavailable(
                            "This assessment has not completed analysis."
                        )

                    else -> {
                        val result = resultStore.load(assessmentId)

                        if (result == null) {
                            ReportDetailUiState.Unavailable(
                                "The saved analysis result could not be found."
                            )
                        } else {
                            ReportDetailUiState.Ready(
                                assessment = assessment,
                                result = result
                            )
                        }
                    }
                }
            } catch (error: Exception) {
                ReportDetailUiState.Unavailable(
                    error.message ?: "The report could not be loaded."
                )
            }
        }
    }

    companion object {
        fun factory(
            repository: AssessmentRepository,
            resultStore: AnalysisResultStore,
            assessmentId: String
        ) = viewModelFactory {
            initializer {
                ReportDetailViewModel(
                    repository = repository,
                    resultStore = resultStore,
                    assessmentId = assessmentId
                )
            }
        }
    }
}