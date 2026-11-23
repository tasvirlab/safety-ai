package com.azizbek.safetyai.analysis

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil

data class VideoStreamInfo(
    val durationUs: Long,
    /** Normalized supported rate: exactly 30 or 60. */
    val sourceFps: Int,
    /** Raw value reported or inferred from the video. */
    val reportedFps: Double,
    val sourceFrameCount: Int,
    val widthPx: Int,
    val heightPx: Int,
    val rotationDegrees: Int
)

data class DecodedSourceFrame(
    val sourceIndex: Int,
    val timestampUs: Long,
    /**
     * Null means decoding failed or this frame was deliberately skipped.
     * The bitmap is recycled immediately after [VideoFrameDecoder.decode]
     * returns from the callback and must not be retained.
     */
    val bitmap: Bitmap?
)

class VideoFrameDecoder {
    suspend fun inspect(file: File): VideoStreamInfo =
        withContext(Dispatchers.IO) {
            require(file.isFile && file.length() > 0L) {
                "Video file is missing or empty"
            }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                readStreamInfo(retriever)
            } finally {
                retriever.release()
            }
        }

    /**
     * Decodes original source frames exactly once.
     *
     * 30-to-60 Hz temporal resampling is deliberately performed after
     * RTMPose, avoiding duplicate inference on the same video frame.
     */
    suspend fun decode(
        file: File,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null,
        shouldDecodeFrame: (sourceIndex: Int, total: Int) -> Boolean = { _, _ -> true },
        onFrame: suspend (DecodedSourceFrame) -> Unit
    ): VideoStreamInfo = withContext(Dispatchers.Default) {
        require(file.isFile && file.length() > 0L) {
            "Video file is missing or empty"
        }

        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(file.absolutePath)
            val info = readStreamInfo(retriever)

            for (sourceIndex in 0 until info.sourceFrameCount) {
                currentCoroutineContext().ensureActive()

                val timestampUs = minOf(
                    sourceIndex * 1_000_000L / info.sourceFps,
                    info.durationUs - 1L
                )

                var bitmap: Bitmap? =
                    if (!shouldDecodeFrame(sourceIndex, info.sourceFrameCount)) {
                        null
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        retriever.getFrameAtIndex(sourceIndex)
                            ?: retriever.getFrameAtTime(
                                timestampUs,
                                MediaMetadataRetriever.OPTION_CLOSEST
                            )
                    } else {
                        retriever.getFrameAtTime(
                            timestampUs,
                            MediaMetadataRetriever.OPTION_CLOSEST
                        )
                    }

                try {
                    onFrame(
                        DecodedSourceFrame(
                            sourceIndex = sourceIndex,
                            timestampUs = timestampUs,
                            bitmap = bitmap
                        )
                    )
                } finally {
                    bitmap?.recycle()
                }

                onProgress?.invoke(
                    sourceIndex + 1,
                    info.sourceFrameCount
                )
            }

            info
        } finally {
            retriever.release()
        }
    }

    private fun readStreamInfo(
        retriever: MediaMetadataRetriever
    ): VideoStreamInfo {
        val durationMs = retriever.metadataLong(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        ) ?: error("Video duration metadata is missing")

        require(durationMs > 0L) {
            "Video duration must be positive"
        }

        val durationUs = durationMs * 1_000L

        val encodedWidth = retriever.metadataInt(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
        ) ?: error("Video width metadata is missing")

        val encodedHeight = retriever.metadataInt(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
        ) ?: error("Video height metadata is missing")

        require(encodedWidth > 0 && encodedHeight > 0) {
            "Video dimensions must be positive"
        }

        val rotation = (
            retriever.metadataInt(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            ) ?: 0
        ).normalizedRotation()

        val metadataFrameCount =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                retriever.metadataInt(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT
                )
            } else {
                null
            }

        val metadataFps = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
        )?.toDoubleOrNull()

        val inferredFps =
            if (
                metadataFps == null &&
                metadataFrameCount != null &&
                metadataFrameCount > 0
            ) {
                metadataFrameCount * 1_000_000.0 / durationUs
            } else {
                null
            }

        val reportedFps =
            metadataFps ?: inferredFps
            ?: error("Video FPS metadata is missing")

        val normalizedFps = when {
            abs(reportedFps - 30.0) <= 1.0 -> 30
            abs(reportedFps - 60.0) <= 1.5 -> 60
            else -> error(
                "Unsupported video FPS: $reportedFps. " +
                    "Only 30 or 60 FPS video is supported."
            )
        }

        val frameCount =
            metadataFrameCount?.takeIf { it > 0 }
                ?: ceil(
                    durationUs * normalizedFps / 1_000_000.0
                ).toInt()

        require(frameCount > 0) {
            "Video contains no decodable frames"
        }

        val orientedWidth =
            if (rotation == 90 || rotation == 270) {
                encodedHeight
            } else {
                encodedWidth
            }

        val orientedHeight =
            if (rotation == 90 || rotation == 270) {
                encodedWidth
            } else {
                encodedHeight
            }

        return VideoStreamInfo(
            durationUs = durationUs,
            sourceFps = normalizedFps,
            reportedFps = reportedFps,
            sourceFrameCount = frameCount,
            widthPx = orientedWidth,
            heightPx = orientedHeight,
            rotationDegrees = rotation
        )
    }

    private fun rotate(
        source: Bitmap,
        rotationDegrees: Int
    ): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }

        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true
        )

        if (rotated !== source) {
            source.recycle()
        }

        return rotated
    }

    private fun Int.normalizedRotation(): Int {
        val normalized = ((this % 360) + 360) % 360
        require(normalized in setOf(0, 90, 180, 270)) {
            "Unsupported video rotation: $this"
        }
        return normalized
    }

    private fun MediaMetadataRetriever.metadataInt(
        key: Int
    ): Int? = extractMetadata(key)?.toIntOrNull()

    private fun MediaMetadataRetriever.metadataLong(
        key: Int
    ): Long? = extractMetadata(key)?.toLongOrNull()
}