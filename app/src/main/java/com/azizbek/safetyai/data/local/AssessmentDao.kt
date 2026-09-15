package com.azizbek.safetyai.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.azizbek.safetyai.domain.AssessmentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AssessmentDao {
    @Query("SELECT * FROM assessments ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<AssessmentEntity>>

    @Query("SELECT * FROM assessments WHERE id = :id")
    fun observeById(id: String): Flow<AssessmentEntity?>

    @Query("SELECT * FROM assessments WHERE id = :id")
    suspend fun getById(id: String): AssessmentEntity?

    @Query("SELECT COUNT(*) FROM assessments")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM assessments WHERE status = :status")
    suspend fun countByStatus(status: AssessmentStatus): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AssessmentEntity)

    @Update
    suspend fun update(entity: AssessmentEntity)

    @Delete
    suspend fun delete(entity: AssessmentEntity)

    @Query("DELETE FROM assessments")
    suspend fun deleteAll()

    @Query("SELECT videoFilePath FROM assessments")
    suspend fun getAllVideoFilePaths(): List<String>
}
