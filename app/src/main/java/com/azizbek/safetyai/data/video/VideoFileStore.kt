package com.azizbek.safetyai.data.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.azizbek.safetyai.domain.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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

    /**
     * Streams [uri]'s content into app-private storage in fixed-size chunks.
     * Content is written to a temporary file and only renamed into place once
     * copying completes; failure or cancellation leaves no partial file behind.
     */
    suspend fun copyToPrivateStorage(uri: Uri): File = withContext(Dispatchers.IO) {
        val destination = File(videosDir, "${UUID.randomUUID()}.mp4")
        val temporary = File(videosDir, destination.name + TEMPORARY_SUFFIX)
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open the selected video")
            input.use {
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
                    while (true) {
                        ensureActive()
                        val read = it.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (temporary.length() == 0L) throw IOException("The selected video is empty")
            if (!temporary.renameTo(destination)) throw IOException("Could not store the selected video")
            destination
        } catch (error: Throwable) {
            temporary.delete()
            destination.delete()
            throw error
        }
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

    /**
     * True only when [uri] opens and holds a decodable video track with
     * positive duration and dimensions. A missing or wrong MIME type alone does
     * not reject the content; only an explicit non-video type does.
     */
    suspend fun isReadableVideo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val mimeType = appContext.contentResolver.getType(uri)
        if (mimeType != null && mimeType != "application/octet-stream" && !mimeType.startsWith("video/")) {
            return@withContext false
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" &&
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L &&
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0) > 0 &&
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0) > 0
        } catch (_: RuntimeException) {
            false
        } finally {
            retriever.release()
        }
    }

    /** Deletes [filePath] only if it lives directly inside the managed videos directory. */
    fun delete(filePath: String): Boolean = runCatching {
        val file = File(filePath).canonicalFile
        file.parentFile == videosDir.canonicalFile && (!file.exists() || file.delete())
    }.getOrDefault(false)

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
        const val TEMPORARY_SUFFIX = ".part"
    }
}
