package com.azizbek.safetyai.analysis

import java.io.File

/**
 * Replaceable analysis contract. A future validated implementation (RTMPose
 * → PoseMamba → REBA → Activity Layer) plugs in here without touching any
 * screen. The call is a plain suspend function so it can be driven directly
 * or wrapped in a WorkManager `CoroutineWorker` for background orchestration
 * — no worker is implemented yet since there is nothing for it to run.
 */
interface SafetyAnalysisEngine {
    /** True only when a validated model is actually installed in this build. */
    val isAvailable: Boolean

    /**
     * Runs analysis on [videoFile] for [assessmentId], reporting [onProgress] as work advances.
     * Cooperative cancellation: callers cancel via the coroutine scope, not a separate token.
     */
    suspend fun analyze(
        assessmentId: String,
        videoFile: File,
        context: AnalysisInputContext,
        onProgress: (AnalysisProgress) -> Unit
    ): AnalysisProgress
}
