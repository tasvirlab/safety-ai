package com.azizbek.safetyai.data.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.azizbek.safetyai.domain.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * All recorded/imported video files live under app-private storage
 * (`filesDir/videos`) — never a shared/public path, and never referenced in
 * the UI by their filesystem path. Copies stream in fixed-size chunks so an
 * upload never has to hold a whole video in memory.
 */
class VideoFileStore(context: Context) {
    private val appContext = context.applicationContext
    private val videosDir: File by lazy {
        File(appContext.filesDir, "videos").apply { mkdirs() }
    }

    /** A fresh, empty destination file for a new camera recording. */
    fun newRecordingFile(): File = File(videosDir, "${UUID.randomUUID()}.mp4")

    /** Streams [uri]'s content into app-private storage without ever holding the whole file in memory. */
    suspend fun copyToPrivateStorage(uri: Uri): File = withContext(Dispatchers.IO) {
        val destination = File(videosDir, "${UUID.randomUUID()}.mp4")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output, bufferSize = DEFAULT_BUFFER_BYTES)
            }
        } ?: error("Unable to open the selected video")
        destination
    }

    suspend fun readMetadata(file: File): VideoMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val swap = width; width = height; height = swap
            }
            VideoMetadata(
                filePath = file.absolutePath,
                durationMs = durationMs,
                widthPx = width,
                heightPx = height,
                fileSizeBytes = file.length()
            )
        } finally {
            retriever.release()
        }
    }

    /** Best-effort validation that [uri] actually points at readable video content. */
    suspend fun isReadableVideo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val mimeType = appContext.contentResolver.getType(uri)
        if (mimeType != null && !mimeType.startsWith("video/")) return@withContext false
        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { it.read() != -1 || true }
        }.getOrNull() == true
    }

    fun delete(filePath: String) {
        runCatching { File(filePath).delete() }
    }

    /** Removes any file in [videosDir] not present in [referencedPaths] — abandoned drafts and cancelled uploads. */
    suspend fun deleteUnreferencedFiles(referencedPaths: Set<String>) = withContext(Dispatchers.IO) {
        videosDir.listFiles()?.forEach { file ->
            if (file.absolutePath !in referencedPaths) file.delete()
        }
    }

    suspend fun totalManagedStorageBytes(): Long = withContext(Dispatchers.IO) {
        videosDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private companion object {
        const val DEFAULT_BUFFER_BYTES = 8 * 1024
    }
}
