package com.azizbek.safetyai.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azizbek.safetyai.analysis.RiskLevel

// Reserved risk-communication colors — used only alongside a text label, per
// "never communicate risk using color alone". Not part of the app's regular
// Material ColorScheme roles.
private val RiskLow = Color(0xFF2E7D32)
private val RiskMedium = Color(0xFFB07A00) // darker than the raw warning yellow for AA text contrast
private val RiskHigh = Color(0xFFE06A1A)
private val RiskVeryHigh = Color(0xFFC62828)

private fun RiskLevel.color(): Color = when (this) {
    RiskLevel.LOW -> RiskLow
    RiskLevel.MEDIUM -> RiskMedium
    RiskLevel.HIGH -> RiskHigh
    RiskLevel.VERY_HIGH -> RiskVeryHigh
}

private fun RiskLevel.label(): String = when (this) {
    RiskLevel.LOW -> "Low risk"
    RiskLevel.MEDIUM -> "Medium risk"
    RiskLevel.HIGH -> "High risk"
    RiskLevel.VERY_HIGH -> "Very high risk"
}

/** Always pairs the risk color with its text label — color alone never carries the meaning. */
@Composable
fun RiskChip(level: RiskLevel) {
    Surface(shape = RoundedCornerShape(8.dp), color = level.color().copy(alpha = 0.16f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(level.label(), color = level.color(), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        }
    }
}
