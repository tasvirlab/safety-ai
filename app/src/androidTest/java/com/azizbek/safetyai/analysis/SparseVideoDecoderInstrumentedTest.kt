package com.azizbek.safetyai.analysis

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SparseVideoDecoderInstrumentedTest {
    @Test
    fun selectedFramesMatchFullDecodeAndMeasureTime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "videos/2a048946-9671-4c3d-b1bf-13de9727a71d.mp4")
        require(file.isFile && file.length() > 0L) {
            "The existing single-person benchmark video is missing"
        }
        val decoder = VideoFrameDecoder()
        val info = decoder.inspect(file)
        require(info.sourceFps == 30 || info.sourceFps == 60)
        val stride = info.sourceFps / 15
        fun selected(index: Int, total: Int): Boolean =
            index % stride == 0 || index == total - 1

        suspend fun run(sparse: Boolean): TimedDecode {
            val signatures = linkedMapOf<Int, String>()
            var decodedCount = 0
            var hashNs = 0L
            val startedNs = SystemClock.elapsedRealtimeNanos()
            val decodedInfo = decoder.decode(
                file = file,
                shouldDecodeFrame = { index, total ->
                    !sparse || selected(index, total)
                }
            ) { frame ->
                if (frame.bitmap != null) decodedCount++
                if (selected(frame.sourceIndex, info.sourceFrameCount)) {
                    check(frame.bitmap != null) {
                        "Selected frame ${frame.sourceIndex} failed to decode"
                    }
                    val hashStartedNs = SystemClock.elapsedRealtimeNanos()
                    signatures[frame.sourceIndex] = signature(frame.bitmap)
                    hashNs += SystemClock.elapsedRealtimeNanos() - hashStartedNs
                } else if (sparse) {
                    check(frame.bitmap == null) { "Unselected frame was decoded" }
                }
            }
            check(decodedInfo.sourceFrameCount == info.sourceFrameCount)
            return TimedDecode(
                millis = (SystemClock.elapsedRealtimeNanos() - startedNs - hashNs) / 1_000_000L,
                decodedCount = decodedCount,
                signatures = signatures
            )
        }

        val full = run(sparse = false)
        val sparse = run(sparse = true)
        check(full.signatures == sparse.signatures) {
            "Pixel-level SHA-256 mismatch in sampled frames"
        }
        val report = buildString {
            appendLine("SPARSE VIDEO DECODER PARITY")
            appendLine("source_frames=${info.sourceFrameCount}")
            appendLine("selected_frames=${sparse.signatures.size}")
            appendLine("full_decoded_bitmaps=${full.decodedCount}")
            appendLine("sparse_decoded_bitmaps=${sparse.decodedCount}")
            appendLine("all_selected_pixels_identical=true")
            appendLine("full_decode_ms=${full.millis}")
            appendLine("sparse_decode_ms=${sparse.millis}")
            appendLine("decode_speedup=${"%.3f".format(java.util.Locale.US, full.millis.toDouble() / sparse.millis)}x")
        }
        File(context.cacheDir, "sparse_decoder_report.txt").writeText(report)
    }

    private fun signature(bitmap: Bitmap): String {
        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.flip()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(buffer)
        val hex = digest.digest()
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "${bitmap.width}x${bitmap.height}:${bitmap.config}:$hex"
    }

    private data class TimedDecode(
        val millis: Long,
        val decodedCount: Int,
        val signatures: Map<Int, String>
    )
}