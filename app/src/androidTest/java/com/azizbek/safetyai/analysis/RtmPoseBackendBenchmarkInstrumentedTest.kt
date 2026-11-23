package com.azizbek.safetyai.analysis

import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RtmPoseBackendBenchmarkInstrumentedTest {
    @Test
    fun compareCpuAndXnnpackOnSamePoseTensor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val fixture = File(appContext.cacheDir, "xnnpack_benchmark_video.mp4")
        instrumentation.context.assets.open("video/real_lifting_5s.mp4").use { input ->
            fixture.outputStream().use { output -> input.copyTo(output) }
        }
        try {
            val retriever = MediaMetadataRetriever()
            val bitmap = try {
                retriever.setDataSource(fixture.absolutePath)
                retriever.getFrameAtTime(
                    2_000_000L, MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: error("Could not decode fixture frame")
            } finally {
                retriever.release()
            }
            try {
                val detectorInput = RtmPoseImagePreprocessor.createDetectorInput(bitmap)
                val box = RtmPoseOnDeviceSession(appContext).use { baseline ->
                    val output = baseline.runDetector(detectorInput.tensor)
                    RtmPosePostprocessor.decodeDetector(
                        output.detections, output.labels, detectorInput.resizeRatio
                    ).minByOrNull { candidate ->
                        abs((candidate.left + candidate.right) * 0.5f - bitmap.width * 0.5f) +
                            abs((candidate.top + candidate.bottom) * 0.5f - bitmap.height * 0.5f)
                    } ?: error("No person was detected in the test frame")
                }
                val poseInput = RtmPoseImagePreprocessor.createPoseInput(bitmap, box)
                // Session initialization and warm-up are excluded from the timed loop.
                val cpu = measure(appContext, false, poseInput)
                val xnn1 = measure(appContext, true, poseInput, 1)
                val xnn = measure(appContext, true, poseInput, 2)
                val xnn4 = measure(appContext, true, poseInput, 4)
                val cpuPose = RtmPosePostprocessor.decodePose(
                    cpu.output.simccX, cpu.output.simccY, box
                )
                val xnnPose = RtmPosePostprocessor.decodePose(
                    xnn.output.simccX, xnn.output.simccY, box
                )
                val xnn1Pose = RtmPosePostprocessor.decodePose(
                    xnn1.output.simccX, xnn1.output.simccY, box
                )
                val xnn4Pose = RtmPosePostprocessor.decodePose(
                    xnn4.output.simccX, xnn4.output.simccY, box
                )
                val report = buildString {
                    appendLine("CPU median_ms=${cpu.medianNs / 1_000_000.0}")
                    appendLine("XNNPACK_1 median_ms=${xnn1.medianNs / 1_000_000.0}")
                    appendLine("XNNPACK_4 median_ms=${xnn4.medianNs / 1_000_000.0}")
                    appendLine("XNNPACK_1 joint_xy_max_abs_px=${maxDifference(cpuPose.coordinates, xnn1Pose.coordinates)}")
                    appendLine("XNNPACK_4 joint_xy_max_abs_px=${maxDifference(cpuPose.coordinates, xnn4Pose.coordinates)}")
                    appendLine("XNNPACK_1 joint_score_max_abs=${maxDifference(cpuPose.scores, xnn1Pose.scores)}")
                    appendLine("XNNPACK_4 joint_score_max_abs=${maxDifference(cpuPose.scores, xnn4Pose.scores)}")
                    appendLine("XNNPACK median_ms=${xnn.medianNs / 1_000_000.0}")
                    appendLine("speedup=${cpu.medianNs.toDouble() / xnn.medianNs}")
                    appendLine("simcc_x_max_abs=${maxDifference(cpu.output.simccX, xnn.output.simccX)}")
                    appendLine("simcc_y_max_abs=${maxDifference(cpu.output.simccY, xnn.output.simccY)}")
                    appendLine("joint_xy_max_abs_px=${maxDifference(cpuPose.coordinates, xnnPose.coordinates)}")
                    appendLine("joint_score_max_abs=${maxDifference(cpuPose.scores, xnnPose.scores)}")
                }
                File(appContext.cacheDir, "rtmpose_xnnpack_sweep_report.txt").writeText(report)
                Log.i("SafetyAI.BackendBench", report.replace('\n', ';'))
                assertTrue(cpu.medianNs > 0L && xnn.medianNs > 0L)
            } finally {
                bitmap.recycle()
            }
        } finally {
            fixture.delete()
        }
    }

    private data class Measurement(
        val medianNs: Long,
        val output: RtmPoseSimccOutput
    )

    private fun measure(
        context: android.content.Context,
        useXnnpack: Boolean,
        input: FloatArray,
        xnnpackThreads: Int = 2
    ): Measurement = RtmPoseOnDeviceSession(
        context, useXnnpack, xnnpackThreads
    ).use { session ->
        repeat(3) { session.runPose(input) }
        val times = LongArray(15)
        var last: RtmPoseSimccOutput? = null
        repeat(times.size) { index ->
            val start = System.nanoTime()
            last = session.runPose(input)
            times[index] = System.nanoTime() - start
        }
        Measurement(times.sorted()[times.size / 2], requireNotNull(last))
    }

    private fun maxDifference(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        var result = 0f
        for (index in a.indices) {
            require(a[index].isFinite() && b[index].isFinite())
            result = max(result, abs(a[index] - b[index]))
        }
        return result
    }
}