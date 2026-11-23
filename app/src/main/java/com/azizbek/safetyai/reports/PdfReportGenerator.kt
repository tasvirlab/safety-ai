package com.azizbek.safetyai.reports

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import com.azizbek.safetyai.analysis.caseStudySummary
import com.azizbek.safetyai.assessment.label
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

private const val PAGE_WIDTH = 595 // A4 at 72dpi
private const val PAGE_HEIGHT = 842
private const val SCOPE_STATEMENT = "Safety AI supports ergonomic task assessment and does not provide a medical diagnosis."

/**
 * Renders a paginated PDF report using the platform's own `PdfDocument`.
 * Accepts only an [Assessment] that is actually
 * [AssessmentStatus.COMPLETED] with its matching [SafetyAnalysisResult];
 * everything else is refused rather than producing a misleading document.
 */
object PdfReportGenerator {

    fun generate(context: Context, assessment: Assessment, result: SafetyAnalysisResult): File {
        require(assessment.status == AssessmentStatus.COMPLETED) {
            "Refusing to generate a report for a non-completed assessment (status=${assessment.status})"
        }
        require(result.assessmentId == assessment.id) {
            "Refusing to generate a report for another assessment."
        }

        val document = PdfDocument()
        try {
            var pageNumber = 1
            var page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; isFakeBoldText = true }
            val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; isFakeBoldText = true }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }

            var y = 40f
            fun nextPage() {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                y = 40f
            }

            fun line(text: String, paint: Paint = bodyPaint, gap: Float = 18f) {
                var current = ""
                // Draw within the printable width; long warnings can span several pages.
                for (word in text.split(' ')) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (current.isNotEmpty() && paint.measureText(candidate) > PAGE_WIDTH - 80f) {
                        if (y + gap > PAGE_HEIGHT - 40f) nextPage()
                        page.canvas.drawText(current, 40f, y, paint)
                        y += gap
                        current = word
                    } else {
                        current = candidate
                    }
                }
                if (current.isNotEmpty()) {
                    if (y + gap > PAGE_HEIGHT - 40f) nextPage()
                    page.canvas.drawText(current, 40f, y, paint)
                    y += gap
                }
            }

            line("Safety AI - Ergonomic Assessment Report", titlePaint, 28f)
            line("Assessment: ${assessment.name}", headingPaint, 20f)
            assessment.projectOrSite?.let { line("Project or site: $it") }
            assessment.workerReference?.let { line("Worker reference: $it") }
            line("Task type: ${assessment.customTaskDescription ?: assessment.taskType.label()}")
            line("Processed: ${DateFormat.getDateInstance().format(Date(result.processingCompletedAtEpochMs))}")
            y += 8f

            val conditional = result.schemaVersion < 2 || !result.activity.fullyEvaluated ||
                result.warnings.any { it.startsWith("Unsupported REBA components:") } ||
                (result.pose2DRepair?.imputedFrameCount ?: 0) > 0 ||
                (assessment.taskType != TaskType.LIFTING &&
                    assessment.taskType != TaskType.MATERIAL_LIFTING &&
                    result.activity.repetitionStatus == "positive")
            val geometryUnreliable = result.inputQualityChecks.any {
                !it.passed && it.description == "2D and 3D shoulder geometry is reliable"
            }
            if (geometryUnreliable) {
                line("Pose tracking was unreliable. Re-record before using the 3D pose or REBA scores.", headingPaint, 20f)
                line("Numeric scores are retained in the case study ZIP for diagnostic review only.")
            } else {
                line(if (conditional) "Conditional ergonomic risk: ${result.overallRiskLevel}" else "Overall ergonomic risk: ${result.overallRiskLevel}", headingPaint, 20f)
                line("${if (conditional) "Conditional" else "Final"} REBA score: ${result.reba.finalScore}")
                line("Table C plus confirmed Activity Score: ${result.reba.activityModifier}")
            }
            y += 8f

            val study = result.caseStudySummary()
            if (geometryUnreliable) {
                line("Case study scores withheld because pose geometry was unreliable.")
                y += 8f
            } else if (study != null) {
                line("Case study summary (time-weighted, conditional scores)", headingPaint, 20f)
                line("Video duration: ${"%.2f".format(study.videoDurationMs / 1000.0)} s; scored: ${study.scoredFrames}/${study.totalFrames} frames")
                line("Minimum REBA: ${study.minimumScore} at frame ${study.minimumFrameIndex + 1} (${"%.3f".format(study.minimumTimestampMs / 1000.0)} s)")
                line("Maximum REBA: ${study.maximumScore} at frame ${study.maximumFrameIndex + 1} (${"%.3f".format(study.maximumTimestampMs / 1000.0)} s)")
                line("Time-weighted mean REBA: ${"%.2f".format(study.timeWeightedMeanScore)}")
                study.riskTimes.forEach { risk ->
                    line("${risk.level.name.replace('_', ' ')}: ${"%.2f".format(risk.durationMs / 1000.0)} s (${"%.1f".format(risk.percentOfVideo)}% of video), ${risk.frames} frames")
                }
                line("Unscored: ${"%.2f".format(study.unscoredDurationMs / 1000.0)} s, ${study.unscoredFrames} frames")
                line("Per-frame scores, angles and 3D joints: case study ZIP export.")
                result.pose2DRepair?.let { repair ->
                    line("2D input repair: ${repair.imputedFrameCount}/${study.totalFrames} frames; " +
                        "${repair.remainingIncompleteFrames} remain incomplete; " +
                        "${repair.modelAffectedFrameCount} model output frames affected.")
                    line("Repaired joints: ${repair.imputedByJoint.entries.joinToString { "${it.key}=${it.value}" }}")
                    if (repair.imputedFrameCount > 0) {
                        line("Scores affected by 2D repair are estimates; see frames.csv for masks.")
                    }
                }
                y += 8f
            } else {
                line("Per-frame REBA and risk-duration data were not stored for this earlier report.")
                y += 8f
            }

            if (result.bodyRegionFindings.isNotEmpty()) {
                line("Body-region findings", headingPaint, 20f)
                result.bodyRegionFindings.forEach { line("${it.region}: ${it.riskLevel} - ${it.postureFinding}") }
                y += 8f
            }

            line("Activity findings", headingPaint, 20f)
            if (result.schemaVersion < 2) {
                line("Older report: Activity Score was 0; activity criteria were not assessed.")
            } else {
                val activity = result.activity
                fun status(value: String): String = when (value) {
                    "positive" -> "detected"
                    "negative" -> "not detected"
                    "insufficient" -> "insufficient evidence"
                    "unavailable" -> "not assessed"
                    else -> "unknown"
                }
                line("Activity criteria: ${if (activity.fullyEvaluated) "complete" else "conditional / incomplete"}")
                line("Static hold: ${status(activity.staticStatus)}; intervals: ${activity.staticIntervals.size}")
                val repetitionCountText = if (activity.repetitionReason == "pose_geometry_unreliable") {
                    "raw candidate cycles (unreliable): ${activity.repetitionCount}"
                } else {
                    "cycles: ${activity.repetitionCount}"
                }
                line("Repetition: ${status(activity.repetitionStatus)}; $repetitionCountText")
                if (assessment.taskType != TaskType.LIFTING && assessment.taskType != TaskType.MATERIAL_LIFTING) {
                    line("Repetition is exploratory for this task. Its count and any REBA contribution have not been validated for this task.")
                }
                line("Repetition rule: ${if (result.schemaVersion >= 3) "at least 4 cycles and 4/min" else "previous rate-only rule (>4/min)"}")
                line("Rapid motion: ${status(activity.rapidMotionStatus)}; events: ${activity.rapidMotionEvents.size}")
                activity.repetitionChannel?.let { line("Repetition channel: $it") }
                activity.repetitionRatePerMinute?.let { line("Repetitions per minute: ${"%.1f".format(it)}") }
                listOfNotNull(activity.staticReason, activity.repetitionReason, activity.rapidMotionReason)
                    .forEach { line("Criterion note: ${it.replace('_', ' ')}") }
                if (!activity.fullyEvaluated) {
                    line("Unassessed criteria add no points. This score is conditional, not a final REBA determination.")
                }
            }
            y += 8f

            if (!geometryUnreliable) line("Highest-risk moment: ${result.highestRiskTimestampMs} ms", headingPaint, 20f)
            if (!geometryUnreliable && result.recommendations.isNotEmpty()) {
                line("Recommendations", headingPaint, 20f)
                result.recommendations.forEach { line("- $it") }
            }
            if (result.warnings.isNotEmpty()) {
                line("Limitations and warnings", headingPaint, 20f)
                result.warnings.forEach { line("- $it") }
            }
            y += 8f
            line("Schema version: ${result.schemaVersion}", bodyPaint, 16f)
            line("Pipeline identity: ${result.provenance?.pipelineId ?: "not recorded in this older result"}", bodyPaint, 16f)
            result.provenance?.let {
                line("Configuration SHA256: ${it.configurationSha256}", bodyPaint, 16f)
                line("Source video SHA256: ${it.sourceVideoSha256}", bodyPaint, 16f)
            }
            line(SCOPE_STATEMENT, bodyPaint, 16f)
            document.finishPage(page)

            val outputDir = File(context.cacheDir, "shared_reports").apply { mkdirs() }
            val outputFile = File(outputDir, "safety_ai_report_${assessment.id}.pdf")
            FileOutputStream(outputFile).use { document.writeTo(it) }
            return outputFile
        } finally {
            document.close()
        }
    }

    /** A [android.net.Uri] safe to hand to a share intent — never a raw file path. */
    fun shareUriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
