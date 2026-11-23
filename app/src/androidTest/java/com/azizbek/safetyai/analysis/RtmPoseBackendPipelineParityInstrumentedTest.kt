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
class RtmPoseBackendPipelineParityInstrumentedTest {

    @Test
    fun cpu4AndXnnpack4At15FpsAreCompared() = runBlocking {
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

        val cpu = runPipeline(
            context = appContext,
            videoFile = videoFile,
            inferenceFps = 15,
            useXnnpack = false,
            manualInputs = manualInputs
        )

        val xnn4 = runPipeline(
            context = appContext,
            videoFile = videoFile,
            inferenceFps = 15,
            useXnnpack = true,
            manualInputs = manualInputs
        )

        val report = compareRuns(
            cpu = cpu,
            xnn4 = xnn4
        )

        File(
            appContext.cacheDir,
            "xnnpack_full_pipeline_parity_report.txt"
        ).writeText(report)

        report.lineSequence().forEach { line ->
            Log.i(PARITY_TAG, line)
        }
    }

    private suspend fun runPipeline(
        context: Context,
        videoFile: File,
        inferenceFps: Int?,
        useXnnpack: Boolean,
        manualInputs: RebaManualInputs
    ): PipelineOutput {
        val estimator = RtmPoseVideoEstimator(
            context = context,
            requestedInferenceFps = inferenceFps,
            useXnnpack = useXnnpack
        )

        val lifter = PoseMambaSequenceLifter(
            OnDevicePoseMambaLifter(context)
        )

        val startedAt = SystemClock.elapsedRealtime()

        try {
            val pose2DStartedAt = SystemClock.elapsedRealtime()
            val pose2D = estimator.estimate(videoFile)
            val pose2DElapsedMs = SystemClock.elapsedRealtime() - pose2DStartedAt

            val normalized = PoseMambaTemporalInput.normalize(
                frames = pose2D.frames,
                frameWidth = pose2D.frameWidth
            )

            val pose3DStartedAt = SystemClock.elapsedRealtime()
            val pose3D = lifter.lift(normalized)
            val pose3DElapsedMs = SystemClock.elapsedRealtime() - pose3DStartedAt
            val angles = RebaAngleCalculator.compute(pose3D)

            val scores = RebaScorer.scoreSequence(
                angles = angles,
                manual = manualInputs
            )

            require(pose3D.frameCount == pose2D.frameCount)
            require(angles.size == pose2D.frameCount)
            require(scores.size == pose2D.frameCount)

            return PipelineOutput(
                pose2D = pose2D,
                pose2DElapsedMs = pose2DElapsedMs,
                pose3DElapsedMs = pose3DElapsedMs,
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
        cpu: PipelineOutput,
        xnn4: PipelineOutput
    ): String {
        require(
            cpu.pose3D.frameCount ==
                xnn4.pose3D.frameCount
        )

        val frameCount = cpu.pose3D.frameCount
        require(cpu.pose2D.frameCount == frameCount && xnn4.pose2D.frameCount == frameCount)
        var max2DAbsPx = 0f
        var validityDisagreements = 0
        var comparableCoordinates = 0
        for (frame in 0 until frameCount) {
            val a = cpu.pose2D.frames[frame]
            val b = xnn4.pose2D.frames[frame]
            for (joint in 0 until JOINT_COUNT) {
                if (a.valid[joint] != b.valid[joint]) validityDisagreements++
                for (xy in 0..1) {
                    val first = a.coordinates[joint * 2 + xy]
                    val second = b.coordinates[joint * 2 + xy]
                    if (first.isFinite() && second.isFinite()) {
                        comparableCoordinates++
                        max2DAbsPx = maxOf(max2DAbsPx, abs(first - second))
                    }
                }
            }
        }
        val poseErrorsMm = mutableListOf<Double>()

        for (frame in 0 until frameCount) {
            if (
                !cpu.pose3D.frameValid[frame] ||
                !xnn4.pose3D.frameValid[frame]
            ) {
                continue
            }

            val frameOffset = frame * JOINT_COUNT * XYZ_COUNT
            val fullRootOffset =
                frameOffset + ROOT_INDEX * XYZ_COUNT
            val sampledRootOffset =
                frameOffset + ROOT_INDEX * XYZ_COUNT

            val fullRootX =
                cpu.pose3D.coordinates[fullRootOffset]
            val fullRootY =
                cpu.pose3D.coordinates[fullRootOffset + 1]
            val fullRootZ =
                cpu.pose3D.coordinates[fullRootOffset + 2]

            val sampledRootX =
                xnn4.pose3D.coordinates[sampledRootOffset]
            val sampledRootY =
                xnn4.pose3D.coordinates[sampledRootOffset + 1]
            val sampledRootZ =
                xnn4.pose3D.coordinates[sampledRootOffset + 2]

            for (joint in 0 until JOINT_COUNT) {
                if (joint == ROOT_INDEX) continue

                val offset = frameOffset + joint * XYZ_COUNT

                val fullX =
                    cpu.pose3D.coordinates[offset] -
                        fullRootX
                val fullY =
                    cpu.pose3D.coordinates[offset + 1] -
                        fullRootY
                val fullZ =
                    cpu.pose3D.coordinates[offset + 2] -
                        fullRootZ

                val sampledX =
                    xnn4.pose3D.coordinates[offset] -
                        sampledRootX
                val sampledY =
                    xnn4.pose3D.coordinates[offset + 1] -
                        sampledRootY
                val sampledZ =
                    xnn4.pose3D.coordinates[offset + 2] -
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
                "cpu_valid=${cpu.pose3D.frameValid.count { it }}; " +
                "xnnpack4_valid=${xnn4.pose3D.frameValid.count { it }}; " +
                "cpu_skipped=${cpu.pose3D.skippedWindowIndices.contentToString()}; " +
                "xnnpack4_skipped=${xnn4.pose3D.skippedWindowIndices.contentToString()}"
        }

        val angleErrors =
            linkedMapOf<String, MutableList<Double>>()

        for (frame in 0 until frameCount) {
            val fullAngles =
                angleValues(cpu.angles[frame])
            val sampledAngles =
                angleValues(xnn4.angles[frame])

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
                cpu.scores[frame].tableCScore != null &&
                    xnn4.scores[frame].tableCScore != null
            }

        require(pairedScoreIndices.isNotEmpty())

        val exactCount = pairedScoreIndices.count { frame ->
            cpu.scores[frame].tableCScore ==
                xnn4.scores[frame].tableCScore
        }

        val withinOneCount =
            pairedScoreIndices.count { frame ->
                abs(
                    requireNotNull(
                        cpu.scores[frame].tableCScore
                    ) -
                        requireNotNull(
                            xnn4.scores[frame].tableCScore
                        )
                ) <= 1
            }

        val fullPeakIndex = peakIndex(cpu.scores)
        val sampledPeakIndex = peakIndex(xnn4.scores)

        val fullCoverage =
            cpu.scores.count { it.valid } *
                100.0 / frameCount

        val sampledCoverage =
            xnn4.scores.count { it.valid } *
                100.0 / frameCount

        return buildString {
            appendLine("CPU4 VS XNNPACK4 PIPELINE PARITY REPORT")
            appendLine("frames=$frameCount")
            appendLine("cpu_2d_ms=${cpu.pose2DElapsedMs}")
            appendLine("xnnpack4_2d_ms=${xnn4.pose2DElapsedMs}")
            appendLine("cpu_3d_ms=${cpu.pose3DElapsedMs}")
            appendLine("xnnpack4_3d_ms=${xnn4.pose3DElapsedMs}")
            appendLine("comparable_2d_coordinates=$comparableCoordinates")
            appendLine("max_2d_abs_px=$max2DAbsPx")
            appendLine("validity_disagreements=$validityDisagreements")
            appendLine(
                "cpu_runtime_ms=${cpu.elapsedMs}"
            )
            appendLine(
                "xnnpack4_runtime_ms=${xnn4.elapsedMs}"
            )
            appendLine(
                "speedup=${format(
                    cpu.elapsedMs.toDouble() /
                        xnn4.elapsedMs.toDouble()
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
                "cpu_peak_reba=" +
                    cpu.scores[fullPeakIndex]
                        .finalRebaScore
            )
            appendLine(
                "xnnpack4_peak_reba=" +
                    xnn4.scores[sampledPeakIndex]
                        .finalRebaScore
            )
            appendLine(
                "cpu_peak_timestamp_ms=" +
                    fullPeakIndex * 1_000L / 60L
            )
            appendLine(
                "xnnpack4_peak_timestamp_ms=" +
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
                "cpu_coverage_percent=" +
                    format(fullCoverage)
            )
            appendLine(
                "xnnpack4_coverage_percent=" +
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
        val pose2D: VideoPose2DSequence,
        val pose2DElapsedMs: Long,
        val pose3DElapsedMs: Long,
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