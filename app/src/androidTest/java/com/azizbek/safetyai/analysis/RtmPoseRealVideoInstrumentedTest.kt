package com.azizbek.safetyai.analysis

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RtmPoseRealVideoInstrumentedTest {

    @Test
    fun realWorkerVideoProducesCanonicalPose() = runBlocking {
        val instrumentation =
            InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext

        val videoFile = File(
            targetContext.cacheDir,
            "real_lifting_1s.mp4"
        )

        instrumentation.context.assets
            .open("video/real_lifting_1s.mp4")
            .use { input ->
                videoFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

        var progressCompleted = 0
        var progressTotal = 0

        try {
            val sequence = RtmPoseVideoEstimator(
                targetContext
            ).use { estimator ->
                estimator.estimate(
                    videoFile = videoFile,
                    onProgress = { completed, total ->
                        progressCompleted = completed
                        progressTotal = total
                    }
                )
            }

            assertEquals(60, sequence.sourceFps)
            assertEquals(60, sequence.targetFps)
            assertTrue(
                "Expected 59-60 decoded frames, got ${sequence.sourceFrameCount}",
                sequence.sourceFrameCount in 59..60
            )
            assertEquals(
                sequence.sourceFrameCount,
                sequence.frameCount
            )
            assertEquals(640, sequence.frameWidth)
            assertEquals(360, sequence.frameHeight)
            assertEquals(0, sequence.decodeFailureCount)

            assertEquals(
                sequence.sourceFrameCount,
                progressCompleted
            )
            assertEquals(
                sequence.sourceFrameCount,
                progressTotal
            )

            for (index in 0 until sequence.frameCount) {
                assertEquals(
                    index,
                    sequence.sourceFrameIndices[index]
                )
                assertEquals(
                    index * 1_000_000L / 60L,
                    sequence.timestampsUs[index]
                )
            }

            val detectedFrames = sequence.frames.filter { pose ->
                pose.valid.any { it }
            }

            assertTrue(
                "RTMPose found no person in the real lifting video",
                detectedFrames.isNotEmpty()
            )

            assertTrue(
                "All frames were marked as no-person",
                sequence.noPersonFrameCount <
                    sequence.sourceFrameCount
            )

            for (pose in detectedFrames) {
                assertEquals(42, pose.coordinates.size)
                assertEquals(21, pose.valid.size)
                assertEquals(21, pose.confidence.size)

                for (joint in 0 until 21) {
                    if (pose.valid[joint]) {
                        assertTrue(
                            pose.coordinates[joint * 2].isFinite()
                        )
                        assertTrue(
                            pose.coordinates[joint * 2 + 1].isFinite()
                        )
                        assertTrue(
                            pose.confidence[joint].isFinite()
                        )
                    }
                }
            }

            assertTrue(
                "PELCEN was not mapped",
                detectedFrames.any {
                    it.valid[RtmPoseCanonical21.ROOT_INDEX]
                }
            )
        } finally {
            videoFile.delete()
        }
    }
}