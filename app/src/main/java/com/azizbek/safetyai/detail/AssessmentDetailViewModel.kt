package com.azizbek.safetyai.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.analysis.AnalysisInputContext
import com.azizbek.safetyai.analysis.AnalysisProgress
import com.azizbek.safetyai.analysis.SafetyAnalysisEngine
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import com.azizbek.safetyai.data.result.AnalysisResultStore
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.analysisInputs
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnalysisUiState(
    val isRunning: Boolean = false,
    val stage: String? = null,
    val fractionComplete: Float = 0f,
    val result: SafetyAnalysisResult? = null,
    val error: String? = null
)

class AssessmentDetailViewModel(
    private val repository: AssessmentRepository,
    private val analysisEngine: SafetyAnalysisEngine,
    private val analysisResultStore: AnalysisResultStore,
    private val assessmentId: String
) : ViewModel() {

    val assessment: StateFlow<Assessment?> =
        repository.observeById(assessmentId)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                null
            )

    private val _analysisState =
        MutableStateFlow(AnalysisUiState())

    val analysisState: StateFlow<AnalysisUiState> =
        _analysisState.asStateFlow()

    val analysisEngineAvailable: Boolean
        get() = analysisEngine.isAvailable

    fun requestAnalysis() {
        if (_analysisState.value.isRunning) return

        _analysisState.value = AnalysisUiState(
            isRunning = true,
            stage = "Preparing assessment",
            fractionComplete = 0f
        )

        viewModelScope.launch {
            val current = repository.getById(assessmentId)

            if (current == null) {
                _analysisState.value = AnalysisUiState(
                    error = "Assessment could not be found."
                )
                return@launch
            }

            if (!analysisEngine.isAvailable) {
                repository.save(
                    current.copy(
                        status = AssessmentStatus.MODEL_UNAVAILABLE,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                )

                _analysisState.value = AnalysisUiState(
                    error = "The on-device analysis engine is unavailable."
                )
                return@launch
            }

            val analyzingAssessment = current.copy(
                status = AssessmentStatus.ANALYZING,
                updatedAtEpochMs = System.currentTimeMillis()
            )

            try {
                repository.save(analyzingAssessment)

                val outcome = analysisEngine.analyze(
                    assessmentId = current.id,
                    videoFile = File(current.video.filePath),
                    context = AnalysisInputContext(
                        loadKg = current.loadKg,
                        suddenForce = current.suddenForce,
                        coupling = current.coupling,
                        taskType = current.taskType
                    ),
                    onProgress = { progress ->
                        if (progress is AnalysisProgress.Running) {
                            _analysisState.value =
                                _analysisState.value.copy(
                                    isRunning = true,
                                    stage = progress.stage,
                                    fractionComplete =
                                        progress.fractionComplete
                                            .coerceIn(0f, 1f),
                                    error = null
                                )
                        }
                    }
                )

                when (outcome) {
                    is AnalysisProgress.Completed -> {
                        val latest = repository.getById(assessmentId)
                        if (latest == null || latest.analysisInputs() != current.analysisInputs()) {
                            // Inputs changed or the assessment was deleted mid-run: drop the outdated result.
                            _analysisState.value = AnalysisUiState(
                                error = "The assessment changed during analysis. Run the analysis again."
                            )
                            return@launch
                        }
                        analysisResultStore.save(outcome.result)

                        repository.save(
                            current.copy(
                                status = AssessmentStatus.COMPLETED,
                                updatedAtEpochMs =
                                    System.currentTimeMillis()
                            )
                        )

                        _analysisState.value = AnalysisUiState(
                            isRunning = false,
                            stage = "Analysis completed",
                            fractionComplete = 1f,
                            result = outcome.result
                        )
                    }

                    is AnalysisProgress.Failed -> {
                        repository.save(
                            current.copy(
                                status =
                                    AssessmentStatus.ANALYSIS_FAILED,
                                updatedAtEpochMs =
                                    System.currentTimeMillis()
                            )
                        )

                        _analysisState.value = AnalysisUiState(
                            isRunning = false,
                            stage = "Analysis failed",
                            fractionComplete = 0f,
                            error = outcome.reason
                        )
                    }

                    is AnalysisProgress.Running -> {
                        /*
                         * analyze() must return Completed or Failed.
                         * Keep this guard so an invalid implementation
                         * cannot mark the assessment as completed.
                         */
                        repository.save(
                            current.copy(
                                status =
                                    AssessmentStatus.ANALYSIS_FAILED,
                                updatedAtEpochMs =
                                    System.currentTimeMillis()
                            )
                        )

                        _analysisState.value = AnalysisUiState(
                            error =
                                "Analysis ended without a final result."
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    repository.save(
                        current.copy(
                            status =
                                AssessmentStatus.READY_FOR_ANALYSIS,
                            updatedAtEpochMs =
                                System.currentTimeMillis()
                        )
                    )
                }

                _analysisState.value = AnalysisUiState(
                    error = "Analysis was cancelled."
                )

                throw cancelled
            } catch (error: Throwable) {
                repository.save(
                    current.copy(
                        status = AssessmentStatus.ANALYSIS_FAILED,
                        updatedAtEpochMs =
                            System.currentTimeMillis()
                    )
                )

                _analysisState.value = AnalysisUiState(
                    isRunning = false,
                    stage = "Analysis failed",
                    error = error.message
                        ?: "Unexpected analysis error."
                )
            }
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
        fun factory(
            repository: AssessmentRepository,
            analysisEngine: SafetyAnalysisEngine,
            analysisResultStore: AnalysisResultStore,
            assessmentId: String
        ) = viewModelFactory {
            initializer {
                AssessmentDetailViewModel(
                    repository = repository,
                    analysisEngine = analysisEngine,
                    analysisResultStore = analysisResultStore,
                    assessmentId = assessmentId
                )
            }
        }
    }
}
