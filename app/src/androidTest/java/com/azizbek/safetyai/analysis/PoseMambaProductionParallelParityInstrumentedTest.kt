package com.azizbek.safetyai.analysis

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoseMambaProductionParallelParityInstrumentedTest {
    @Test
    fun productionWindowAssemblyMatchesSequential() = runBlocking {
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

        val sequential = PoseMambaSequenceLifter(
            OnDevicePoseMambaLifter(context)
        )
        val parallel = PoseMambaSequenceLifter(
            OnDevicePoseMambaLifter(context),
            OnDevicePoseMambaLifter(context)
        )

        try {
            suspend fun measured(
                name: String,
                lifter: PoseMambaSequenceLifter
            ): Run {
                val started = SystemClock.elapsedRealtimeNanos()
                val result = lifter.lift(normalized)
                return Run(
                    name,
                    (SystemClock.elapsedRealtimeNanos() - started) /
                        1_000_000.0,
                    result
                )
            }

            val runs = listOf(
                measured("sequential_cold", sequential),
                measured("parallel_cold", parallel),
                measured("parallel_warm", parallel),
                measured("sequential_warm", sequential)
            )

            val reference = runs.first().result
            val manual = RebaManualInputs(
                loadKg = 0.0,
                suddenForce = false,
                coupling = 0,
                activityScore = 0,
                activityPending = true
            )
            val referenceScores = RebaScorer.scoreSequence(
                RebaAngleCalculator.compute(reference),
                manual
            )

            var parityPassed = true
            val report = buildString {
                appendLine("PRODUCTION POSEMAMBA PARALLEL PARITY")
                appendLine("frames=${reference.frameCount}")
                appendLine("windows=${PoseMambaTemporalInput.createWindows(normalized).size}")

                for (run in runs) {
                    val result = run.result
                    require(result.frameCount == reference.frameCount)
                    var maxDifference = 0f
                    var equalValidity = true

                    for (frame in reference.frameValid.indices) {
                        if (reference.frameValid[frame] != result.frameValid[frame]) {
                            equalValidity = false
                        }
                    }

                    for (i in reference.coordinates.indices) {
                        val a = reference.coordinates[i]
                        val b = result.coordinates[i]
                        if (a.isNaN() && b.isNaN()) continue
                        if (!a.isFinite() || !b.isFinite()) {
                            equalValidity = false
                            continue
                        }
                        maxDifference = maxOf(maxDifference, abs(a - b))
                    }

                    val equalSkipped = reference.skippedWindowIndices
                        .contentEquals(result.skippedWindowIndices)
                    val scores = RebaScorer.scoreSequence(
                        RebaAngleCalculator.compute(result),
                        manual
                    )
                    val exactTableC = scores.indices.count {
                        scores[it].tableCScore ==
                            referenceScores[it].tableCScore
                    }
                    val exactFinal = scores.indices.count {
                        scores[it].finalRebaScore ==
                            referenceScores[it].finalRebaScore
                    }

                    appendLine(
                        "${run.name}_ms=${"%.3f".format(
                            java.util.Locale.US,
                            run.millis
                        )}"
                    )
                    appendLine("${run.name}_max_3d_abs_m=$maxDifference")
                    appendLine("${run.name}_validity_equal=$equalValidity")
                    appendLine("${run.name}_skipped_equal=$equalSkipped")
                    appendLine(
                        "${run.name}_table_c_exact=$exactTableC/${scores.size}"
                    )
                    appendLine(
                        "${run.name}_final_reba_exact=$exactFinal/${scores.size}"
                    )

                    if (
                        maxDifference != 0f ||
                        !equalValidity ||
                        !equalSkipped ||
                        exactTableC != scores.size ||
                        exactFinal != scores.size
                    ) {
                        parityPassed = false
                    }
                }
                appendLine("parity_passed=$parityPassed")
            }

            File(
                context.cacheDir,
                "posemamba_production_parallel_report.txt"
            ).writeText(report)

            require(parityPassed) {
                "Production parity failed; inspect the report"
            }
        } finally {
            sequential.close()
            parallel.close()
        }
    }

    private data class Run(
        val name: String,
        val millis: Double,
        val result: PoseMamba3DSequence
    )
}