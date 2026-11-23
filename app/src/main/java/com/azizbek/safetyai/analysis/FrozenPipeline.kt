package com.azizbek.safetyai.analysis

import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.ondevice.PoseMambaModelLoader
import java.io.File
import java.io.Serializable
import java.security.MessageDigest

/** Captured when analysis runs, never inferred from a later app installation. */
data class PipelineProvenance(
    val pipelineId: String,
    val configuration: Map<String, String>,
    val configurationSha256: String,
    val modelSha256: Map<String, String>,
    val sourceVideoSha256: String,
    val runtime: Map<String, String>,
    val timingsMs: Map<String, Long>
) : Serializable {
    companion object { private const val serialVersionUID: Long = 1L }
}

/** The candidate becomes the case-study freeze only after the recorded checks pass. */
object FrozenPipeline {
    const val ID = "safetyai-case-study-20261001-v1"
    const val INFERENCE_FPS = 15
    const val OUTPUT_FPS = 60
    const val RTMPOSE_THREADS = 4
    const val POSEMAMBA_THREADS = 4
    const val PARALLEL_PHONE_MODEL = "25019PNF3C"
    const val PARALLEL_PHONE_SOC = "SM8750"

    fun configuration(): Map<String, String> = linkedMapOf(
        "joint_order" to RtmPoseCanonical21.jointNames.joinToString(","),
        "root_index" to RtmPoseCanonical21.ROOT_INDEX.toString(),
        "confidence_threshold" to RtmPoseCanonical21.CONFIDENCE_THRESHOLD.toString(),
        "rtmpose_inference_fps" to INFERENCE_FPS.toString(),
        "output_fps" to OUTPUT_FPS.toString(),
        "source_fps" to "30_or_60",
        "normalization" to "subtract_pelvis_then_divide_by_frame_width_over_2",
        "window_frames" to PoseMambaTemporalInput.WINDOW_SIZE.toString(),
        "window_policy" to "nonoverlap_243_then_backward_tail_first_window_owns_overlap_including_invalid",
        "gap_repair_max_frames" to Pose2DGapRepair.MAX_INTERPOLATION_FRAMES.toString(),
        "edge_hold_max_frames" to Pose2DGapRepair.MAX_EDGE_HOLD_FRAMES.toString(),
        "filter_cutoff_hz" to ResearchActivityLayer.FILTER_CUTOFF_HZ.toString(),
        "static_duration_strictly_greater_s" to ResearchActivityLayer.STATIC_MIN_DURATION_S.toString(),
        "static_max_range_deg" to ResearchActivityLayer.STATIC_MAX_RANGE_DEG.toString(),
        "repetition_min_cycles" to ResearchActivityLayer.REPETITION_MIN_CYCLES.toString(),
        "repetition_min_rate_per_min_inclusive" to ResearchActivityLayer.REPETITION_MIN_RATE_PER_MIN.toString(),
        "repetition_rate_denominator" to "entire_output_sequence_duration",
        "repetition_scoring_policy" to "case_study_rule_not_literal_published_REBA_rate_rule",
        "repetition_channel_selection" to "amplitude_snr_times_regularity_then_runner_up_merge",
        "repetition_merge_tolerance_s" to ResearchActivityLayer.REPETITION_MERGE_TOLERANCE_S.toString(),
        "rapid_min_velocity_deg_s" to ResearchActivityLayer.RAPID_MIN_VELOCITY_DEG_S.toString(),
        "rapid_min_excursion_deg" to ResearchActivityLayer.RAPID_MIN_EXCURSION_DEG.toString(),
        "rapid_max_duration_s" to ResearchActivityLayer.RAPID_MAX_DURATION_S.toString(),
        "rapid_motion_floor_deg_s" to ResearchActivityLayer.RAPID_MOTION_FLOOR_DEG_S.toString(),
        "locomotion_policy" to "stationary_recording_protocol_false_no_segment_mask",
        "unstable_base_policy" to "not_observed_null_rapid_negative_remains_unavailable",
        "geometry_gate" to "checked>=120_and_more_than_half_have_2d_shoulder_torso<0.15_and_3d<0.25",
        "geometry_gate_evidence" to "operational_quality_check_three_clip_audit_not_general_validation",
        "load_policy" to "lt5=0_5to10inclusive=1_gt10=2_sudden_force_add1_to_A",
        "coupling_policy" to "good=0_fair=1_poor=2_legacy_unacceptable=2_not_applicable=0",
        "activity_application" to "confirmed_trial_level_sum_applied_to_each_scored_frame",
        "risk_bins" to "1to3_low_4to7_medium_8to10_high_11to15_very_high",
        "rtmpose_threads" to RTMPOSE_THREADS.toString(),
        "posemamba_threads" to POSEMAMBA_THREADS.toString(),
        "onnxruntime_android_version" to "1.22.0",
        "native_scan_compiler" to "O3_fno_fast_math_ffp_contract_off",
        "detector_refresh_policy" to "initial_then_body_confidence_recovery_8_of_17_at_0.30",
        "parallel_profile" to "2pose_XNNPACK4_if_Android12plus_25019PNF3C_SM8750_2lift_if_25019PNF3C_otherwise_CPU_sequential",
        "preview_policy" to "x_reflected_floor_relative_5frame_median_pose_lead32ms_visual_only"
    ).also { config ->
        listOf(TaskType.MATERIAL_LIFTING, TaskType.REBAR_TYING, TaskType.ROOFING_WORK,
            TaskType.OVERHEAD_FASTENING, TaskType.WALL_PAINTING, TaskType.OTHER).forEach { task ->
            config["repetition_channels.${task.name}"] =
                TaskRepetitionChannels.forTask(task)?.joinToString(",") ?: "generic_all_except_knee_max"
        }
    }

    fun configurationHash(config: Map<String, String>): String = sha256(
        config.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
            .toByteArray(Charsets.UTF_8)
    )

    fun modelHashes(): Map<String, String> = linkedMapOf(
        "yolox" to RtmPoseModelLoader.DETECTOR_SHA256,
        "rtmpose" to RtmPoseModelLoader.POSE_SHA256,
        "posemamba" to PoseMambaModelLoader.MODEL_SHA256
    )

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65_536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }

    private fun sha256(bytes: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") {
        "%02x".format(it.toInt() and 255)
    }
}
