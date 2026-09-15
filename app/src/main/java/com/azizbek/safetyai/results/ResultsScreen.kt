package com.azizbek.safetyai.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.azizbek.safetyai.analysis.BodyRegion
import com.azizbek.safetyai.analysis.RiskLevel
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import com.azizbek.safetyai.ui.components.RiskChip
import com.azizbek.safetyai.ui.theme.SafetyAITheme
import java.util.Locale

/**
 * Production result composables, ready to receive only a real, completed
 * [SafetyAnalysisResult]. No navigation route in this build reaches this
 * screen — there is no way to obtain a completed result yet — so it is
 * reviewable only through the `@Preview`s below and Compose UI tests.
 */
@Composable
fun ResultsScreen(result: SafetyAnalysisResult) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Overview", "Movement", "Body regions", "Activity")
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        when (tab) {
            0 -> OverviewTab(result)
            1 -> MovementTab()
            2 -> BodyRegionsTab(result)
            else -> ActivityTab(result)
        }
    }
}

@Composable
private fun OverviewTab(result: SafetyAnalysisResult) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RiskChip(level = result.overallRiskLevel)
            Text("REBA ${result.reba.finalScore}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        SectionCard(title = "Analysis coverage") {
            Text("${(result.coveragePercent * 100).toInt()}% of the video was analyzable", style = MaterialTheme.typography.bodyMedium)
            result.inputQualityChecks.filterNot { it.passed }.forEach {
                Text("⚠ ${it.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        SectionCard(title = "Highest-risk moment") {
            Text(formatTimestamp(result.highestRiskTimestampMs), style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(title = "Key findings") {
            result.recommendations.take(3).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        if (result.warnings.isNotEmpty()) {
            SectionCard(title = "Warnings") {
                result.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private val movementViews = listOf("Original", "2D pose", "3D skeleton", "Posture risk")

@Composable
private fun MovementTab() {
    var selected by remember { mutableStateOf(movementViews.first()) }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            movementViews.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selected == label,
                    onClick = { selected = label },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, movementViews.size)
                ) { Text(label) }
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (selected) {
                    "Posture risk" -> "Posture risk map"
                    else -> selected
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "Labelled as posture/ergonomic risk — not measured muscle stress or a medical finding.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BodyRegionsTab(result: SafetyAnalysisResult) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BodyRegion.entries.forEach { region ->
            val finding = result.bodyRegionFindings.firstOrNull { it.region == region }
            SectionCard(title = region.label()) {
                if (finding == null) {
                    Text("No finding for this region", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    RiskChip(level = finding.riskLevel)
                    Text(finding.postureFinding, style = MaterialTheme.typography.bodyMedium)
                    Text("${finding.angleDegrees.toInt()}° · ${formatTimestamp(finding.highestRiskTimestampMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ActivityTab(result: SafetyAnalysisResult) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Static posture") {
            Text("${result.activity.staticIntervals.size} intervals detected", style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(title = "Repetition") {
            Text("${result.activity.repetitionCount} repetitions detected", style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(title = "Rapid movement") {
            Text(
                if (result.activity.rapidMotionDetected) "${result.activity.rapidMotionEvents.size} rapid-movement events detected" else "No rapid movement detected",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScopeAlias.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

private typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

private fun BodyRegion.label(): String = when (this) {
    BodyRegion.NECK -> "Neck"
    BodyRegion.TRUNK -> "Trunk"
    BodyRegion.LEGS -> "Legs"
    BodyRegion.UPPER_ARMS -> "Upper arms"
    BodyRegion.LOWER_ARMS -> "Lower arms"
    BodyRegion.WRISTS -> "Wrists"
}

private fun formatTimestamp(ms: Long): String = String.format(Locale.US, "%02d:%02d", (ms / 1000) / 60, (ms / 1000) % 60)

// ---- Preview-only fixture data: never used by real navigation. ----
private fun previewFixtureResult() = SafetyAnalysisResult(
    assessmentId = "preview",
    processingStartedAtEpochMs = 0,
    processingCompletedAtEpochMs = 1,
    inputQualityChecks = listOf(
        com.azizbek.safetyai.analysis.InputQualityCheck("Worker fully visible", true),
        com.azizbek.safetyai.analysis.InputQualityCheck("Stable framing", false)
    ),
    frames = emptyList(),
    reba = com.azizbek.safetyai.analysis.RebaComponents(2, 3, 1, 2, 1, 2, 1, 7, RiskLevel.HIGH),
    bodyRegionFindings = BodyRegion.entries.map {
        com.azizbek.safetyai.analysis.BodyRegionFinding(it, RiskLevel.MEDIUM, "Sustained forward flexion", 42f, 12_500)
    },
    activity = com.azizbek.safetyai.analysis.ActivityFindings(
        staticIntervals = listOf(com.azizbek.safetyai.analysis.StaticPostureInterval(0, 5000, BodyRegion.TRUNK)),
        repetitionEvents = listOf(com.azizbek.safetyai.analysis.RepetitionEvent(3000, BodyRegion.UPPER_ARMS)),
        repetitionCount = 6,
        rapidMotionEvents = emptyList(),
        rapidMotionDetected = false
    ),
    overallRiskLevel = RiskLevel.HIGH,
    highestRiskTimestampMs = 12_500,
    coveragePercent = 0.92f,
    recommendations = listOf("Reduce trunk flexion during lifting", "Increase task rotation frequency"),
    warnings = listOf("Framing was unstable for part of the video"),
    processingErrors = emptyList()
)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ResultsScreenPreview() {
    SafetyAITheme { ResultsScreen(previewFixtureResult()) }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ResultsScreenDarkPreview() {
    SafetyAITheme(darkTheme = true) { ResultsScreen(previewFixtureResult()) }
}
