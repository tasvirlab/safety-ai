package com.azizbek.safetyai.data.repository

import com.azizbek.safetyai.data.local.AssessmentDao
import com.azizbek.safetyai.data.local.toDomain
import com.azizbek.safetyai.data.local.toEntity
import com.azizbek.safetyai.data.video.VideoFileStore
import com.azizbek.safetyai.domain.Assessment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AssessmentRepository {
    fun observeAll(): Flow<List<Assessment>>
    fun observeById(id: String): Flow<Assessment?>
    suspend fun getById(id: String): Assessment?
    fun observeCount(): Flow<Int>
    suspend fun save(assessment: Assessment)
    suspend fun delete(assessment: Assessment)
    suspend fun deleteAll()
    /** Every video file path currently owned by a saved assessment — used to identify abandoned draft files for cleanup. */
    suspend fun getAllVideoFilePaths(): List<String>
}

/** Room-backed [AssessmentRepository]. Deleting an assessment also deletes the video file it owns. */
class RoomAssessmentRepository(
    private val dao: AssessmentDao,
    private val videoFileStore: VideoFileStore
) : AssessmentRepository {

    override fun observeAll(): Flow<List<Assessment>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeById(id: String): Flow<Assessment?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): Assessment? = dao.getById(id)?.toDomain()

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun save(assessment: Assessment) {
        val existing = dao.getById(assessment.id)
        if (existing == null) dao.insert(assessment.toEntity()) else dao.update(assessment.toEntity())
    }

    override suspend fun delete(assessment: Assessment) {
        dao.delete(assessment.toEntity())
        videoFileStore.delete(assessment.video.filePath)
    }

    override suspend fun deleteAll() {
        val paths = dao.getAllVideoFilePaths()
        dao.deleteAll()
        paths.forEach { videoFileStore.delete(it) }
    }

    override suspend fun getAllVideoFilePaths(): List<String> = dao.getAllVideoFilePaths()
}
