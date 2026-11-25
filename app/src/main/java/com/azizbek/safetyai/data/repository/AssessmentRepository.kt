package com.azizbek.safetyai.data.repository

import com.azizbek.safetyai.data.local.AssessmentDao
import com.azizbek.safetyai.data.local.toDomain
import com.azizbek.safetyai.data.local.toEntity
import com.azizbek.safetyai.data.result.AnalysisResultStore
import com.azizbek.safetyai.data.video.VideoFileStore
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.analysisInputs
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
    /** Ids of every stored assessment, used to sweep orphaned analysis results. */
    suspend fun getAllIds(): Set<String>
    /** Recovers interrupted jobs (ANALYZING left by a dead process) and removes orphaned result files. */
    suspend fun recoverAndSweep()
    /** Every video file path currently owned by a saved assessment — used to identify abandoned draft files for cleanup. */
    suspend fun getAllVideoFilePaths(): List<String>
}

/** Room-backed [AssessmentRepository]. Deleting an assessment also deletes the video file it owns. */
class RoomAssessmentRepository(
    private val dao: AssessmentDao,
    private val videoFileStore: VideoFileStore,
    private val resultStore: AnalysisResultStore? = null
) : AssessmentRepository {

    override fun observeAll(): Flow<List<Assessment>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeById(id: String): Flow<Assessment?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): Assessment? = dao.getById(id)?.toDomain()

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun save(assessment: Assessment) {
        val existing = dao.getById(assessment.id)?.toDomain()
        if (existing == null) {
            dao.insert(assessment.toEntity())
            return
        }
        // Result is stale when an analysis input changed; descriptive edits keep it.
        val stale = existing.analysisInputs() != assessment.analysisInputs() &&
            existing.status in setOf(AssessmentStatus.COMPLETED, AssessmentStatus.ANALYZING, AssessmentStatus.ANALYSIS_FAILED)
        if (stale) {
            resultStore?.delete(assessment.id)
            dao.update(assessment.copy(status = AssessmentStatus.READY_FOR_ANALYSIS).toEntity())
        } else {
            dao.update(assessment.toEntity())
        }
    }

    override suspend fun delete(assessment: Assessment) {
        dao.delete(assessment.toEntity())
        videoFileStore.delete(assessment.video.filePath)
        resultStore?.delete(assessment.id)
    }

    override suspend fun deleteAll() {
        val paths = dao.getAllVideoFilePaths()
        dao.deleteAll()
        paths.forEach { videoFileStore.delete(it) }
        resultStore?.retainOnly(emptySet())
    }

    override suspend fun recoverAndSweep() {
        dao.resetStatus(AssessmentStatus.ANALYZING, AssessmentStatus.READY_FOR_ANALYSIS)
        resultStore?.retainOnly(getAllIds())
    }

    override suspend fun getAllIds(): Set<String> = dao.getAllIds().toSet()

    override suspend fun getAllVideoFilePaths(): List<String> = dao.getAllVideoFilePaths()
}
