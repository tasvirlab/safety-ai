package com.azizbek.safetyai.detail

import com.azizbek.safetyai.analysis.UnavailableSafetyAnalysisEngine
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/** Model-unavailable behavior: no engine installed means an honest status change, never a fabricated result. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssessmentDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun assessment() = Assessment(
        id = "abc", name = "Floor-to-waist lifting", projectOrSite = null, workerReference = null,
        taskType = TaskType.LIFTING, customTaskDescription = null, loadKg = 10.0, suddenForce = false,
        coupling = CouplingQuality.GOOD, source = AssessmentSource.CAMERA,
        video = VideoMetadata("/tmp/x.mp4", 5000, 1920, 1080, 100),
        status = AssessmentStatus.READY_FOR_ANALYSIS, createdAtEpochMs = 0, updatedAtEpochMs = 0
    )

    @Test
    fun `engine is reported unavailable`() {
        val viewModel = AssessmentDetailViewModel(FakeAssessmentRepository(), UnavailableSafetyAnalysisEngine(), "abc")
        assertFalse(viewModel.analysisEngineAvailable)
    }

    @Test
    fun `requesting analysis without an engine records MODEL_UNAVAILABLE, never a fabricated result`() = runTest(testDispatcher) {
        val repo = FakeAssessmentRepository(listOf(assessment()))
        val viewModel = AssessmentDetailViewModel(repo, UnavailableSafetyAnalysisEngine(), "abc")
        val job = launch { viewModel.assessment.collect {} }
        advanceUntilIdle()

        viewModel.requestAnalysis()
        advanceUntilIdle()

        val updated = repo.observeAll().value.first { it.id == "abc" }
        assertEquals(AssessmentStatus.MODEL_UNAVAILABLE, updated.status)
        job.cancel()
    }
}
