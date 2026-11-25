package com.azizbek.safetyai.history

import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** In-memory [AssessmentRepository] for ViewModel unit tests — no Room, no Android framework. */
class FakeAssessmentRepository(initial: List<Assessment> = emptyList()) : AssessmentRepository {
    private val backing = MutableStateFlow(initial)
    val deleted = mutableListOf<Assessment>()

    override fun observeAll(): StateFlow<List<Assessment>> = backing
    override fun observeById(id: String): Flow<Assessment?> = backing.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun getById(id: String): Assessment? = backing.value.firstOrNull { it.id == id }
    override fun observeCount(): Flow<Int> = backing.map { it.size }

    override suspend fun save(assessment: Assessment) {
        val existing = backing.value.firstOrNull { it.id == assessment.id }
        // Mirrors RoomAssessmentRepository: the status of an existing row belongs to the analysis lifecycle.
        val stored = if (existing == null) assessment else assessment.copy(status = existing.status)
        backing.value = backing.value.filterNot { it.id == assessment.id } + stored
    }

    override suspend fun delete(assessment: Assessment) {
        deleted += assessment
        backing.value = backing.value.filterNot { it.id == assessment.id }
    }

    override suspend fun deleteAll() {
        deleted += backing.value
        backing.value = emptyList()
    }

    override suspend fun transitionStatus(id: String, from: Set<AssessmentStatus>, to: AssessmentStatus): Boolean {
        val current = backing.value.firstOrNull { it.id == id } ?: return false
        if (current.status !in from) return false
        backing.value = backing.value.map { if (it.id == id) it.copy(status = to) else it }
        return true
    }

    override suspend fun getAllIds(): Set<String> = backing.value.map { it.id }.toSet()
    override suspend fun recoverAndSweep() {}
    override suspend fun getAllVideoFilePaths(): List<String> = backing.value.map { it.video.filePath }
}
