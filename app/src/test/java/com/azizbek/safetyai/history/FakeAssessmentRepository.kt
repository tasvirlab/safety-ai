package com.azizbek.safetyai.history

import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.domain.Assessment
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
        backing.value = backing.value.filterNot { it.id == assessment.id } + assessment
    }

    override suspend fun delete(assessment: Assessment) {
        deleted += assessment
        backing.value = backing.value.filterNot { it.id == assessment.id }
    }

    override suspend fun deleteAll() {
        deleted += backing.value
        backing.value = emptyList()
    }

    override suspend fun getAllIds(): Set<String> = backing.value.map { it.id }.toSet()
    override suspend fun recoverAndSweep() {}
    override suspend fun getAllVideoFilePaths(): List<String> = backing.value.map { it.video.filePath }
}
