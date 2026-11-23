package com.azizbek.safetyai.reports

import android.content.Context
import androidx.core.content.FileProvider
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import com.azizbek.safetyai.analysis.RtmPoseCanonical21
import com.azizbek.safetyai.analysis.caseStudySummary
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.assessment.loadCategoryLabel
import com.azizbek.safetyai.assessment.TaskType
import java.io.File
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Export a reproducible, machine-readable record without loading the video into the ZIP. */
object CaseStudyExport {
    fun generate(context: Context, assessment: Assessment, result: SafetyAnalysisResult): File {
        require(assessment.status == AssessmentStatus.COMPLETED)
        require(result.assessmentId == assessment.id)
        val summary = requireNotNull(result.caseStudySummary()) {
            "Per-frame REBA was not saved in this assessment. Reanalyze the video for case study export."
        }
        val timeline = requireNotNull(result.frameRebaResults)
        val repetitionValidatedForTask = assessment.taskType == TaskType.LIFTING ||
            assessment.taskType == TaskType.MATERIAL_LIFTING
        val geometryUnreliable = result.inputQualityChecks.any {
            !it.passed && it.description == "2D and 3D shoulder geometry is reliable"
        }
        val outputDir = File(context.cacheDir, "shared_reports").apply { mkdirs() }
        val file = File(outputDir, "safety_ai_case_study_${assessment.id}.zip")
        val temporary = File(outputDir, "${file.name}.tmp")
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                fun entry(name: String, write: (OutputStreamWriter) -> Unit) {
                    zip.putNextEntry(ZipEntry(name))
                    val writer = OutputStreamWriter(zip, Charsets.UTF_8)
                    write(writer)
                    writer.flush()
                    zip.closeEntry()
                }
                fun jsonList(values: List<String>): JSONArray = JSONArray().also { array ->
                    values.forEach { array.put(it) }
                }
                entry("summary.json") { writer ->
                    val risk = JSONObject()
                    summary.riskTimes.forEach {
                        risk.put(it.level.name, JSONObject()
                            .put("frames", it.frames)
                            .put("duration_ms", it.durationMs)
                            .put("percent_of_video", it.percentOfVideo))
                    }
                    val activity = result.activity
                    val repair = result.pose2DRepair
                    val provenance = result.provenance
                    val provenanceJson = provenance?.let {
                        JSONObject()
                            .put("pipeline_id", it.pipelineId)
                            .put("configuration_sha256", it.configurationSha256)
                            .put("configuration", JSONObject(it.configuration))
                            .put("model_sha256", JSONObject(it.modelSha256))
                            .put("source_video_sha256", it.sourceVideoSha256)
                            .put("runtime", JSONObject(it.runtime))
                            .put("timings_ms", JSONObject(it.timingsMs))
                    }
                    val repairJson = JSONObject()
                        .put("method", repair?.method)
                        .put("imputed_frames", repair?.imputedFrameCount ?: 0)
                        .put("model_affected_frames", repair?.modelAffectedFrameCount ?: 0)
                        .put("remaining_incomplete_frames", repair?.remainingIncompleteFrames ?: 0)
                        .put("imputed_by_joint", JSONObject(repair?.imputedByJoint ?: emptyMap<String, Int>()))
                        .put("longest_imputed_gap_frames_by_joint", JSONObject(repair?.longestImputedGapFramesByJoint ?: emptyMap<String, Int>()))
                    val record = JSONObject()
                        .put("assessment_id", assessment.id)
                        .put("assessment_name", assessment.name)
                        .put("task_type", assessment.taskType.name)
                        .put("worker_reference", assessment.workerReference)
                        .put("project_or_site", assessment.projectOrSite)
                        .put("analysis_schema_version", result.schemaVersion)
                        .put("provenance_status", if (provenance == null) "not_recorded_legacy_result" else "recorded_at_analysis")
                        .put("pipeline_id", provenance?.pipelineId ?: JSONObject.NULL)
                        .put("configuration_sha256", provenance?.configurationSha256 ?: JSONObject.NULL)
                        .put("posemamba_model_sha256", provenance?.modelSha256?.get("posemamba") ?: JSONObject.NULL)
                        .put("source_video_sha256", provenance?.sourceVideoSha256 ?: JSONObject.NULL)
                        .put("analysis_provenance", provenanceJson ?: JSONObject.NULL)
                        .put("processed_at_epoch_ms", result.processingCompletedAtEpochMs)
                        .put("source_video_duration_ms", assessment.video.durationMs)
                        .put("source_video_width_px", assessment.video.widthPx)
                        .put("source_video_height_px", assessment.video.heightPx)
                        .put("source_video_bytes", assessment.video.fileSizeBytes)
                        .put("processing_duration_ms", result.processingCompletedAtEpochMs - result.processingStartedAtEpochMs)
                        .put("video_duration_ms", summary.videoDurationMs)
                        .put("total_frames", summary.totalFrames)
                        .put("scored_frames", summary.scoredFrames)
                        .put("unscored_frames", summary.unscoredFrames)
                        .put("scored_duration_ms", summary.scoredDurationMs)
                        .put("unscored_duration_ms", summary.unscoredDurationMs)
                        .put("pose_coverage_percent", result.coveragePercent.toDouble())
                        .put("minimum_reba", summary.minimumScore)
                        .put("minimum_frame_index_zero_based", summary.minimumFrameIndex)
                        .put("minimum_timestamp_ms", summary.minimumTimestampMs)
                        .put("maximum_reba", summary.maximumScore)
                        .put("maximum_frame_index_zero_based", summary.maximumFrameIndex)
                        .put("maximum_timestamp_ms", summary.maximumTimestampMs)
                        .put("time_weighted_mean_reba", summary.timeWeightedMeanScore)
                        .put("risk_duration", risk)
                        .put("scores_conditional", geometryUnreliable || (repair?.imputedFrameCount ?: 0) > 0 ||
                            result.warnings.any { it.contains("incomplete", true) || it.contains("Unsupported REBA components:") } ||
                            (!repetitionValidatedForTask && activity.repetitionStatus == "positive"))
                        .put("pose_2d_repair", repairJson)
                        .put("load_kg", if (assessment.loadIsCategory) JSONObject.NULL else assessment.loadKg)
                        .put("load_category", if (assessment.loadIsCategory) loadCategoryLabel(assessment.loadKg) else JSONObject.NULL)
                        .put("load_scoring_reference_kg", assessment.loadKg)
                        .put("sudden_force_manual", assessment.suddenForce)
                        .put("coupling", assessment.coupling.name)
                        .put("activity_score", result.reba.activityModifier)
                        .put("activity_fully_evaluated", activity.fullyEvaluated)
                        .put("static_status", activity.staticStatus)
                        .put("repetition_status", activity.repetitionStatus)
                        .put("repetition_validated_for_task", repetitionValidatedForTask)
                        .put("repetition_interpretation", if (repetitionValidatedForTask) "lifting_evaluation" else "exploratory_unvalidated_task")
                        .put("repetition_count", activity.repetitionCount)
                        .put("repetition_count_unreliable", geometryUnreliable)
                        .put("repetition_reason", activity.repetitionReason)
                        .put("repetition_channel", activity.repetitionChannel)
                        .put("repetition_rate_per_minute", activity.repetitionRatePerMinute)
                        .put("repetition_rule", "minimum 4 cycles AND >=4 cycles per full video minute")
                        .put("activity_scope", "trial-level modifier applied to every scored frame; event timestamps are reported separately")
                        .put("rapid_motion_status", activity.rapidMotionStatus)
                        .put("warnings", jsonList(result.warnings))
                        .put("processing_errors", jsonList(result.processingErrors))
                        .put("input_quality", JSONArray().also { checks ->
                            result.inputQualityChecks.forEach {
                                checks.put(JSONObject().put("description", it.description).put("passed", it.passed))
                            }
                        })
                        .put("time_method", "Each frame represents time until the next output timestamp; the last represents 1/60 s. Unscored frames remain unscored. Percentages use entire video duration.")
                    writer.write(record.toString(2))
                }
                entry("frames.csv") { writer ->
                    val angleNames = result.frames.flatMap { it.jointAngles.keys }.distinct().sorted()
                    writer.write((listOf("frame_index_zero_based", "timestamp_us", "duration_us", "scored", "score_a", "score_b", "table_c", "activity_score", "final_reba", "risk_level", "neck", "trunk", "legs", "upper_arm_r", "upper_arm_l", "lower_arm_r", "lower_arm_l", "wrist_r", "wrist_l", "load", "fully_evaluated", "invalid_reasons", "imputed_joint_mask", "imputed_joint_names", "model_input_affected_by_repair") + angleNames).joinToString(",") { csv(it) } + "\n")
                    timeline.forEachIndexed { index, frame ->
                        val mask = result.pose2DRepair?.imputedMaskByFrame?.getOrNull(index) ?: 0
                        val imputedNames = (0 until 21).filter { mask and (1 shl it) != 0 }
                            .joinToString(";") { RtmPoseCanonical21.jointNames[it] }
                        val fields: List<Any?> = listOf(frame.frameIndex, frame.timestampUs, frame.durationUs,
                            frame.valid, frame.scoreA, frame.scoreB, frame.tableCScore,
                            frame.activityScore, frame.finalScore, frame.riskLevel?.name,
                            frame.neckScore, frame.trunkScore, frame.legsScore,
                            frame.upperArmRScore, frame.upperArmLScore,
                            frame.lowerArmRScore, frame.lowerArmLScore,
                            frame.wristRScore, frame.wristLScore, frame.loadScore,
                            frame.fullyEvaluated, frame.invalidReasons.joinToString(";"),
                            mask, imputedNames,
                            result.pose2DRepair?.modelInputAffectedByFrame?.getOrNull(index) ?: false) +
                            angleNames.map { result.frames[index].jointAngles[it] }
                        writer.write(fields.joinToString(",") { csv(it) } + "\n")
                    }
                }
                entry("joints.csv") { writer ->
                    writer.write("frame_index_zero_based,timestamp_us,joint_index,joint_name,space,x,y,z,confidence,valid\n")
                    result.frames.forEachIndexed { index, frame ->
                        listOf("2d" to frame.keypoints2D, "3d" to frame.pose3D).forEach { (space, joints) ->
                            joints.forEachIndexed { jointIndex, joint ->
                                writer.write(listOf(index, timeline[index].timestampUs, jointIndex,
                                    RtmPoseCanonical21.jointNames[jointIndex], space,
                                    joint.x, joint.y, joint.z, joint.confidence, joint.valid)
                                    .joinToString(",") { csv(it) } + "\n")
                            }
                        }
                    }
                }
                entry("activity_events.csv") { writer ->
                    writer.write("type,start_ms,end_ms,region\n")
                    result.activity.staticIntervals.forEach { writer.write("static,${it.startMs},${it.endMs},${it.region.name}\n") }
                    result.activity.repetitionEvents.forEach { writer.write("repetition,${it.timestampMs},${it.timestampMs},${it.region.name}\n") }
                    result.activity.rapidMotionEvents.forEach { writer.write("rapid_motion,${it.timestampMs},${it.timestampMs},${it.region.name}\n") }
                }
            }
            if (file.exists()) require(file.delete()) { "Cannot replace previous case study export." }
            require(temporary.renameTo(file)) { "Cannot finalize case study export." }
            return file
        } finally {
            temporary.delete()
        }
    }

    private fun csv(value: Any?): String {
        val raw = when (value) {
            null -> ""
            is Float -> if (value.isFinite()) String.format(Locale.US, "%.6f", value) else ""
            is Double -> if (value.isFinite()) String.format(Locale.US, "%.6f", value) else ""
            else -> value.toString()
        }
        return if (raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"${raw.replace("\"", "\"\"")}\"" else raw
    }

    fun shareUriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
