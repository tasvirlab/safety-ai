package com.azizbek.safetyai.analysis

import com.azizbek.safetyai.assessment.CouplingQuality

/**
 * Versioned result schema for the future validated engine (RTMPose /
 * PoseMamba / REBA / Activity Layer). Nothing in this build produces one of
 * these outside previews and tests — see [UnavailableSafetyAnalysisEngine].
 */

/** Bumped whenever a shipped [SafetyAnalysisResult]'s shape changes. */
const val ANALYSIS_SCHEMA_VERSION = 1

enum class BodyRegion { NECK, TRUNK, LEGS, UPPER_ARMS, LOWER_ARMS, WRISTS }

enum class RiskLevel { LOW, MEDIUM, HIGH, VERY_HIGH }

data class Joint3D(val x: Float, val y: Float, val z: Float, val confidence: Float, val valid: Boolean)

/** One analyzed video frame. 21-joint 3D pose per [com.azizbek.safetyai analysis contract]; 2D keypoints kept alongside for overlay rendering. */
data class FrameAnalysis(
    val timestampMs: Long,
    val keypoints2D: List<Joint3D>,
    val pose3D: List<Joint3D>,
    val jointAngles: Map<String, Float>
)

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
)

data class BodyRegionFinding(
    val region: BodyRegion,
    val riskLevel: RiskLevel,
    val postureFinding: String,
    val angleDegrees: Float,
    val highestRiskTimestampMs: Long
)

data class StaticPostureInterval(val startMs: Long, val endMs: Long, val region: BodyRegion)
data class RepetitionEvent(val timestampMs: Long, val region: BodyRegion)
data class RapidMotionEvent(val timestampMs: Long, val region: BodyRegion)

data class ActivityFindings(
    val staticIntervals: List<StaticPostureInterval>,
    val repetitionEvents: List<RepetitionEvent>,
    val repetitionCount: Int,
    val rapidMotionEvents: List<RapidMotionEvent>,
    val rapidMotionDetected: Boolean
)

data class InputQualityCheck(val description: String, val passed: Boolean)

/** A completed, real analysis. Never populated by [UnavailableSafetyAnalysisEngine]. */
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
    val processingErrors: List<String>
)

/** Context handed to the engine alongside the video — the load/force/coupling inputs collected in Step 1. */
data class AnalysisInputContext(
    val loadKg: Double,
    val suddenForce: Boolean,
    val coupling: CouplingQuality
)

sealed interface AnalysisProgress {
    data class Running(val stage: String, val fractionComplete: Float) : AnalysisProgress
    data class Failed(val reason: String) : AnalysisProgress
    data class Completed(val result: SafetyAnalysisResult) : AnalysisProgress
}
