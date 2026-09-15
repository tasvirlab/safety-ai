package com.azizbek.safetyai.reports

import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.VideoMetadata
import com.azizbek.safetyai.history.FakeAssessmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Only [AssessmentStatus.COMPLETED] assessments are ever report-eligible. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun assessment(id: String, status: AssessmentStatus) = Assessment(
        id = id, name = "Assessment $id", projectOrSite = null, workerReference = null,
        taskType = TaskType.LIFTING, customTaskDescription = null, loadKg = 5.0, suddenForce = false,
        coupling = CouplingQuality.GOOD, source = AssessmentSource.CAMERA,
        video = VideoMetadata("/tmp/$id.mp4", 1000, 1920, 1080, 100),
        status = status, createdAtEpochMs = 0, updatedAtEpochMs = 0
    )

    @Test
    fun `READY_FOR_ANALYSIS and MODEL_UNAVAILABLE never appear in reports`() = runTest(testDispatcher) {
        val repo = FakeAssessmentRepository(
            listOf(
                assessment("1", AssessmentStatus.READY_FOR_ANALYSIS),
                assessment("2", AssessmentStatus.MODEL_UNAVAILABLE),
                assessment("3", AssessmentStatus.ANALYZING),
                assessment("4", AssessmentStatus.ANALYSIS_FAILED)
            )
        )
        val viewModel = ReportsViewModel(repo)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(ReportsUiState.Empty, viewModel.uiState.value)
        job.cancel()
    }

    @Test
    fun `completed assessments are listed`() = runTest(testDispatcher) {
        val repo = FakeAssessmentRepository(
            listOf(assessment("1", AssessmentStatus.READY_FOR_ANALYSIS), assessment("2", AssessmentStatus.COMPLETED))
        )
        val viewModel = ReportsViewModel(repo)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val state = viewModel.uiState.value as ReportsUiState.Populated
        assertEquals(listOf("2"), state.items.map { it.id })
        job.cancel()
    }
}
