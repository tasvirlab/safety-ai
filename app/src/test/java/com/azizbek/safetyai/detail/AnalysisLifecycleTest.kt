package com.azizbek.safetyai.detail

import com.azizbek.safetyai.analysis.ActivityFindings
import com.azizbek.safetyai.analysis.AnalysisInputContext
import com.azizbek.safetyai.analysis.AnalysisProgress
import com.azizbek.safetyai.analysis.RebaComponents
import com.azizbek.safetyai.analysis.RiskLevel
import com.azizbek.safetyai.analysis.SafetyAnalysisEngine
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.data.result.AnalysisResultStore
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.VideoMetadata
import com.azizbek.safetyai.history.FakeAssessmentRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A deleted, edited or cancelled assessment must never be restored or overwritten by a finishing analysis job. */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisLifecycleTest {
    @get:Rule val folder = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class GatedEngine : SafetyAnalysisEngine {
        val gate = CompletableDeferred<AnalysisProgress>()
        override val isAvailable = true
        override suspend fun analyze(
            assessmentId: String,
            videoFile: File,
            context: AnalysisInputContext,
            onProgress: (AnalysisProgress) -> Unit
        ): AnalysisProgress = gate.await()
    }

    private fun assessment() = Assessment(
        id = "abc", name = "Lift", projectOrSite = null, workerReference = null,
        taskType = TaskType.LIFTING, customTaskDescription = null, loadKg = 10.0, suddenForce = false,
        coupling = CouplingQuality.GOOD, source = AssessmentSource.CAMERA,
        video = VideoMetadata("/tmp/x.mp4", 5000, 1920, 1080, 100),
        status = AssessmentStatus.READY_FOR_ANALYSIS, createdAtEpochMs = 0, updatedAtEpochMs = 0
    )

    private fun completed() = AnalysisProgress.Completed(
        SafetyAnalysisResult(
            assessmentId = "abc", processingStartedAtEpochMs = 0, processingCompletedAtEpochMs = 1,
            inputQualityChecks = emptyList(), frames = emptyList(),
            reba = RebaComponents(1, 1, 1, 1, 1, 1, 1, 2, RiskLevel.LOW), bodyRegionFindings = emptyList(),
            activity = ActivityFindings(emptyList(), emptyList(), 0, emptyList(), false),
            overallRiskLevel = RiskLevel.LOW, highestRiskTimestampMs = 0, coveragePercent = 1f,
            recommendations = emptyList(), warnings = emptyList(), processingErrors = emptyList()
        )
    )

    private class Setup(
        val repo: FakeAssessmentRepository,
        val engine: GatedEngine,
        val store: AnalysisResultStore,
        val vm: AssessmentDetailViewModel
    )

    /** Result files are written on the real IO dispatcher, so poll until the job reports it has finished. */
    private fun TestScope.settle(s: Setup) {
        repeat(300) {
            advanceUntilIdle()
            if (!s.vm.analysisState.value.isRunning) return
            Thread.sleep(10)
        }
    }

    private fun setup(): Setup {
        val repo = FakeAssessmentRepository(listOf(assessment()))
        val engine = GatedEngine()
        val store = AnalysisResultStore(File(folder.root, "results"))
        return Setup(repo, engine, store, AssessmentDetailViewModel(repo, engine, store, "abc"))
    }

    @Test fun `normal completion commits status and result`() = runTest(dispatcher) {
        val s = setup()
        s.vm.requestAnalysis(); advanceUntilIdle()
        assertEquals(AssessmentStatus.ANALYZING, s.repo.getById("abc")!!.status)
        s.engine.gate.complete(completed()); settle(s)
        assertEquals(AssessmentStatus.COMPLETED, s.repo.getById("abc")!!.status)
        assertTrue(s.store.exists("abc"))
    }

    @Test fun `deleting during analysis is not undone by completion`() = runTest(dispatcher) {
        val s = setup()
        s.vm.requestAnalysis(); advanceUntilIdle()
        s.repo.delete(s.repo.getById("abc")!!)
        s.engine.gate.complete(completed()); settle(s)
        assertNull(s.repo.getById("abc"))
        assertFalse(s.store.exists("abc"))
    }

    @Test fun `deleting during analysis is not undone by failure`() = runTest(dispatcher) {
        val s = setup()
        s.vm.requestAnalysis(); advanceUntilIdle()
        s.repo.delete(s.repo.getById("abc")!!)
        s.engine.gate.complete(AnalysisProgress.Failed("boom")); settle(s)
        assertNull(s.repo.getById("abc"))
    }

    @Test fun `deleting during analysis is not undone by cancellation`() = runTest(dispatcher) {
        val s = setup()
        s.vm.requestAnalysis(); advanceUntilIdle()
        s.repo.delete(s.repo.getById("abc")!!)
        s.engine.gate.completeExceptionally(CancellationException("cancelled")); settle(s)
        assertNull(s.repo.getById("abc"))
    }

    @Test fun `stale completion after an input change is discarded`() = runTest(dispatcher) {
        val s = setup()
        s.vm.requestAnalysis(); advanceUntilIdle()
        // What RoomAssessmentRepository does when an analysis input changes mid-run.
        s.repo.transitionStatus("abc", setOf(AssessmentStatus.ANALYZING), AssessmentStatus.READY_FOR_ANALYSIS)
        s.engine.gate.complete(completed()); settle(s)
        assertEquals(AssessmentStatus.READY_FOR_ANALYSIS, s.repo.getById("abc")!!.status)
        assertFalse(s.store.exists("abc"))
    }

    @Test fun `metadata edited during analysis survives completion`() = runTest(dispatcher) {
        val s = setup()
        s.vm.requestAnalysis(); advanceUntilIdle()
        s.repo.save(s.repo.getById("abc")!!.copy(name = "Renamed"))
        s.engine.gate.complete(completed()); settle(s)
        val stored = s.repo.getById("abc")!!
        assertEquals("Renamed", stored.name)
        assertEquals(AssessmentStatus.COMPLETED, stored.status)
        assertNotNull(s.store.load("abc"))
    }

    @Test fun `cancellation returns an existing assessment to ready`() = runTest(dispatcher) {
        val s = setup()
        s.vm.requestAnalysis(); advanceUntilIdle()
        s.engine.gate.completeExceptionally(CancellationException("cancelled")); settle(s)
        assertEquals(AssessmentStatus.READY_FOR_ANALYSIS, s.repo.getById("abc")!!.status)
    }

    @Test fun `repeated request while running does not start a second job`() = runTest(dispatcher) {
        val s = setup()
        s.vm.requestAnalysis(); s.vm.requestAnalysis(); advanceUntilIdle()
        s.engine.gate.complete(completed()); settle(s)
        assertEquals(AssessmentStatus.COMPLETED, s.repo.getById("abc")!!.status)
    }
}
