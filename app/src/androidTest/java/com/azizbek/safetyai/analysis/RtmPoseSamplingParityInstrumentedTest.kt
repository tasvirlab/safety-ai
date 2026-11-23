package com.azizbek.safetyai.analysis

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RtmPoseSamplingParityInstrumentedTest {

    @Test
    fun fullSourceRateAnd15FpsAreCompared() = runBlocking {
        val instrumentation =
            InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext

        val videoFile = File(
            appContext.filesDir,
            "videos/2a048946-9671-4c3d-b1bf-13de9727a71d.mp4"
        )

        require(videoFile.isFile && videoFile.length() > 0L) {
            "Single-person benchmark video is missing"
        }
        val manualInputs = RebaManualInputs(
            loadKg = 0.0,
            suddenForce = false,
            coupling = 0,
            activityScore = 0,
            activityPending = true
        )

        val fullRate = runPipeline(
            context = appContext,
            videoFile = videoFile,
            inferenceFps = null,
            manualInputs = manualInputs
        )

        val sampled15 = runPipeline(
            context = appContext,
            videoFile = videoFile,
            inferenceFps = 15,
            manualInputs = manualInputs
        )

        val report = compareRuns(
            fullRate = fullRate,
            sampled15 = sampled15
        )

        File(
            appContext.cacheDir,
            "sampling_parity_report.txt"
        ).writeText(report)

        report.lineSequence().forEach { line ->
            Log.i(PARITY_TAG, line)
        }
    }

    private suspend fun runPipeline(
        context: Context,
        videoFile: File,
        inferenceFps: Int?,
        manualInputs: RebaManualInputs
    ): PipelineOutput {
        val estimator = RtmPoseVideoEstimator(
            context = context,
            requestedInferenceFps = inferenceFps
        )

        val lifter = PoseMambaSequenceLifter(
            OnDevicePoseMambaLifter(context)
        )

        val startedAt = SystemClock.elapsedRealtime()

        try {
            val pose2D = estimator.estimate(videoFile)

            val normalized = PoseMambaTemporalInput.normalize(
                frames = pose2D.frames,
                frameWidth = pose2D.frameWidth
            )

            val pose3D = lifter.lift(normalized)
            val angles = RebaAngleCalculator.compute(pose3D)

            val scores = RebaScorer.scoreSequence(
                angles = angles,
                manual = manualInputs
            )

            require(pose3D.frameCount == pose2D.frameCount)
            require(angles.size == pose2D.frameCount)
            require(scores.size == pose2D.frameCount)

            return PipelineOutput(
                pose3D = pose3D,
                angles = angles,
                scores = scores,
                elapsedMs =
                    SystemClock.elapsedRealtime() - startedAt
            )
        } finally {
            estimator.close()
            lifter.close()
        }
    }

    private fun compareRuns(
        fullRate: PipelineOutput,
        sampled15: PipelineOutput
    ): String {
        require(
            fullRate.pose3D.frameCount ==
                sampled15.pose3D.frameCount
        )

        val frameCount = fullRate.pose3D.frameCount
        val poseErrorsMm = mutableListOf<Double>()

        for (frame in 0 until frameCount) {
            if (
                !fullRate.pose3D.frameValid[frame] ||
                !sampled15.pose3D.frameValid[frame]
            ) {
                continue
            }

            val frameOffset = frame * JOINT_COUNT * XYZ_COUNT
            val fullRootOffset =
                frameOffset + ROOT_INDEX * XYZ_COUNT
            val sampledRootOffset =
                frameOffset + ROOT_INDEX * XYZ_COUNT

            val fullRootX =
                fullRate.pose3D.coordinates[fullRootOffset]
            val fullRootY =
                fullRate.pose3D.coordinates[fullRootOffset + 1]
            val fullRootZ =
                fullRate.pose3D.coordinates[fullRootOffset + 2]

            val sampledRootX =
                sampled15.pose3D.coordinates[sampledRootOffset]
            val sampledRootY =
                sampled15.pose3D.coordinates[sampledRootOffset + 1]
            val sampledRootZ =
                sampled15.pose3D.coordinates[sampledRootOffset + 2]

            for (joint in 0 until JOINT_COUNT) {
                if (joint == ROOT_INDEX) continue

                val offset = frameOffset + joint * XYZ_COUNT

                val fullX =
                    fullRate.pose3D.coordinates[offset] -
                        fullRootX
                val fullY =
                    fullRate.pose3D.coordinates[offset + 1] -
                        fullRootY
                val fullZ =
                    fullRate.pose3D.coordinates[offset + 2] -
                        fullRootZ

                val sampledX =
                    sampled15.pose3D.coordinates[offset] -
                        sampledRootX
                val sampledY =
                    sampled15.pose3D.coordinates[offset + 1] -
                        sampledRootY
                val sampledZ =
                    sampled15.pose3D.coordinates[offset + 2] -
                        sampledRootZ

                if (
                    fullX.isFinite() &&
                    fullY.isFinite() &&
                    fullZ.isFinite() &&
                    sampledX.isFinite() &&
                    sampledY.isFinite() &&
                    sampledZ.isFinite()
                ) {
                    val dx = (fullX - sampledX).toDouble()
                    val dy = (fullY - sampledY).toDouble()
                    val dz = (fullZ - sampledZ).toDouble()

                    poseErrorsMm +=
                        sqrt(dx * dx + dy * dy + dz * dz) *
                            1_000.0
                }
            }
        }

        require(poseErrorsMm.isNotEmpty()) {
            "No common valid 3D frames: " +
                "full_valid=${fullRate.pose3D.frameValid.count { it }}; " +
                "sampled15_valid=${sampled15.pose3D.frameValid.count { it }}; " +
                "full_skipped=${fullRate.pose3D.skippedWindowIndices.contentToString()}; " +
                "sampled15_skipped=${sampled15.pose3D.skippedWindowIndices.contentToString()}"
        }

        val angleErrors =
            linkedMapOf<String, MutableList<Double>>()

        for (frame in 0 until frameCount) {
            val fullAngles =
                angleValues(fullRate.angles[frame])
            val sampledAngles =
                angleValues(sampled15.angles[frame])

            fullAngles.forEach { (name, fullValue) ->
                val sampledValue =
                    sampledAngles[name] ?: return@forEach

                if (
                    fullValue.isFinite() &&
                    sampledValue.isFinite()
                ) {
                    angleErrors
                        .getOrPut(name) { mutableListOf() }
                        .add(abs(fullValue - sampledValue))
                }
            }
        }

        val pairedScoreIndices =
            (0 until frameCount).filter { frame ->
                fullRate.scores[frame].tableCScore != null &&
                    sampled15.scores[frame].tableCScore != null
            }

        require(pairedScoreIndices.isNotEmpty())

        val exactCount = pairedScoreIndices.count { frame ->
            fullRate.scores[frame].tableCScore ==
                sampled15.scores[frame].tableCScore
        }

        val withinOneCount =
            pairedScoreIndices.count { frame ->
                abs(
                    requireNotNull(
                        fullRate.scores[frame].tableCScore
                    ) -
                        requireNotNull(
                            sampled15.scores[frame].tableCScore
                        )
                ) <= 1
            }

        val fullPeakIndex = peakIndex(fullRate.scores)
        val sampledPeakIndex = peakIndex(sampled15.scores)

        val fullCoverage =
            fullRate.scores.count { it.valid } *
                100.0 / frameCount

        val sampledCoverage =
            sampled15.scores.count { it.valid } *
                100.0 / frameCount

        return buildString {
            appendLine("SAMPLING PARITY REPORT")
            appendLine("frames=$frameCount")
            appendLine(
                "full_runtime_ms=${fullRate.elapsedMs}"
            )
            appendLine(
                "sampled_15_runtime_ms=${sampled15.elapsedMs}"
            )
            appendLine(
                "speedup=${format(
                    fullRate.elapsedMs.toDouble() /
                        sampled15.elapsedMs.toDouble()
                )}x"
            )
            appendLine(
                "root_aligned_mpjpe_mm=" +
                    format(poseErrorsMm.average())
            )
            appendLine(
                "root_aligned_p95_mm=" +
                    format(percentile95(poseErrorsMm))
            )
            appendLine(
                "table_c_exact_percent=" +
                    format(
                        exactCount * 100.0 /
                            pairedScoreIndices.size
                    )
            )
            appendLine(
                "table_c_within_1_percent=" +
                    format(
                        withinOneCount * 100.0 /
                            pairedScoreIndices.size
                    )
            )
            appendLine(
                "paired_reba_frames=" +
                    pairedScoreIndices.size
            )
            appendLine(
                "full_peak_reba=" +
                    fullRate.scores[fullPeakIndex]
                        .finalRebaScore
            )
            appendLine(
                "sampled_15_peak_reba=" +
                    sampled15.scores[sampledPeakIndex]
                        .finalRebaScore
            )
            appendLine(
                "full_peak_timestamp_ms=" +
                    fullPeakIndex * 1_000L / 60L
            )
            appendLine(
                "sampled_15_peak_timestamp_ms=" +
                    sampledPeakIndex * 1_000L / 60L
            )
            appendLine(
                "peak_timestamp_delta_ms=" +
                    abs(
                        fullPeakIndex -
                            sampledPeakIndex
                    ) * 1_000L / 60L
            )
            appendLine(
                "full_coverage_percent=" +
                    format(fullCoverage)
            )
            appendLine(
                "sampled_15_coverage_percent=" +
                    format(sampledCoverage)
            )

            angleErrors.forEach { (name, values) ->
                appendLine(
                    "angle_mae_deg.$name=" +
                        format(values.average())
                )
                appendLine(
                    "angle_p95_deg.$name=" +
                        format(percentile95(values))
                )
            }
        }
    }

    private fun peakIndex(
        scores: List<RebaFrameScore>
    ): Int {
        val validIndices = scores.indices.filter { index ->
            scores[index].valid &&
                scores[index].finalRebaScore != null
        }

        require(validIndices.isNotEmpty())

        return validIndices.maxByOrNull { index ->
            requireNotNull(scores[index].finalRebaScore)
        } ?: error("No REBA peak")
    }

    private fun angleValues(
        angles: RebaFrameAngles
    ): Map<String, Double> = linkedMapOf(
        "trunk_sagittal" to angles.trunkSagittalDeg,
        "trunk_lateral" to angles.trunkLateralDeg,
        "trunk_twist" to angles.trunkTwistDeg,
        "neck_flex" to angles.neckFlexDeg,
        "neck_lateral" to angles.neckLateralDeg,
        "upper_arm_r_sagittal" to
            angles.upperArmRSagittalDeg,
        "upper_arm_l_sagittal" to
            angles.upperArmLSagittalDeg,
        "upper_arm_r_abduction" to
            angles.upperArmRAbductionDeg,
        "upper_arm_l_abduction" to
            angles.upperArmLAbductionDeg,
        "lower_arm_r" to angles.lowerArmRDeg,
        "lower_arm_l" to angles.lowerArmLDeg,
        "wrist_r" to angles.wristRDeg,
        "wrist_l" to angles.wristLDeg,
        "knee_r" to angles.kneeRDeg,
        "knee_l" to angles.kneeLDeg,
        "knee_max" to angles.kneeMaxDeg
    )

    private fun percentile95(
        values: List<Double>
    ): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val index =
            (0.95 * (sorted.size - 1))
                .roundToInt()
                .coerceIn(sorted.indices)

        return sorted[index]
    }

    private fun format(
        value: Double
    ): String = "%.4f".format(
        java.util.Locale.US,
        value
    )

    private data class PipelineOutput(
        val pose3D: PoseMamba3DSequence,
        val angles: List<RebaFrameAngles>,
        val scores: List<RebaFrameScore>,
        val elapsedMs: Long
    )

    private companion object {
        const val PARITY_TAG = "SafetyAI.Parity"
        const val JOINT_COUNT = 21
        const val XYZ_COUNT = 3
        const val ROOT_INDEX = 3
    }
}