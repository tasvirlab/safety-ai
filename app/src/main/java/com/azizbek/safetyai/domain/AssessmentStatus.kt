package com.azizbek.safetyai.domain

/** Truthful lifecycle state of a saved assessment — never a stand-in for a real analysis result. */
enum class AssessmentStatus {
    /** Details/video started but not yet saved as a complete assessment. Reserved for future resumable drafts. */
    DRAFT,
    /** Saved with a managed video, waiting on the (not-yet-integrated) analysis engine. */
    READY_FOR_ANALYSIS,
    ANALYZING,
    COMPLETED,
    ANALYSIS_FAILED,
    /** Analyze was requested but no validated engine is installed in this build. */
    MODEL_UNAVAILABLE
}
