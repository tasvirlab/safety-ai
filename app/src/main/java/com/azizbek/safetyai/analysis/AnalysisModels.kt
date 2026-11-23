package com.azizbek.safetyai.analysis

import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import java.io.Serializable

/** Versioned result schema produced by the on-device analysis engine. */
const val ANALYSIS_SCHEMA_VERSION = 7

enum class BodyRegion {
    NECK,
    TRUNK,
    LEGS,
    UPPER_ARMS,
    LOWER_ARMS,
    WRISTS
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

data class Joint3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val confidence: Float,
    val valid: Boolean
) : Serializable

data class FrameAnalysis(
    val timestampMs: Long,
    val keypoints2D: List<Joint3D>,
    val pose3D: List<Joint3D>,
    val jointAngles: Map<String, Float>
) : Serializable

/** One entry per output frame; null scores mean that frame was not scored. */
data class FrameRebaResult(
    val frameIndex: Int,
    val timestampUs: Long,
    val durationUs: Long,
    val valid: Boolean,
    val scoreA: Int?,
    val scoreB: Int?,
    val tableCScore: Int?,
    val activityScore: Int,
    val finalScore: Int?,
    val riskLevel: RiskLevel?,
    val neckScore: Int?,
    val trunkScore: Int?,
    val legsScore: Int?,
    val upperArmRScore: Int?,
    val upperArmLScore: Int?,
    val lowerArmRScore: Int?,
    val lowerArmLScore: Int?,
    val wristRScore: Int?,
    val wristLScore: Int?,
    val loadScore: Int?,
    val fullyEvaluated: Boolean,
    val invalidReasons: List<String>
) : Serializable

/** The 21 low-order bits of each mask identify joints interpolated for the model. */
data class Pose2DRepairSummary(
    val method: String,
    val imputedMaskByFrame: IntArray,
    val imputedByJoint: Map<String, Int>,
    val longestImputedGapFramesByJoint: Map<String, Int>,
    val remainingIncompleteFrames: Int,
    /** True if an emitted 3D frame came from a window containing repaired 2D input. */
    val modelInputAffectedByFrame: BooleanArray
) : Serializable {
    val imputedFrameCount: Int
        get() = imputedMaskByFrame.count { it != 0 }
    val modelAffectedFrameCount: Int
        get() = modelInputAffectedByFrame.count { it }
}

data class RebaComponents(
    val neckScore: Int,
    val trunkScore: Int,
    val legsScore: Int,
    val upperArmScore: Int,
    val lowerArmScore: Int,
    val wristScore: Int,
    val activityModifier: Int,
    val finalScore: Int,
    val riskLevel: RiskLevel
) : Serializable

data class BodyRegionFinding(
    val region: BodyRegion,
    val riskLevel: RiskLevel,
    val postureFinding: String,
    val angleDegrees: Float,
    val highestRiskTimestampMs: Long
) : Serializable

data class StaticPostureInterval(
    val startMs: Long,
    val endMs: Long,
    val region: BodyRegion
) : Serializable

data class RepetitionEvent(
    val timestampMs: Long,
    val region: BodyRegion
) : Serializable

data class RapidMotionEvent(
    val timestampMs: Long,
    val region: BodyRegion
) : Serializable

data class ActivityFindings(
    val staticIntervals: List<StaticPostureInterval>,
    val repetitionEvents: List<RepetitionEvent>,
    val repetitionCount: Int,
    val rapidMotionEvents: List<RapidMotionEvent>,
    val rapidMotionDetected: Boolean,
    val staticStatus: String = "unavailable",
    val repetitionStatus: String = "unavailable",
    val rapidMotionStatus: String = "unavailable",
    val staticReason: String? = null,
    val repetitionReason: String? = null,
    val rapidMotionReason: String? = null,
    val repetitionChannel: String? = null,
    val repetitionRatePerMinute: Double? = null,
    val fullyEvaluated: Boolean = false,
    val scoreStatus: String = "conditional_incomplete"
) : Serializable {
    companion object {
        // Keep reading ActivityFindings serialized by the previous app build.
        private const val serialVersionUID: Long = -2113949514190549453L
    }
}

data class InputQualityCheck(
    val description: String,
    val passed: Boolean
) : Serializable

data class SafetyAnalysisResult(
    val schemaVersion: Int = ANALYSIS_SCHEMA_VERSION,
    val assessmentId: String,
    val processingStartedAtEpochMs: Long,
    val processingCompletedAtEpochMs: Long,
    val inputQualityChecks: List<InputQualityCheck>,
    val frames: List<FrameAnalysis>,
    val reba: RebaComponents,
    val bodyRegionFindings: List<BodyRegionFinding>,
    val activity: ActivityFindings,
    val overallRiskLevel: RiskLevel,
    val highestRiskTimestampMs: Long,
    val coveragePercent: Float,
    val recommendations: List<String>,
    val warnings: List<String>,
    val processingErrors: List<String>,
    /** Introduced in schema 4. Null on previously serialized results. */
    val frameRebaResults: List<FrameRebaResult>? = null,
    /** Introduced in schema 5; absent on older saved results. */
    val pose2DRepair: Pose2DRepairSummary? = null,
    /** Analysis-time identity; null on results saved before schema 7. */
    val provenance: PipelineProvenance? = null
) : Serializable {
    companion object {
        // Preserve access to already saved version-1 analysis results.
        private const val serialVersionUID: Long = -5850266546818223683L
    }
}

data class AnalysisInputContext(
    val loadKg: Double,
    val suddenForce: Boolean,
    val coupling: CouplingQuality,
    val taskType: TaskType? = null
)

sealed interface AnalysisProgress {
    data class Running(
        val stage: String,
        val fractionComplete: Float
    ) : AnalysisProgress

    data class Failed(
        val reason: String
    ) : AnalysisProgress

    data class Completed(
        val result: SafetyAnalysisResult
    ) : AnalysisProgress
}
