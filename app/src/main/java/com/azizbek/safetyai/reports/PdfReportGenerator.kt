package com.azizbek.safetyai.reports

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import com.azizbek.safetyai.assessment.label
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
 * Renders a one-page PDF report using the platform's own `PdfDocument` — no
 * PDF library needed. Accepts only a [Assessment] that is actually
 * [AssessmentStatus.COMPLETED] with its matching [SafetyAnalysisResult];
 * everything else is refused rather than producing a misleading document.
 */
object PdfReportGenerator {

    fun generate(context: Context, assessment: Assessment, result: SafetyAnalysisResult): File {
        require(assessment.status == AssessmentStatus.COMPLETED) {
            "Refusing to generate a report for a non-completed assessment (status=${assessment.status})"
        }

        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val headingPaint = Paint().apply { textSize = 13f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 11f }

        var y = 40f
        fun line(text: String, paint: Paint = bodyPaint, gap: Float = 18f) {
            canvas.drawText(text, 40f, y, paint)
            y += gap
        }

        line("Safety AI — Ergonomic Assessment Report", titlePaint, 28f)
        line("Assessment: ${assessment.name}", headingPaint, 20f)
        assessment.projectOrSite?.let { line("Project or site: $it") }
        assessment.workerReference?.let { line("Worker reference: $it") }
        line("Task type: ${assessment.customTaskDescription ?: assessment.taskType.label()}")
        line("Processed: ${DateFormat.getDateInstance().format(Date(result.processingCompletedAtEpochMs))}")
        y += 8f

        line("Overall ergonomic risk: ${result.overallRiskLevel}", headingPaint, 20f)
        line("Final REBA score: ${result.reba.finalScore}")
        y += 8f

        line("Body-region findings", headingPaint, 20f)
        result.bodyRegionFindings.forEach { line("${it.region}: ${it.riskLevel} — ${it.postureFinding}") }
        y += 8f

        line("Activity findings", headingPaint, 20f)
        line("Static intervals: ${result.activity.staticIntervals.size}")
        line("Repetitions: ${result.activity.repetitionCount}")
        line("Rapid movement: ${if (result.activity.rapidMotionDetected) "Detected" else "Not detected"}")
        y += 8f

        line("Highest-risk moment: ${result.highestRiskTimestampMs} ms", headingPaint, 20f)

        line("Recommendations", headingPaint, 20f)
        result.recommendations.forEach { line("• $it") }
        y += 8f

        line("Schema version: ${result.schemaVersion}", bodyPaint, 16f)
        y += 12f
        line(SCOPE_STATEMENT, bodyPaint, 16f)

        document.finishPage(page)

        val outputDir = File(context.cacheDir, "shared_reports").apply { mkdirs() }
        val outputFile = File(outputDir, "safety_ai_report_${assessment.id}.pdf")
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
        return outputFile
    }

    /** A [android.net.Uri] safe to hand to a share intent — never a raw file path. */
    fun shareUriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
