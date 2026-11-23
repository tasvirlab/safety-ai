package com.azizbek.safetyai.analysis

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azizbek.safetyai.ondevice.PoseMambaModelLoader
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoseMambaThreadSweepInstrumentedTest {
    @Test
    fun compareThreadsOnRealWindow() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val video = File(context.filesDir, "videos/2a048946-9671-4c3d-b1bf-13de9727a71d.mp4")
        require(video.isFile && video.length() > 0L) { "Benchmark video is missing" }

        val estimator = RtmPoseVideoEstimator(context)
        val pose2D = try {
            estimator.estimate(video)
        } finally {
            estimator.close()
        }
        val normalized = PoseMambaTemporalInput.normalize(pose2D.frames, pose2D.frameWidth)
        val window = PoseMambaTemporalInput.createWindows(normalized).first { it.valid }
        val manual = RebaManualInputs(
            loadKg = 0.0, suddenForce = false, coupling = 0,
            activityScore = 0, activityPending = true
        )
        val results = mutableListOf<RunResult>()

        // Last 4-thread run exposes order/temperature effects in the timings.
        for (threads in listOf(4, 1, 2, 8, 4)) {
            val session = PoseMambaModelLoader.open(context, intraOpThreads = threads)
            try {
                session.lift(window.input) // warm-up outside measurement
                val started = SystemClock.elapsedRealtimeNanos()
                val prediction = session.lift(window.input)
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
                val sequence = PoseMamba3DSequence(
                    frameCount = 243,
                    coordinates = prediction,
                    frameValid = BooleanArray(243) { true },
                    skippedWindowIndices = intArrayOf()
                )
                val scores = RebaScorer.scoreSequence(
                    RebaAngleCalculator.compute(sequence), manual
                )
                results += RunResult(threads, elapsedMs, prediction, scores)
            } finally {
                session.close()
            }
        }

        val baseline = results.first()
        val report = buildString {
            appendLine("POSEMAMBA REAL-WINDOW THREAD SWEEP")
            appendLine("input_frames=243")
            appendLine("reference_threads=4")
            results.forEachIndexed { index, run ->
                var maxAbsMeters = 0f
                for (i in run.prediction.indices) {
                    maxAbsMeters = maxOf(maxAbsMeters,
                        abs(run.prediction[i] - baseline.prediction[i]))
                }
                val exactTableC = run.scores.indices.count { frame ->
                    run.scores[frame].tableCScore == baseline.scores[frame].tableCScore
                }
                val exactFinal = run.scores.indices.count { frame ->
                    run.scores[frame].finalRebaScore == baseline.scores[frame].finalRebaScore
                }
                appendLine("run_${index}_threads=${run.threads}")
                appendLine("run_${index}_lift_ms=${"%.3f".format(java.util.Locale.US, run.millis)}")
                appendLine("run_${index}_max_3d_abs_m=$maxAbsMeters")
                appendLine("run_${index}_table_c_exact=$exactTableC/${run.scores.size}")
                appendLine("run_${index}_final_reba_exact=$exactFinal/${run.scores.size}")
            }
        }
        File(context.cacheDir, "posemamba_threads_report.txt").writeText(report)
    }

    private data class RunResult(
        val threads: Int,
        val millis: Double,
        val prediction: FloatArray,
        val scores: List<RebaFrameScore>
    )
}