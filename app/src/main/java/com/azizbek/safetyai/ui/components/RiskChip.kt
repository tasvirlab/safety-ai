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
import com.azizbek.safetyai.ui.theme.LocalSafetyDarkTheme
import com.azizbek.safetyai.ui.theme.RiskHighInkDark
import com.azizbek.safetyai.ui.theme.RiskHighInkLight
import com.azizbek.safetyai.ui.theme.RiskLowInkDark
import com.azizbek.safetyai.ui.theme.RiskLowInkLight
import com.azizbek.safetyai.ui.theme.RiskMediumInkDark
import com.azizbek.safetyai.ui.theme.RiskMediumInkLight
import com.azizbek.safetyai.ui.theme.RiskVeryHighInkDark
import com.azizbek.safetyai.ui.theme.RiskVeryHighInkLight

/** The theme-safe risk color for text/borders — darker on light surfaces, brighter on dark surfaces so it always clears AA contrast. */
@Composable
private fun RiskLevel.ink(): Color {
    val dark = LocalSafetyDarkTheme.current
    return when (this) {
        RiskLevel.LOW -> if (dark) RiskLowInkDark else RiskLowInkLight
        RiskLevel.MEDIUM -> if (dark) RiskMediumInkDark else RiskMediumInkLight
        RiskLevel.HIGH -> if (dark) RiskHighInkDark else RiskHighInkLight
        RiskLevel.VERY_HIGH -> if (dark) RiskVeryHighInkDark else RiskVeryHighInkLight
    }
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
    val ink = level.ink()
    Surface(shape = RoundedCornerShape(8.dp), color = ink.copy(alpha = 0.14f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(level.label(), color = ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        }
    }
}
