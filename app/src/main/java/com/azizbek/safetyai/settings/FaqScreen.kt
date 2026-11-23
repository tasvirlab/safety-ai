package com.azizbek.safetyai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val questions = listOf(
    "What does Safety AI measure?" to
        "It estimates posture-related ergonomic risk from one worker's video. The ergonomic report shows REBA scores over time and the time spent at each risk level. It does not assess overall site safety.",
    "How should I record a task?" to
        "Keep one worker's whole body, hands and feet visible. Use a steady phone, preferably from the side, with enough space to see the full movement. Record the task for the planned study duration. A suggested distance is only a guide; the app does not measure camera distance.",
    "What do incomplete and conditional mean?" to
        "Incomplete means some frames could not be scored reliably. Conditional means a result needs caution because some inputs or criteria were estimated, unavailable or outside the evaluated scope. Check the report's coverage and notes; record again if the body was obscured.",
    "How do I read a REBA score?" to
        "Higher scores indicate greater ergonomic concern: 1 negligible, 2–3 low, 4–7 medium, 8–10 high and 11–15 very high. The final score includes the posture score and applicable activity modifiers. A low score is not a guarantee of safety.",
    "Does selecting a task change the model?" to
        "No. The task label identifies what was recorded. It does not activate a separately validated task model. Repetition findings have been evaluated for lifting; they should not be treated as validated for other tasks.",
    "Does roofing assessment include fall risk?" to
        "No. The app assesses posture-related ergonomic risk. It cannot evaluate fall hazards, equipment, site conditions or medical outcomes."
)

@Composable
fun FaqScreen(onBack: () -> Unit) {
    var expanded by remember { mutableIntStateOf(-1) }
    Scaffold(topBar = {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Help and FAQ", style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
            }
        }
    }) { insets ->
        Column(
            modifier = Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Recording and reading your ergonomic report", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            questions.forEachIndexed { index, (question, answer) ->
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded == index) "Expanded" else "Collapsed" }
                                .clickable(role = Role.Button) { expanded = if (expanded == index) -1 else index }
                                .padding(vertical = 16.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(question, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Icon(if (expanded == index) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null)
                        }
                        if (expanded == index) {
                            Text(answer, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
