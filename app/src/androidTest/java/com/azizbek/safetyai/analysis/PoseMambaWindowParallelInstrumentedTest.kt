package com.azizbek.safetyai.analysis

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azizbek.safetyai.ondevice.PoseMambaModelLoader
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoseMambaWindowParallelInstrumentedTest {
    @Test
    fun compareSequentialAndTwoParallelWindows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val video = File(
            context.filesDir,
            "videos/2a048946-9671-4c3d-b1bf-13de9727a71d.mp4"
        )
        require(video.isFile && video.length() > 0L) {
            "Previous benchmark video is missing"
        }

        val estimator = RtmPoseVideoEstimator(context)
        val pose2D = try {
            estimator.estimate(video)
        } finally {
            estimator.close()
        }

        val normalized = PoseMambaTemporalInput.normalize(
            pose2D.frames,
            pose2D.frameWidth
        )
        val windows = PoseMambaTemporalInput.createWindows(normalized)
            .filter { it.valid }
            .take(2)
        require(windows.size == 2) {
            "Two valid 243-frame windows are required"
        }

        val first = PoseMambaModelLoader.open(context, intraOpThreads = 4)
        val second = try {
            PoseMambaModelLoader.open(context, intraOpThreads = 4)
        } catch (error: Throwable) {
            first.close()
            throw error
        }
        val executor = Executors.newFixedThreadPool(2)

        try {
            first.lift(windows[0].input)
            second.lift(windows[1].input)

            fun timed(label: String, action: () -> List<FloatArray>): TimedRun {
                val started = SystemClock.elapsedRealtimeNanos()
                val output = action()
                val elapsedMs =
                    (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
                return TimedRun(label, elapsedMs, output)
            }

            fun sequential(): List<FloatArray> = listOf(
                first.lift(windows[0].input),
                first.lift(windows[1].input)
            )

            fun parallel(): List<FloatArray> {
                val a = executor.submit(Callable {
                    first.lift(windows[0].input)
                })
                val b = executor.submit(Callable {
                    second.lift(windows[1].input)
                })
                return listOf(a.get(), b.get())
            }

            // Qarama-qarshi tartib qizish ta'sirini ko'rishga yordam beradi.
            val runs = listOf(
                timed("sequential_0", ::sequential),
                timed("parallel_0", ::parallel),
                timed("parallel_1", ::parallel),
                timed("sequential_1", ::sequential)
            )

            val reference = runs.first().outputs
            var overallMaxDifference = 0f
            val report = buildString {
                appendLine("POSEMAMBA TWO-WINDOW PARALLEL EXPERIMENT")
                appendLine("windows=2")
                appendLine("frames_per_window=243")
                appendLine("threads_per_session=4")
                appendLine("session_initialization_excluded=true")
                appendLine("warmup_excluded=true")

                for (run in runs) {
                    var maxDifference = 0f
                    require(run.outputs.size == reference.size)

                    for (windowIndex in reference.indices) {
                        val expected = reference[windowIndex]
                        val actual = run.outputs[windowIndex]
                        require(actual.size == expected.size)

                        for (i in expected.indices) {
                            maxDifference = maxOf(
                                maxDifference,
                                abs(actual[i] - expected[i])
                            )
                        }
                    }

                    overallMaxDifference = maxOf(
                        overallMaxDifference,
                        maxDifference
                    )
                    appendLine("${run.label}_ms=${"%.3f".format(
                        java.util.Locale.US,
                        run.millis
                    )}")
                    appendLine("${run.label}_max_3d_abs_m=$maxDifference")
                }
                appendLine("all_3d_outputs_exact=${overallMaxDifference == 0f}")
            }

            File(
                context.cacheDir,
                "posemamba_parallel_windows_report.txt"
            ).writeText(report)

            require(overallMaxDifference == 0f) {
                "Parallel output differs; inspect posemamba_parallel_windows_report.txt"
            }
        } finally {
            executor.shutdown()
            first.close()
            second.close()
        }
    }

    private data class TimedRun(
        val label: String,
        val millis: Double,
        val outputs: List<FloatArray>
    )
}