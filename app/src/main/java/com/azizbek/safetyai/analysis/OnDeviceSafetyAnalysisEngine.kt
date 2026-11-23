package com.azizbek.safetyai.analysis

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.azizbek.safetyai.assessment.CouplingQuality
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Complete local pipeline:
 *
 * Video -> RTMPose-WB -> canonical 21 joints -> PoseMamba 3D
 * -> anatomical angles -> frozen trial-level Activity Layer -> REBA A/B/C.
 */
class OnDeviceSafetyAnalysisEngine(
    context: Context
) : SafetyAnalysisEngine, AutoCloseable {

    private val appContext = context.applicationContext
    private val runMutex = Mutex()

    private val rtmPoseLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RtmPoseVideoEstimator(appContext)
    }

    private val poseMambaLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PoseMambaSequenceLifter(
            OnDevicePoseMambaLifter(appContext),
            secondaryLifter = if (android.os.Build.MODEL == FrozenPipeline.PARALLEL_PHONE_MODEL) {
                OnDevicePoseMambaLifter(appContext)
            } else {
                null
            }
        )
    }

    @Volatile
    private var closed = false

    override val isAvailable: Boolean
        get() = !closed

    override suspend fun analyze(
        assessmentId: String,
        videoFile: File,
        context: AnalysisInputContext,
        onProgress: (AnalysisProgress) -> Unit
    ): AnalysisProgress = runMutex.withLock {
        if (closed) {
            return@withLock fail(
                "Safety analysis engine is closed.",
                onProgress
            )
        }

        val startedAt = System.currentTimeMillis()
        val totalStartedAtMs = SystemClock.elapsedRealtime()

        try {
            validateInput(
                assessmentId = assessmentId,
                videoFile = videoFile,
                input = context
            )

            onProgress(
                AnalysisProgress.Running(
                    stage = "Preparing video",
                    fractionComplete = 0.02f
                )
            )

            val sourceVideoSha256 = withContext(Dispatchers.IO) {
                FrozenPipeline.sha256(videoFile)
            }

            val pose2DStartedAtMs = SystemClock.elapsedRealtime()

            val pose2D = rtmPoseLazy.value.estimate(
                videoFile = videoFile
            ) { completed, total ->
                val fraction = if (total > 0) {
                    completed.toFloat() / total.toFloat()
                } else {
                    0f
                }

                onProgress(
                    AnalysisProgress.Running(
                        stage = "RTMPose 2D: $completed/$total frames",
                        fractionComplete =
                            (0.05f + 0.55f * fraction)
                                .coerceIn(0.05f, 0.60f)
                    )
                )
            }

            val pose2DMs = SystemClock.elapsedRealtime() - pose2DStartedAtMs
            Log.i(PERF_TAG, "rtmpose_2d_ms=$pose2DMs;source_frames=${pose2D.sourceFrameCount};output_frames=${pose2D.frameCount}")

            require(
                pose2D.frameCount >= PoseMambaTemporalInput.WINDOW_SIZE
            ) {
                "Video has ${pose2D.frameCount} frames at 60 Hz, but " +
                    "PoseMamba requires at least " +
                    "${PoseMambaTemporalInput.WINDOW_SIZE} frames."
            }

            onProgress(
                AnalysisProgress.Running(
                    stage = "Normalizing 2D pose",
                    fractionComplete = 0.62f
                )
            )

            val normalizationStartedAtMs = SystemClock.elapsedRealtime()

            val poseRepair = Pose2DGapRepair.repair(pose2D.frames)
            val normalized = PoseMambaTemporalInput.normalize(
                frames = poseRepair.frames,
                frameWidth = pose2D.frameWidth
            )

            // Keep raw detector coverage diagnostics alongside repaired model input.
            val missingCounts = IntArray(21)
            val longestGaps = IntArray(21)
            val currentGaps = IntArray(21)
            pose2D.frames.forEach { frame ->
                for (joint in 0 until 21) {
                    if (frame.valid[joint]) {
                        currentGaps[joint] = 0
                    } else {
                        missingCounts[joint]++
                        currentGaps[joint]++
                        longestGaps[joint] = maxOf(longestGaps[joint], currentGaps[joint])
                    }
                }
            }
            val windowQuality = PoseMambaTemporalInput.createWindows(normalized)
            val modelInputAffected = BooleanArray(pose2D.frameCount)
            var coveredUntil = 0
            windowQuality.forEach { window ->
                val end = window.startFrame + PoseMambaTemporalInput.WINDOW_SIZE
                if (window.valid && (window.startFrame until end).any {
                        poseRepair.imputedMaskByFrame[it] != 0
                    }) {
                    for (frame in maxOf(coveredUntil, window.startFrame) until end) {
                        modelInputAffected[frame] = true
                    }
                }
                coveredUntil = end
            }
            Log.i(PERF_TAG,
                "pose_input_quality=complete_frames:${normalized.frameValid.count { it }}" +
                    "/${normalized.frameCount};valid_windows:${windowQuality.count { it.valid }}" +
                    "/${windowQuality.size};decode_failures:${pose2D.decodeFailureCount};" +
                    "no_person_frames:${pose2D.noPersonFrameCount}")
            Log.i(PERF_TAG, "pose_missing_joints=" + (0 until 21)
                .filter { missingCounts[it] > 0 }
                .joinToString(",") { joint ->
                    "${RtmPoseCanonical21.jointNames[joint]}:${missingCounts[joint]}" +
                        "/gap${longestGaps[joint]}"
                })
            Log.i(PERF_TAG,
                "pose_repair=frames:${poseRepair.imputedMaskByFrame.count { it != 0 }}" +
                    "/${pose2D.frameCount};remaining_incomplete:" +
                    "${poseRepair.remainingIncompleteFrames};model_affected:" +
                    "${modelInputAffected.count { it }};max_gap_frames:" +
                    "${Pose2DGapRepair.MAX_INTERPOLATION_FRAMES}")
            require(windowQuality.any { it.valid }) {
                val missing = (0 until 21)
                    .filter { missingCounts[it] > 0 }
                    .sortedByDescending { missingCounts[it] }
                    .take(3)
                    .joinToString(", ") { joint ->
                        "${RtmPoseCanonical21.jointNames[joint]} " +
                            "${missingCounts[joint]}/${pose2D.frameCount} frames"
                    }
                "Cannot estimate a 3D pose after bounded 2D repair: " +
                    "no complete 243-frame window. Missing joints: $missing. " +
                    "Keep the worker and limbs visible for at least four seconds."
            }

            onProgress(
                AnalysisProgress.Running(
                    stage = "PoseMamba 3D",
                    fractionComplete = 0.65f
                )
            )

            val normalizationMs = SystemClock.elapsedRealtime() - normalizationStartedAtMs
            Log.i(PERF_TAG, "normalize_2d_ms=$normalizationMs")

            val poseMambaStartedAtMs = SystemClock.elapsedRealtime()

            val pose3D = poseMambaLazy.value.lift(
                sequence = normalized
            ) { completed, total ->
                val fraction = if (total > 0) {
                    completed.toFloat() / total.toFloat()
                } else {
                    0f
                }

                onProgress(
                    AnalysisProgress.Running(
                        stage = "PoseMamba 3D: $completed/$total windows",
                        fractionComplete =
                            (0.65f + 0.20f * fraction)
                                .coerceIn(0.65f, 0.85f)
                    )
                )
            }

            val pose3DMs = SystemClock.elapsedRealtime() - poseMambaStartedAtMs
            Log.i(PERF_TAG, "posemamba_3d_ms=$pose3DMs;frames=${pose3D.frameCount}")

            require(pose3D.frameCount == pose2D.frameCount) {
                "2D/3D frame-count mismatch: " +
                    "${pose2D.frameCount} vs ${pose3D.frameCount}"
            }
            val geometryQuality = PoseGeometryQuality.inspect(pose2D, pose3D)
            Log.i(PERF_TAG, "pose_geometry=collapsed_frames:${geometryQuality.collapsedFrames}/" +
                "${geometryQuality.checkedFrames};unreliable:${geometryQuality.unreliable}")

            onProgress(
                AnalysisProgress.Running(
                    stage = "Calculating anatomical angles",
                    fractionComplete = 0.88f
                )
            )

            val anglesStartedAtMs = SystemClock.elapsedRealtime()

            val angles = RebaAngleCalculator.compute(pose3D)

            val anglesMs = SystemClock.elapsedRealtime() - anglesStartedAtMs
            Log.i(PERF_TAG, "angles_ms=$anglesMs")

            require(angles.size == pose3D.frameCount) {
                "Angle/frame-count mismatch: " +
                    "${angles.size} vs ${pose3D.frameCount}"
            }

            onProgress(
                AnalysisProgress.Running(
                    stage = "Evaluating activity",
                    fractionComplete = 0.90f
                )
            )
            val activityStartedAtMs = SystemClock.elapsedRealtime()
            val repetitionChannels = TaskRepetitionChannels.forTask(context.taskType)
            val angleSeries = angleChannels(angles)
            val detectedActivity = ResearchActivityLayer.evaluate(
                angleSeries,
                FrozenPipeline.OUTPUT_FPS.toDouble(),
                false, // Stationary lifting study protocol; walking tasks require a separate protocol.
                null, // Ground stability cannot be inferred from joint motion.
                repetitionChannels
            )
            val activityResult = if (geometryQuality.unreliable) {
                ResearchActivityLayer.withUnreliableRepetition(detectedActivity)
            } else detectedActivity
            val activityMs = SystemClock.elapsedRealtime() - activityStartedAtMs
            Log.i(PERF_TAG, "activity_ms=$activityMs;" +
                "score=${activityResult.score};fully_evaluated=${activityResult.fullyEvaluated};" +
                "task=${context.taskType};repetition_candidates=${repetitionChannels ?: "all"};" +
                "repetition_channel=${activityResult.repetition.channel};" +
                "repetition_count=${activityResult.repetition.count}")
            if (repetitionChannels != null) {
                val baseline = ResearchActivityLayer.evaluate(
                    angleSeries, FrozenPipeline.OUTPUT_FPS.toDouble(), false, null
                )
                Log.i("SafetyAI.RepCompare", "task=${context.taskType};" +
                    "generic_count=${baseline.repetition.count};" +
                    "generic_channel=${baseline.repetition.channel};" +
                    "generic_status=${baseline.repetition.status};" +
                    "task_count=${activityResult.repetition.count};" +
                    "task_channel=${activityResult.repetition.channel};" +
                    "task_status=${activityResult.repetition.status};" +
                    "quality_gate=${geometryQuality.unreliable};" +
                    "static_equal=${baseline.staticHold.status == activityResult.staticHold.status};" +
                    "rapid_equal=${baseline.rapidMotion.status == activityResult.rapidMotion.status}")
            }

            onProgress(
                AnalysisProgress.Running(
                    stage = "Calculating REBA Tables A/B/C",
                    fractionComplete = 0.92f
                )
            )

            val manualInputs = RebaManualInputs(
                loadKg = context.loadKg,
                suddenForce = context.suddenForce,
                coupling = couplingScore(context),
                activityScore = activityResult.score,
                activityPending = !activityResult.fullyEvaluated
            )

            val rebaStartedAtMs = SystemClock.elapsedRealtime()

            val scores = RebaScorer.scoreSequence(
                angles = angles,
                manual = manualInputs
            )

            val rebaMs = SystemClock.elapsedRealtime() - rebaStartedAtMs
            Log.i(PERF_TAG, "reba_ms=$rebaMs")

            require(scores.size == pose3D.frameCount) {
                "REBA/frame-count mismatch: " +
                    "${scores.size} vs ${pose3D.frameCount}"
            }

            val validScoreIndices = scores.indices.filter { frameIndex ->
                pose3D.frameValid[frameIndex] &&
                    scores[frameIndex].valid &&
                    scores[frameIndex].finalRebaScore != null
            }

            require(validScoreIndices.isNotEmpty()) {
                "No frame produced a valid 3D pose and REBA score. " +
                    "Valid 3D frames: ${pose3D.frameValid.count { it }}" +
                    "/${pose3D.frameCount}; skipped 243-frame windows: " +
                    "${pose3D.skippedWindowIndices.size}/${windowQuality.size}. " +
                    "See SafetyAI.Perf pose_missing_joints for input coverage."
            }

            /*
             * maxByOrNull keeps the earliest occurrence when several frames
             * have the same highest final REBA score.
             */
            val peakFrameIndex = validScoreIndices.maxByOrNull { frameIndex ->
                scores[frameIndex].finalRebaScore
                    ?: Int.MIN_VALUE
            } ?: error("No peak REBA frame")

            val peak = scores[peakFrameIndex]
            val peakFinalScore = requireNotNull(peak.finalRebaScore)
            val riskLevel = riskLevel(peakFinalScore)

            val analyzedFrames = buildFrameResults(
                pose2D = pose2D,
                pose3D = pose3D,
                angles = angles
            )

            val coveragePercent =
                validScoreIndices.size * 100f / pose3D.frameCount

            val warnings = buildWarnings(
                pose2D = pose2D,
                pose3D = pose3D,
                peak = peak,
                coveragePercent = coveragePercent,
                activityResult = activityResult,
                poseRepair = poseRepair,
                geometryQuality = geometryQuality
            )

            val completedAt = System.currentTimeMillis()
            val nativeScanSha256 = withContext(Dispatchers.IO) {
                FrozenPipeline.sha256(File(appContext.applicationInfo.nativeLibraryDir, "libposemamba_scan_ort.so"))
            }
            val configuration = FrozenPipeline.configuration()
            val provenance = PipelineProvenance(
                pipelineId = FrozenPipeline.ID,
                configuration = configuration,
                configurationSha256 = FrozenPipeline.configurationHash(configuration),
                modelSha256 = FrozenPipeline.modelHashes(),
                sourceVideoSha256 = sourceVideoSha256,
                runtime = rtmPoseLazy.value.executionProfile() + linkedMapOf(
                    "device_model" to android.os.Build.MODEL,
                    "device_soc" to if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                        android.os.Build.SOC_MODEL else "unavailable",
                    "android_sdk" to android.os.Build.VERSION.SDK_INT.toString(),
                    "native_scan_sha256" to nativeScanSha256,
                    "source_fps" to pose2D.sourceFps.toString(),
                    "source_frames" to pose2D.sourceFrameCount.toString(),
                    "source_width_px" to pose2D.frameWidth.toString(),
                    "source_height_px" to pose2D.frameHeight.toString(),
                    "output_frames" to pose2D.frameCount.toString(),
                    "task" to (context.taskType?.name ?: "unspecified"),
                    "posemamba_parallel_sessions" to if (android.os.Build.MODEL == FrozenPipeline.PARALLEL_PHONE_MODEL) "2" else "1"
                ),
                timingsMs = linkedMapOf(
                    "rtmpose_2d" to pose2DMs, "normalization_and_repair" to normalizationMs,
                    "posemamba_3d" to pose3DMs, "angles" to anglesMs,
                    "activity" to activityMs, "reba" to rebaMs,
                    "analysis_before_result_assembly" to (SystemClock.elapsedRealtime() - totalStartedAtMs)
                )
            )
            Log.i("SafetyAI.Freeze", "pipeline_id=${provenance.pipelineId};" +
                "configuration_sha256=${provenance.configurationSha256};" +
                "video_sha256=$sourceVideoSha256;device=${android.os.Build.MODEL}")

            val result = SafetyAnalysisResult(
                assessmentId = assessmentId,
                processingStartedAtEpochMs = startedAt,
                processingCompletedAtEpochMs = completedAt,
                inputQualityChecks = listOf(
                    InputQualityCheck(
                        description = "Input video exists and is not empty",
                        passed = videoFile.isFile &&
                            videoFile.length() > 0L
                    ),
                    InputQualityCheck(
                        description = "Video frame rate is 30 or 60 FPS",
                        passed = pose2D.sourceFps == 30 ||
                            pose2D.sourceFps == 60
                    ),
                    InputQualityCheck(
                        description =
                            "At least one 243-frame PoseMamba window is available",
                        passed = pose2D.frameCount >=
                            PoseMambaTemporalInput.WINDOW_SIZE
                    ),
                    InputQualityCheck(
                        description = "All source frames were decoded",
                        passed = pose2D.decodeFailureCount == 0
                    ),
                    InputQualityCheck(
                        description = "At least one valid REBA frame was produced",
                        passed = validScoreIndices.isNotEmpty()
                    ),
                    InputQualityCheck(
                        description = "2D and 3D shoulder geometry is reliable",
                        passed = !geometryQuality.unreliable
                    )
                ),
                frames = analyzedFrames,
                reba = RebaComponents(
                    neckScore = peak.components.neck.score ?: 0,
                    trunkScore = peak.components.trunk.score ?: 0,
                    legsScore = peak.components.legs.score ?: 0,
                    upperArmScore = maxOf(
                        peak.components.upperArmR.score ?: 0,
                        peak.components.upperArmL.score ?: 0
                    ),
                    lowerArmScore = maxOf(
                        peak.components.lowerArmR.score ?: 0,
                        peak.components.lowerArmL.score ?: 0
                    ),
                    wristScore = maxOf(
                        peak.components.wristR.score ?: 0,
                        peak.components.wristL.score ?: 0
                    ),
                    activityModifier = activityResult.score,
                    finalScore = peakFinalScore,
                    riskLevel = riskLevel
                ),

                /*
                 * Body-region narrative findings will be populated by the
                 * reporting stage. Empty is safer than fabricating findings.
                 */
                bodyRegionFindings = emptyList(),

                activity = buildActivityFindings(activityResult, pose2D),
                overallRiskLevel = riskLevel,
                highestRiskTimestampMs =
                    pose2D.timestampsUs[peakFrameIndex] / 1_000L,
                coveragePercent = coveragePercent,
                recommendations = listOfNotNull(peak.actionText),
                warnings = warnings,
                processingErrors = emptyList(),
                frameRebaResults = buildFrameRebaResults(pose2D, pose3D, scores, modelInputAffected),
                provenance = provenance,
                pose2DRepair = Pose2DRepairSummary(
                    method = "two_sided_linear_max_60_frames;edge_hold_max_10_frames",
                    imputedMaskByFrame = poseRepair.imputedMaskByFrame,
                    imputedByJoint = (0 until 21).filter { poseRepair.imputedCounts[it] > 0 }
                        .associate { RtmPoseCanonical21.jointNames[it] to poseRepair.imputedCounts[it] },
                    longestImputedGapFramesByJoint = (0 until 21)
                        .filter { poseRepair.longestImputedGap[it] > 0 }
                        .associate { RtmPoseCanonical21.jointNames[it] to poseRepair.longestImputedGap[it] },
                    remainingIncompleteFrames = poseRepair.remainingIncompleteFrames,
                    modelInputAffectedByFrame = modelInputAffected
                )
            )

            Log.i(PERF_TAG, "total_ms=${SystemClock.elapsedRealtime() - totalStartedAtMs};frames=${pose3D.frameCount}")

            val completed = AnalysisProgress.Completed(result)
            onProgress(completed)
            completed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            fail(
                reason = error.message
                    ?: "Safety analysis failed: " +
                        error.javaClass.simpleName,
                onProgress = onProgress
            )
        }
    }

    private fun validateInput(
        assessmentId: String,
        videoFile: File,
        input: AnalysisInputContext
    ) {
        require(assessmentId.isNotBlank()) {
            "Assessment ID must not be blank."
        }

        require(videoFile.isFile && videoFile.length() > 0L) {
            "Video file is missing or empty."
        }

        require(input.loadKg.isFinite() && input.loadKg >= 0.0) {
            "Load must be a finite non-negative value."
        }

        if (input.coupling == CouplingQuality.NOT_APPLICABLE) {
            require(input.loadKg == 0.0) {
                "NOT_APPLICABLE coupling is allowed only when load is 0 kg."
            }
        }
    }

    /**
     * Mapping follows the frozen Android/Python REBA contract:
     * 0 = good/not applicable, 1 = fair, 2 = poor/unacceptable.
     */
    private fun couplingScore(
        input: AnalysisInputContext
    ): Int = when (input.coupling) {
        CouplingQuality.GOOD -> 0
        CouplingQuality.FAIR -> 1
        CouplingQuality.POOR -> 2
        CouplingQuality.UNACCEPTABLE -> 2

        CouplingQuality.NOT_APPLICABLE -> {
            require(input.loadKg == 0.0) {
                "NOT_APPLICABLE coupling requires zero load."
            }
            0
        }
    }

    private fun buildFrameResults(
        pose2D: VideoPose2DSequence,
        pose3D: PoseMamba3DSequence,
        angles: List<RebaFrameAngles>
    ): List<FrameAnalysis> =
        List(pose3D.frameCount) { frameIndex ->
            val source2D = pose2D.frames[frameIndex]
            val frame3DOffset = frameIndex * JOINT_COUNT * XYZ_COUNT

            val keypoints2D = List(JOINT_COUNT) { jointIndex ->
                val coordinateOffset = jointIndex * XY_COUNT
                val x = source2D.coordinates[coordinateOffset]
                val y = source2D.coordinates[coordinateOffset + 1]
                val confidence =
                    source2D.confidence[jointIndex]
                        .finiteOrZero()

                val valid =
                    source2D.valid[jointIndex] &&
                        x.isFinite() &&
                        y.isFinite()

                Joint3D(
                    x = x,
                    y = y,
                    z = 0f,
                    confidence = confidence,
                    valid = valid
                )
            }

            val joints3D = List(JOINT_COUNT) { jointIndex ->
                val coordinateOffset =
                    frame3DOffset + jointIndex * XYZ_COUNT

                val x = pose3D.coordinates[coordinateOffset]
                val y = pose3D.coordinates[coordinateOffset + 1]
                val z = pose3D.coordinates[coordinateOffset + 2]

                val confidence =
                    source2D.confidence[jointIndex]
                        .finiteOrZero()

                val valid =
                    pose3D.frameValid[frameIndex] &&
                        x.isFinite() &&
                        y.isFinite() &&
                        z.isFinite()

                Joint3D(
                    x = x,
                    y = y,
                    z = z,
                    confidence = confidence,
                    valid = valid
                )
            }

            FrameAnalysis(
                timestampMs =
                    pose2D.timestampsUs[frameIndex] / 1_000L,
                keypoints2D = keypoints2D,
                pose3D = joints3D,
                jointAngles = angles[frameIndex].toAngleMap()
            )
        }

    private fun buildFrameRebaResults(
        pose2D: VideoPose2DSequence,
        pose3D: PoseMamba3DSequence,
        scores: List<RebaFrameScore>,
        modelInputAffected: BooleanArray
    ): List<FrameRebaResult> {
        val timestamps = pose2D.timestampsUs
        val nominalDurationUs = 1_000_000L / 60L
        return List(scores.size) { index ->
            val score = scores[index]
            val finalScore = if (pose3D.frameValid[index] && score.valid) {
                score.finalRebaScore
            } else null
            val durationUs = if (index + 1 < scores.size) {
                (timestamps[index + 1] - timestamps[index]).coerceAtLeast(0L)
            } else {
                // The last sample represents one additional output-frame interval.
                nominalDurationUs
            }
            FrameRebaResult(
                frameIndex = index,
                timestampUs = timestamps[index],
                durationUs = durationUs,
                valid = finalScore != null,
                scoreA = if (finalScore != null) score.scoreA else null,
                scoreB = if (finalScore != null) score.scoreB else null,
                tableCScore = if (finalScore != null) score.tableCScore else null,
                activityScore = score.activityScore,
                finalScore = finalScore,
                riskLevel = finalScore?.let(::riskLevel),
                neckScore = score.components.neck.score,
                trunkScore = score.components.trunk.score,
                legsScore = score.components.legs.score,
                upperArmRScore = score.components.upperArmR.score,
                upperArmLScore = score.components.upperArmL.score,
                lowerArmRScore = score.components.lowerArmR.score,
                lowerArmLScore = score.components.lowerArmL.score,
                wristRScore = score.components.wristR.score,
                wristLScore = score.components.wristL.score,
                loadScore = score.components.load.score,
                fullyEvaluated = score.fullyEvaluated && !modelInputAffected[index],
                invalidReasons = if (pose3D.frameValid[index]) score.invalidReasons
                    else score.invalidReasons + "invalid_3d_pose"
            )
        }
    }

    private fun RebaFrameAngles.toAngleMap(): Map<String, Float> =
        buildMap {
            fun add(name: String, value: Number?) {
                val numeric = value?.toDouble()
                if (numeric != null && numeric.isFinite()) {
                    put(name, numeric.toFloat())
                }
            }

            add("trunk_sagittal_deg", trunkSagittalDeg)
            add("trunk_lateral_deg", trunkLateralDeg)
            add("trunk_twist_deg", trunkTwistDeg)
            add("neck_flex_deg", neckFlexDeg)
            add("neck_lateral_deg", neckLateralDeg)
            add("upper_arm_r_sagittal_deg", upperArmRSagittalDeg)
            add("upper_arm_l_sagittal_deg", upperArmLSagittalDeg)
            add("upper_arm_r_abduction_deg", upperArmRAbductionDeg)
            add("upper_arm_l_abduction_deg", upperArmLAbductionDeg)
            add("lower_arm_r_deg", lowerArmRDeg)
            add("lower_arm_l_deg", lowerArmLDeg)
            add("wrist_r_deg", wristRDeg)
            add("wrist_l_deg", wristLDeg)
            add("knee_r_deg", kneeRDeg)
            add("knee_l_deg", kneeLDeg)
            add("knee_max_deg", kneeMaxDeg)
        }

    private fun buildWarnings(
        pose2D: VideoPose2DSequence,
        pose3D: PoseMamba3DSequence,
        peak: RebaFrameScore,
        coveragePercent: Float,
        activityResult: ResearchActivityLayer.Result,
        poseRepair: Pose2DGapRepair.Result,
        geometryQuality: PoseGeometryQuality
    ): List<String> = buildList {
        add("Repetition was evaluated for stationary lifting. Counts for other tasks are exploratory; walking or camera motion may create false cycles.")
        val repairedFrames = poseRepair.imputedMaskByFrame.count { it != 0 }
        if (repairedFrames > 0) {
            add("2D gaps were interpolated or edge-held in $repairedFrames/${pose2D.frameCount} " +
                "frames before PoseMamba. Scores influenced by repaired joints are estimates; " +
                "review the 2D repair mask in the case study export.")
        }
        if (geometryQuality.unreliable) {
            add("Pose geometry is unreliable: 2D and 3D shoulder positions collapsed in " +
                "${geometryQuality.collapsedFrames}/${geometryQuality.checkedFrames} checked frames. " +
                "The repetition point was withheld; re-record before using the 3D pose or REBA scores.")
        }
        if (poseRepair.remainingIncompleteFrames > 0) {
            add("${poseRepair.remainingIncompleteFrames} output frames remain incomplete " +
                "after bounded 2D repair; affected 3D windows may be skipped.")
        }
        if (!activityResult.fullyEvaluated) {
            add("Activity assessment is incomplete. REBA combines Table C with only " +
                "confirmed activity modifiers; unassessed criteria contribute no point.")
            listOf(
                "Static" to activityResult.staticHold,
                "Repetition" to activityResult.repetition,
                "Rapid motion" to activityResult.rapidMotion
            ).forEach { (label, criterion) ->
                if (criterion.status == ResearchActivityLayer.Status.INSUFFICIENT ||
                    criterion.status == ResearchActivityLayer.Status.UNAVAILABLE) {
                    add("$label: ${criterion.reason ?: criterion.status.name.lowercase()}.")
                }
            }
        }

        if (pose2D.decodeFailureCount > 0) {
            add(
                "${pose2D.decodeFailureCount} source video frames " +
                    "could not be decoded."
            )
        }

        if (pose2D.noPersonFrameCount > 0) {
            add(
                "RTMPose did not find a tracked person in " +
                    "${pose2D.noPersonFrameCount} source frames."
            )
        }

        if (pose3D.skippedWindowIndices.isNotEmpty()) {
            add(
                "${pose3D.skippedWindowIndices.size} PoseMamba windows " +
                    "were skipped because their 2D input was incomplete."
            )
        }

        if (coveragePercent < 100f) {
            add(
                "Valid REBA coverage is " +
                    "%.1f".format(coveragePercent) +
                    "%; invalid frames were not replaced with zero risk."
            )
        }

        if (!peak.postureFullyEvaluated) {
            add(
                "Some REBA posture modifiers were not observable from " +
                    "the current pose contract."
            )
        }

        if (peak.unsupportedComponents.isNotEmpty()) {
            add(
                "Unsupported REBA components: " +
                    peak.unsupportedComponents
                        .distinct()
                        .joinToString(", ")
            )
        }
    }

    private fun angleChannels(angles: List<RebaFrameAngles>): Map<String, DoubleArray> {
        return ResearchActivityLayer.CHANNELS.associateWith { channel ->
            DoubleArray(angles.size) { i ->
                val a = angles[i]
                when (channel) {
                    "trunk_sagittal_deg" -> a.trunkSagittalDeg
                    "trunk_lateral_deg" -> a.trunkLateralDeg
                    "trunk_twist_deg" -> a.trunkTwistDeg
                    "neck_flex_deg" -> a.neckFlexDeg
                    "neck_lateral_deg" -> a.neckLateralDeg
                    "upper_arm_r_sagittal_deg" -> a.upperArmRSagittalDeg
                    "upper_arm_l_sagittal_deg" -> a.upperArmLSagittalDeg
                    "upper_arm_r_abd_deg" -> a.upperArmRAbductionDeg
                    "upper_arm_l_abd_deg" -> a.upperArmLAbductionDeg
                    "lower_arm_r_deg" -> a.lowerArmRDeg
                    "lower_arm_l_deg" -> a.lowerArmLDeg
                    "wrist_r_deg" -> a.wristRDeg
                    "wrist_l_deg" -> a.wristLDeg
                    "knee_r_deg" -> a.kneeRDeg
                    "knee_l_deg" -> a.kneeLDeg
                    "knee_max_deg" -> a.kneeMaxDeg
                    else -> error("Unsupported research angle channel: $channel")
                }
            }
        }
    }

    private fun buildActivityFindings(
        evaluation: ResearchActivityLayer.Result,
        pose2D: VideoPose2DSequence
    ): ActivityFindings {
        fun timeMs(index: Int): Long =
            pose2D.timestampsUs[index.coerceIn(0, pose2D.frameCount - 1)] / 1_000L

        fun region(channel: String): BodyRegion = when {
            channel.startsWith("trunk") -> BodyRegion.TRUNK
            channel.startsWith("neck") -> BodyRegion.NECK
            channel.startsWith("upper_arm") -> BodyRegion.UPPER_ARMS
            channel.startsWith("lower_arm") -> BodyRegion.LOWER_ARMS
            channel.startsWith("wrist") -> BodyRegion.WRISTS
            else -> BodyRegion.LEGS
        }

        fun status(value: ResearchActivityLayer.Status): String = value.name.lowercase()

        return ActivityFindings(
            staticIntervals = evaluation.staticHold.events.map { event ->
                StaticPostureInterval(timeMs(event.startFrame), timeMs(event.endFrame), region(event.channel))
            },
            repetitionEvents = evaluation.repetition.events.map { event ->
                RepetitionEvent(timeMs(event.endFrame), region(event.channel))
            },
            repetitionCount = evaluation.repetition.count,
            rapidMotionEvents = evaluation.rapidMotion.events.map { event ->
                RapidMotionEvent(timeMs(event.startFrame), region(event.channel))
            },
            rapidMotionDetected = evaluation.rapidMotion.status == ResearchActivityLayer.Status.POSITIVE,
            staticStatus = status(evaluation.staticHold.status),
            repetitionStatus = status(evaluation.repetition.status),
            rapidMotionStatus = status(evaluation.rapidMotion.status),
            staticReason = evaluation.staticHold.reason,
            repetitionReason = evaluation.repetition.reason,
            rapidMotionReason = evaluation.rapidMotion.reason,
            repetitionChannel = evaluation.repetition.channel,
            repetitionRatePerMinute = evaluation.repetition.ratePerMinute,
            fullyEvaluated = evaluation.fullyEvaluated,
            scoreStatus = if (evaluation.fullyEvaluated) "final" else "conditional_incomplete"
        )
    }

    private fun riskLevel(score: Int): RiskLevel =
        when {
            score <= 3 -> RiskLevel.LOW
            score <= 7 -> RiskLevel.MEDIUM
            score <= 10 -> RiskLevel.HIGH
            else -> RiskLevel.VERY_HIGH
        }

    private fun Float.finiteOrZero(): Float =
        if (isFinite()) this else 0f

    private fun fail(
        reason: String,
        onProgress: (AnalysisProgress) -> Unit
    ): AnalysisProgress.Failed {
        val failure = AnalysisProgress.Failed(reason)
        onProgress(failure)
        return failure
    }

    override fun close() {
        if (!closed) {
            closed = true

            if (rtmPoseLazy.isInitialized()) {
                rtmPoseLazy.value.close()
            }

            if (poseMambaLazy.isInitialized()) {
                poseMambaLazy.value.close()
            }
        }
    }

    private companion object {
        const val PERF_TAG = "SafetyAI.Perf"
        const val JOINT_COUNT = 21
        const val XY_COUNT = 2
        const val XYZ_COUNT = 3
    }
}
