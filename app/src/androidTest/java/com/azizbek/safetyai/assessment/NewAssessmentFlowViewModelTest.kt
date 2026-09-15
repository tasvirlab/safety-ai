package com.azizbek.safetyai.assessment

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.data.video.VideoFileStore
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory fake — real Room isn't needed to test the flow ViewModel's own save/discard logic. */
private class RecordingFakeRepository : AssessmentRepository {
    val saved = mutableListOf<Assessment>()
    override fun observeAll(): StateFlow<List<Assessment>> = MutableStateFlow(saved)
    override fun observeById(id: String) = throw UnsupportedOperationException()
    override suspend fun getById(id: String): Assessment? = saved.firstOrNull { it.id == id }
    override fun observeCount() = throw UnsupportedOperationException()
    override suspend fun save(assessment: Assessment) { saved += assessment }
    override suspend fun delete(assessment: Assessment) { saved.remove(assessment) }
    override suspend fun deleteAll() { saved.clear() }
    override suspend fun getAllVideoFilePaths(): List<String> = saved.map { it.video.filePath }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NewAssessmentFlowViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun sampleDraft() = AssessmentDraft(
        source = AssessmentSource.CAMERA,
        name = "Floor-to-waist lifting",
        projectOrSite = null,
        workerReference = null,
        taskType = TaskType.LIFTING,
        customTaskDescription = null,
        loadKg = 10.0,
        suddenForce = false,
        coupling = CouplingQuality.GOOD
    )

    @Test
    fun repeatedSaveTapsPersistExactlyOnce() = runTest(testDispatcher) {
        val repository = RecordingFakeRepository()
        val videoFileStore = VideoFileStore(ApplicationProvider.getApplicationContext())
        val viewModel = NewAssessmentFlowViewModel(repository, videoFileStore)

        val videoFile = videoFileStore.newRecordingFile().apply { writeBytes(byteArrayOf(1)) }
        viewModel.setDraft(sampleDraft())
        viewModel.setVideo(videoFile.absolutePath, VideoMetadata(videoFile.absolutePath, 1000, 1920, 1080, videoFile.length()))

        val savedIds = mutableListOf<String>()
        // Simulate several rapid taps on "Save assessment" before the UI can disable the button.
        repeat(5) { viewModel.save { id -> savedIds += id } }
        advanceUntilIdle()

        assertEquals(1, repository.saved.size)
        assertEquals(1, savedIds.distinct().size)
    }

    @Test
    fun saveDoesNothingWithoutADraftOrVideo() = runTest(testDispatcher) {
        val repository = RecordingFakeRepository()
        val videoFileStore = VideoFileStore(ApplicationProvider.getApplicationContext())
        val viewModel = NewAssessmentFlowViewModel(repository, videoFileStore)

        var savedId: String? = null
        viewModel.save { savedId = it }
        advanceUntilIdle()

        assertEquals(0, repository.saved.size)
        assertNull(savedId)
    }
}
