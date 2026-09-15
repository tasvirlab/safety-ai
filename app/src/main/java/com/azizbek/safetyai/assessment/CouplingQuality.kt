package com.azizbek.safetyai.assessment

/**
 * How securely the worker can hold or control the load — task context for a
 * future REBA coupling modifier. Never shown to the user as a numeric score.
 */
enum class CouplingQuality {
    GOOD,
    FAIR,
    POOR,
    UNACCEPTABLE,

    /** Explicit "doesn't apply" — only offered when the load is 0 kg. */
    NOT_APPLICABLE
}
