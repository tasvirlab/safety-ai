package com.azizbek.safetyai.domain

fun AssessmentStatus.label(): String = when (this) {
    AssessmentStatus.DRAFT -> "Draft"
    AssessmentStatus.READY_FOR_ANALYSIS -> "Ready for analysis"
    AssessmentStatus.ANALYZING -> "Analyzing"
    AssessmentStatus.COMPLETED -> "Completed"
    AssessmentStatus.ANALYSIS_FAILED -> "Analysis failed"
    AssessmentStatus.MODEL_UNAVAILABLE -> "Model unavailable"
}
