package com.azizbek.safetyai.analysis

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RtmPoseProductionParallelParityInstrumentedTest {
    @Test
    fun productionParallelMatchesSequential() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val video = File(
            context.filesDir,
            "videos/2a048946-9671-4c3d-b1bf-13de9727a71d.mp4"
        )
        require(video.isFile && video.length() > 0L) {
            "Benchmark video is missing"
        }

        val sequential = RtmPoseVideoEstimator(
            context,
            useParallelPose = false
        )
        val parallel = RtmPoseVideoEstimator(
            context,
            useParallelPose = true
        )

        try {
            sequential.estimate(video)
            parallel.estimate(video)

            val sequentialStart = SystemClock.elapsedRealtimeNanos()
            val reference = sequential.estimate(video)
            val sequentialMs =
                (SystemClock.elapsedRealtimeNanos() - sequentialStart) / 1_000_000.0

            val parallelStart = SystemClock.elapsedRealtimeNanos()
            val candidate = parallel.estimate(video)
            val parallelMs =
                (SystemClock.elapsedRealtimeNanos() - parallelStart) / 1_000_000.0

            var coordinateDifferences = 0
            var confidenceDifferences = 0
            var validityDifferences = 0

            require(reference.frameCount == candidate.frameCount) {
                "Frame counts differ"
            }
            require(reference.sourceFps == candidate.sourceFps) {
                "Source frame rates differ"
            }
            require(reference.frameWidth == candidate.frameWidth) {
                "Frame widths differ"
            }

            reference.frames.zip(candidate.frames).forEach { (left, right) ->
                coordinateDifferences += left.coordinates.indices.count { index ->
                    left.coordinates[index].toRawBits() !=
                        right.coordinates[index].toRawBits()
                }
                confidenceDifferences += left.confidence.indices.count { index ->
                    left.confidence[index].toRawBits() !=
                        right.confidence[index].toRawBits()
                }
                validityDifferences += left.valid.indices.count { index ->
                    left.valid[index] != right.valid[index]
                }
            }

            val countsEqual =
                reference.decodeFailureCount == candidate.decodeFailureCount &&
                    reference.noPersonFrameCount == candidate.noPersonFrameCount

            val passed =
                coordinateDifferences == 0 &&
                    confidenceDifferences == 0 &&
                    validityDifferences == 0 &&
                    countsEqual

            val report = buildString {
                appendLine("RTMPOSE PRODUCTION PARALLEL PARITY")
                appendLine("source_fps=${reference.sourceFps}")
                appendLine("output_frames=${reference.frameCount}")
                appendLine("sequential_warm_ms=$sequentialMs")
                appendLine("parallel_warm_ms=$parallelMs")
                appendLine("speedup=${sequentialMs / parallelMs}")
                appendLine("coordinate_differences=$coordinateDifferences")
                appendLine("confidence_differences=$confidenceDifferences")
                appendLine("validity_differences=$validityDifferences")
                appendLine("decode_failures_equal=${reference.decodeFailureCount == candidate.decodeFailureCount}")
                appendLine("no_person_counts_equal=${reference.noPersonFrameCount == candidate.noPersonFrameCount}")
                appendLine("parity_passed=$passed")
            }

            File(context.cacheDir, "rtmpose_production_parallel_parity.txt")
                .writeText(report)

            require(passed) { report }
        } finally {
            parallel.close()
            sequential.close()
        }
    }
}