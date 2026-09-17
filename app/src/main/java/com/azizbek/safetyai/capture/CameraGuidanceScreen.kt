package com.azizbek.safetyai.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.VideoStable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.azizbek.safetyai.ui.components.SafetyPrimaryButton
import com.azizbek.safetyai.ui.components.StepProgressBar
import com.azizbek.safetyai.ui.theme.SafetyAITheme

private data class GuidanceItem(val icon: ImageVector, val text: String)

private val guidanceItems = listOf(
    GuidanceItem(Icons.Filled.PersonPin, "Keep the worker's full body visible"),
    GuidanceItem(Icons.Filled.VideoStable, "Keep the camera stable"),
    GuidanceItem(Icons.Filled.Accessibility, "Record one primary worker"),
    GuidanceItem(Icons.Filled.CenterFocusStrong, "Keep the task and handled object visible"),
    GuidanceItem(Icons.Filled.LightMode, "Use adequate lighting")
)

@Composable
fun CameraGuidanceScreen(onBack: () -> Unit, onOpenCamera: () -> Unit) {
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(modifier = Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Column {
                            Text(
                                text = "Camera setup",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text("Step 3 of 5", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    StepProgressBar(currentStep = 3, totalSteps = 5, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SafetyPrimaryButton(text = "Open camera", onClick = onOpenCamera, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("Back", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Before you record, keep these in mind. Following this guidance improves the video's usefulness — it doesn't guarantee analysis results.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            guidanceItems.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraGuidanceScreenPreview() {
    SafetyAITheme { CameraGuidanceScreen(onBack = {}, onOpenCamera = {}) }
}

@Preview(showBackground = true)
@Composable
private fun CameraGuidanceScreenDarkPreview() {
    SafetyAITheme(darkTheme = true) { CameraGuidanceScreen(onBack = {}, onOpenCamera = {}) }
}
