package com.azizbek.safetyai.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.label
import com.azizbek.safetyai.ui.theme.LocalSafetyDarkTheme
import com.azizbek.safetyai.ui.theme.RiskLowInkDark
import com.azizbek.safetyai.ui.theme.RiskLowInkLight

/** A workflow-state chip (Draft/Ready/Analyzing/…) — never a risk-level indicator, so it stays on brand-neutral colors. */
@Composable
fun StatusChip(status: AssessmentStatus) {
    // Container + its matching "on*Container" foreground everywhere a yellow
    // fill is involved — primary itself is too light to read as text.
    val completedInk = if (LocalSafetyDarkTheme.current) RiskLowInkDark else RiskLowInkLight
    val (container, content) = when (status) {
        AssessmentStatus.DRAFT -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        AssessmentStatus.READY_FOR_ANALYSIS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        AssessmentStatus.ANALYZING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        AssessmentStatus.COMPLETED -> completedInk.copy(alpha = 0.14f) to completedInk
        AssessmentStatus.ANALYSIS_FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        AssessmentStatus.MODEL_UNAVAILABLE -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
