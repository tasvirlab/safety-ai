package com.azizbek.safetyai.domain

/**
 * Metadata about a managed (app-private) video file. [filePath] is an
 * internal storage path — never render it in the UI (log or display); it
 * exists only to load the file for playback or deletion.
 */
data class VideoMetadata(
    val filePath: String,
    val durationMs: Long?,
    val widthPx: Int?,
    val heightPx: Int?,
    val fileSizeBytes: Long
)
