package com.azizbek.safetyai.analysis

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideoFrameDecoderInstrumentedTest {

    @Test
    fun decoderPreservesFrameRateCountAndOrientation() = runBlocking {
        val instrumentation =
            InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testAssets = instrumentation.context.assets

        fun copyAsset(assetPath: String): File {
            val destination = File(
                targetContext.cacheDir,
                assetPath.substringAfterLast("/")
            )

            testAssets.open(assetPath).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            assertTrue(destination.isFile)
            assertTrue(destination.length() > 0L)
            return destination
        }

        val video30 = copyAsset(
            "video/decoder_30_rotated.mp4"
        )
        val video60 = copyAsset(
            "video/decoder_60.mp4"
        )

        val decoder = VideoFrameDecoder()

        try {
            val info30 = decoder.inspect(video30)

            assertEquals(30, info30.sourceFps)
            assertEquals(30, info30.sourceFrameCount)
            assertTrue(
                "Expected quarter-turn rotation, got ${info30.rotationDegrees}",
                info30.rotationDegrees == 90 ||
                    info30.rotationDegrees == 270
            )
            assertEquals(180, info30.widthPx)
            assertEquals(320, info30.heightPx)
            assertTrue(
                "Unexpected reported 30 FPS: ${info30.reportedFps}",
                info30.reportedFps in 29.0..31.0
            )

            var decoded30 = 0
            var previousTimestamp30 = -1L
            var progress30 = 0 to 0

            val decodedInfo30 = decoder.decode(
                file = video30,
                onProgress = { completed, total ->
                    progress30 = completed to total
                }
            ) { frame ->
                assertEquals(decoded30, frame.sourceIndex)
                assertTrue(
                    "30 FPS timestamps are not increasing",
                    frame.timestampUs > previousTimestamp30
                )

                val bitmap = frame.bitmap
                assertNotNull(
                    "30 FPS frame ${frame.sourceIndex} was not decoded",
                    bitmap
                )

                bitmap!!
                assertEquals(
                    "Rotated frame width differs",
                    180,
                    bitmap.width
                )
                assertEquals(
                    "Rotated frame height differs",
                    320,
                    bitmap.height
                )

                previousTimestamp30 = frame.timestampUs
                decoded30++
            }

            assertEquals(info30, decodedInfo30)
            assertEquals(30, decoded30)
            assertEquals(30 to 30, progress30)

            val info60 = decoder.inspect(video60)

            assertEquals(60, info60.sourceFps)
            assertEquals(60, info60.sourceFrameCount)
            assertEquals(0, info60.rotationDegrees)
            assertEquals(320, info60.widthPx)
            assertEquals(180, info60.heightPx)
            assertTrue(
                "Unexpected reported 60 FPS: ${info60.reportedFps}",
                info60.reportedFps in 58.5..61.5
            )

            var decoded60 = 0
            var previousTimestamp60 = -1L
            var progress60 = 0 to 0

            val decodedInfo60 = decoder.decode(
                file = video60,
                onProgress = { completed, total ->
                    progress60 = completed to total
                }
            ) { frame ->
                assertEquals(decoded60, frame.sourceIndex)
                assertTrue(
                    "60 FPS timestamps are not increasing",
                    frame.timestampUs > previousTimestamp60
                )

                val bitmap = frame.bitmap
                assertNotNull(
                    "60 FPS frame ${frame.sourceIndex} was not decoded",
                    bitmap
                )

                bitmap!!
                assertEquals(320, bitmap.width)
                assertEquals(180, bitmap.height)

                previousTimestamp60 = frame.timestampUs
                decoded60++
            }

            assertEquals(info60, decodedInfo60)
            assertEquals(60, decoded60)
            assertEquals(60 to 60, progress60)
        } finally {
            video30.delete()
            video60.delete()
        }
    }
}