package com.azizbek.safetyai.analysis

import java.io.File

/**
 * Runtime implementation while no validated model is installed. Always
 * reports unavailable — it never fabricates a score or sample result, even
 * in the installed app. Preview/test fixtures for [SafetyAnalysisResult]
 * live only in `@Preview`s and tests, never here.
 */
class UnavailableSafetyAnalysisEngine : SafetyAnalysisEngine {
    override val isAvailable: Boolean = false

    override suspend fun analyze(
        assessmentId: String,
        videoFile: File,
        context: AnalysisInputContext,
        onProgress: (AnalysisProgress) -> Unit
    ): AnalysisProgress {
        val failure = AnalysisProgress.Failed(
            reason = "This build does not yet include the validated Safety AI analysis engine."
        )
        onProgress(failure)
        return failure
    }
}
