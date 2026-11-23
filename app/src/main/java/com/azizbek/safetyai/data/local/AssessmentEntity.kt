package com.azizbek.safetyai.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.VideoMetadata

/**
 * Room row for one saved assessment, including its managed video's metadata.
 * A single table is enough for this phase — a real analysis-result table
 * joins in once the analysis engine exists (see [com.azizbek.safetyai.analysis]).
 */
@Entity(tableName = "assessments")
data class AssessmentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val projectOrSite: String?,
    val workerReference: String?,
    val taskType: TaskType,
    val customTaskDescription: String?,
    val loadKg: Double,
    val suddenForce: Boolean,
    val coupling: CouplingQuality,
    val source: AssessmentSource,
    val videoFilePath: String,
    val videoDurationMs: Long?,
    val videoWidthPx: Int?,
    val videoHeightPx: Int?,
    val videoFileSizeBytes: Long,
    val status: AssessmentStatus,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    @ColumnInfo(defaultValue = "0") val loadIsCategory: Boolean = false
)

fun AssessmentEntity.toDomain(): Assessment = Assessment(
    id = id,
    name = name,
    projectOrSite = projectOrSite,
    workerReference = workerReference,
    taskType = taskType,
    customTaskDescription = customTaskDescription,
    loadKg = loadKg,
    suddenForce = suddenForce,
    coupling = coupling,
    source = source,
    video = VideoMetadata(
        filePath = videoFilePath,
        durationMs = videoDurationMs,
        widthPx = videoWidthPx,
        heightPx = videoHeightPx,
        fileSizeBytes = videoFileSizeBytes
    ),
    status = status,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    loadIsCategory = loadIsCategory
)

fun Assessment.toEntity(): AssessmentEntity = AssessmentEntity(
    id = id,
    name = name,
    projectOrSite = projectOrSite,
    workerReference = workerReference,
    taskType = taskType,
    customTaskDescription = customTaskDescription,
    loadKg = loadKg,
    suddenForce = suddenForce,
    coupling = coupling,
    source = source,
    videoFilePath = video.filePath,
    videoDurationMs = video.durationMs,
    videoWidthPx = video.widthPx,
    videoHeightPx = video.heightPx,
    videoFileSizeBytes = video.fileSizeBytes,
    status = status,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    loadIsCategory = loadIsCategory
)
