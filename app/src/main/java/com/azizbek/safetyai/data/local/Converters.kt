package com.azizbek.safetyai.data.local

import androidx.room.TypeConverter
import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.domain.AssessmentStatus

/** Every domain enum stored as its name — stable across reorderings, unlike an ordinal. */
class Converters {
    @TypeConverter fun taskTypeToString(value: TaskType): String = value.name
    @TypeConverter fun taskTypeFromString(value: String): TaskType = TaskType.valueOf(value)

    @TypeConverter fun couplingToString(value: CouplingQuality): String = value.name
    @TypeConverter fun couplingFromString(value: String): CouplingQuality = CouplingQuality.valueOf(value)

    @TypeConverter fun sourceToString(value: AssessmentSource): String = value.name
    @TypeConverter fun sourceFromString(value: String): AssessmentSource = AssessmentSource.valueOf(value)

    @TypeConverter fun statusToString(value: AssessmentStatus): String = value.name
    @TypeConverter fun statusFromString(value: String): AssessmentStatus = AssessmentStatus.valueOf(value)
}
