package com.azizbek.safetyai.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.data.local.AppDatabase
import com.azizbek.safetyai.data.repository.RoomAssessmentRepository
import com.azizbek.safetyai.data.video.VideoFileStore
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.VideoMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Room (in-memory) + real app-private file storage — persistence and delete-cascade behavior. */
@RunWith(AndroidJUnit4::class)
class AssessmentRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomAssessmentRepository
    private lateinit var videoFileStore: VideoFileStore
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        videoFileStore = VideoFileStore(context)
        repository = RoomAssessmentRepository(db.assessmentDao(), videoFileStore)
    }

    @After
    fun tearDown() { db.close() }

    private fun sampleAssessment(videoFile: File) = Assessment(
        id = UUID.randomUUID().toString(),
        name = "Floor-to-waist lifting",
        projectOrSite = null,
        workerReference = null,
        taskType = TaskType.LIFTING,
        customTaskDescription = null,
        loadKg = 10.0,
        suddenForce = false,
        coupling = CouplingQuality.GOOD,
        source = AssessmentSource.CAMERA,
        video = VideoMetadata(videoFile.absolutePath, 5000, 1920, 1080, videoFile.length()),
        status = AssessmentStatus.READY_FOR_ANALYSIS,
        createdAtEpochMs = System.currentTimeMillis(),
        updatedAtEpochMs = System.currentTimeMillis()
    )

    @Test
    fun saveThenObserveAllReturnsTheAssessment() = runBlocking {
        val file = videoFileStore.newRecordingFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val assessment = sampleAssessment(file)
        repository.save(assessment)
        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(assessment.id, all.first().id)
    }

    @Test
    fun deleteAlsoDeletesTheOwnedVideoFile() = runBlocking {
        val file = videoFileStore.newRecordingFile().apply { writeBytes(byteArrayOf(1)) }
        val assessment = sampleAssessment(file)
        repository.save(assessment)
        assertTrue(file.exists())

        repository.delete(assessment)

        assertFalse(file.exists())
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun deleteAllRemovesEveryAssessmentAndItsVideoFile() = runBlocking {
        val fileA = videoFileStore.newRecordingFile().apply { writeBytes(byteArrayOf(1)) }
        val fileB = videoFileStore.newRecordingFile().apply { writeBytes(byteArrayOf(2)) }
        repository.save(sampleAssessment(fileA))
        repository.save(sampleAssessment(fileB))

        repository.deleteAll()

        assertTrue(repository.observeAll().first().isEmpty())
        assertFalse(fileA.exists())
        assertFalse(fileB.exists())
    }

    @Test
    fun saveTwiceWithTheSameIdUpdatesRatherThanDuplicates() = runBlocking {
        val file = videoFileStore.newRecordingFile().apply { writeBytes(byteArrayOf(1)) }
        val assessment = sampleAssessment(file)
        repository.save(assessment)
        repository.save(assessment.copy(name = "Updated name"))

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Updated name", all.first().name)
    }

    @Test
    fun getByIdReturnsNullForAnUnknownId() = runBlocking {
        assertNull(repository.getById("does-not-exist"))
    }
}
