package com.azizbek.safetyai.history

import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.VideoMetadata
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [AssessmentsViewModel.uiState] is a `WhileSubscribed` [kotlinx.coroutines.flow.StateFlow] — it
 * only starts computing once something collects it, so every test subscribes
 * with a background `launch` before reading `.value`, then advances the
 * shared [StandardTestDispatcher] (installed as `Dispatchers.Main`, the same
 * dispatcher `viewModelScope` runs on) past that point.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssessmentsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun assessment(
        id: String,
        name: String,
        taskType: TaskType = TaskType.LIFTING,
        status: AssessmentStatus = AssessmentStatus.READY_FOR_ANALYSIS,
        createdAtEpochMs: Long = 0L,
        projectOrSite: String? = null
    ) = Assessment(
        id = id, name = name, projectOrSite = projectOrSite, workerReference = null,
        taskType = taskType, customTaskDescription = null, loadKg = 5.0, suddenForce = false,
        coupling = CouplingQuality.GOOD, source = AssessmentSource.CAMERA,
        video = VideoMetadata("/tmp/$id.mp4", 1000, 1920, 1080, 100),
        status = status, createdAtEpochMs = createdAtEpochMs, updatedAtEpochMs = createdAtEpochMs
    )

    @Test
    fun `empty repository yields Empty state`() = runTest(testDispatcher) {
        val viewModel = AssessmentsViewModel(FakeAssessmentRepository(emptyList()))
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(AssessmentsUiState.Empty, viewModel.uiState.value)
        job.cancel()
    }

    @Test
    fun `search filters by name, project and worker reference`() = runTest(testDispatcher) {
        val repo = FakeAssessmentRepository(
            listOf(
                assessment("1", "Floor lifting", projectOrSite = "Tower A"),
                assessment("2", "Overhead reach", projectOrSite = "Tower B")
            )
        )
        val viewModel = AssessmentsViewModel(repo)
        val job = launch { viewModel.uiState.collect {} }
        viewModel.searchQuery.value = "tower a"
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state is AssessmentsUiState.Populated)
        assertEquals(listOf("1"), (state as AssessmentsUiState.Populated).items.map { it.id })
        job.cancel()
    }

    @Test
    fun `search with no matches yields SearchNoResults`() = runTest(testDispatcher) {
        val repo = FakeAssessmentRepository(listOf(assessment("1", "Floor lifting")))
        val viewModel = AssessmentsViewModel(repo)
        val job = launch { viewModel.uiState.collect {} }
        viewModel.searchQuery.value = "nonexistent"
        advanceUntilIdle()
        assertEquals(AssessmentsUiState.SearchNoResults, viewModel.uiState.value)
        job.cancel()
    }

    @Test
    fun `task type filter narrows results`() = runTest(testDispatcher) {
        val repo = FakeAssessmentRepository(
            listOf(
                assessment("1", "A", taskType = TaskType.LIFTING),
                assessment("2", "B", taskType = TaskType.CARRYING)
            )
        )
        val viewModel = AssessmentsViewModel(repo)
        val job = launch { viewModel.uiState.collect {} }
        viewModel.taskTypeFilter.value = TaskType.CARRYING
        advanceUntilIdle()
        val state = viewModel.uiState.value as AssessmentsUiState.Populated
        assertEquals(listOf("2"), state.items.map { it.id })
        job.cancel()
    }

    @Test
    fun `status filter narrows results`() = runTest(testDispatcher) {
        val repo = FakeAssessmentRepository(
            listOf(
                assessment("1", "A", status = AssessmentStatus.READY_FOR_ANALYSIS),
                assessment("2", "B", status = AssessmentStatus.COMPLETED)
            )
        )
        val viewModel = AssessmentsViewModel(repo)
        val job = launch { viewModel.uiState.collect {} }
        viewModel.statusFilter.value = AssessmentStatus.COMPLETED
        advanceUntilIdle()
        val state = viewModel.uiState.value as AssessmentsUiState.Populated
        assertEquals(listOf("2"), state.items.map { it.id })
        job.cancel()
    }

    @Test
    fun `sort toggles between newest and oldest first`() = runTest(testDispatcher) {
        val repo = FakeAssessmentRepository(
            listOf(assessment("old", "A", createdAtEpochMs = 100), assessment("new", "B", createdAtEpochMs = 200))
        )
        val viewModel = AssessmentsViewModel(repo)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val newestFirst = viewModel.uiState.value as AssessmentsUiState.Populated
        assertEquals(listOf("new", "old"), newestFirst.items.map { it.id })

        viewModel.sortNewestFirst.value = false
        advanceUntilIdle()
        val oldestFirst = viewModel.uiState.value as AssessmentsUiState.Populated
        assertEquals(listOf("old", "new"), oldestFirst.items.map { it.id })
        job.cancel()
    }

    @Test
    fun `delete removes the assessment via the repository`() = runTest(testDispatcher) {
        val target = assessment("1", "A")
        val repo = FakeAssessmentRepository(listOf(target))
        val viewModel = AssessmentsViewModel(repo)
        viewModel.delete(target)
        advanceUntilIdle()
        assertTrue(repo.deleted.contains(target))
    }
}
