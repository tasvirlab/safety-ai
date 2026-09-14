package com.azizbek.safetyai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.azizbek.safetyai.ui.components.AssessmentSourceSheet
import com.azizbek.safetyai.ui.components.SafetyCard
import com.azizbek.safetyai.ui.components.SafetyPrimaryButton
import com.azizbek.safetyai.ui.theme.LocalSafetyDarkTheme
import com.azizbek.safetyai.ui.theme.SafetyAITheme
import com.azizbek.safetyai.ui.theme.SafetyDarkPrimaryCard
import com.azizbek.safetyai.ui.theme.SafetyNavy
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRecordTask: () -> Unit = {},
    onUploadVideo: () -> Unit = {},
    onViewAllAssessments: () -> Unit = {}
) {
    var showAssessmentSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { WelcomeSection() }
        item { PrimaryAssessmentCard(onStartAssessment = { showAssessmentSheet = true }) }
        item {
            AssessmentSourceActions(
                onRecordTask = onRecordTask,
                onUploadVideo = onUploadVideo
            )
        }
        item { RecentAssessmentsSection(onViewAllAssessments = onViewAllAssessments) }
        item { HowItWorksSection() }
    }

    if (showAssessmentSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()

        fun dismiss(after: () -> Unit) {
            scope.launch {
                sheetState.hide()
            }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    showAssessmentSheet = false
                    after()
                }
            }
        }

        AssessmentSourceSheet(
            sheetState = sheetState,
            onDismiss = { dismiss {} },
            onRecordTask = { dismiss(onRecordTask) },
            onUploadVideo = { dismiss(onUploadVideo) }
        )
    }
}

@Composable
private fun WelcomeSection() {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Movement, made visible.",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Ready to assess a task?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Capture a construction task and review posture, movement " +
                "patterns, and ergonomic risk.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun PrimaryAssessmentCard(onStartAssessment: () -> Unit) {
    // The hero card is a fixed brand color per theme (not a semantic
    // ColorScheme role) so it keeps its dominance next to regular cards.
    val heroColor = if (LocalSafetyDarkTheme.current) SafetyDarkPrimaryCard else SafetyNavy
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = heroColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Start a movement assessment",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Record a new task or select an existing video for analysis.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
            SafetyPrimaryButton(
                text = "Start assessment",
                onClick = onStartAssessment,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AssessmentSourceActions(
    onRecordTask: () -> Unit,
    onUploadVideo: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Below this width, two equal-height cards would compress their
        // text too tightly (especially at larger font scales) — stack them
        // instead of clipping or shrinking content.
        if (maxWidth < 340.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SourceActionCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.CameraAlt,
                    title = "Record task",
                    supportingText = "Use the device camera",
                    onClick = onRecordTask
                )
                SourceActionCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.VideoLibrary,
                    title = "Upload video",
                    supportingText = "Choose an existing recording",
                    onClick = onUploadVideo
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SourceActionCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    icon = Icons.Filled.CameraAlt,
                    title = "Record task",
                    supportingText = "Use the device camera",
                    onClick = onRecordTask
                )
                SourceActionCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    icon = Icons.Filled.VideoLibrary,
                    title = "Upload video",
                    supportingText = "Choose an existing recording",
                    onClick = onUploadVideo
                )
            }
        }
    }
}

@Composable
private fun SourceActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    supportingText: String,
    onClick: () -> Unit
) {
    SafetyCard(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClickLabel = title, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentAssessmentsSection(onViewAllAssessments: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent assessments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onViewAllAssessments) {
                Text("View all", color = MaterialTheme.colorScheme.primary)
            }
        }
        SafetyCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "No assessments yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Your completed assessments will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class OnboardingStep(val number: Int, val title: String, val description: String)

private val onboardingSteps = listOf(
    OnboardingStep(1, "Capture the task", "Record or upload the work activity."),
    OnboardingStep(2, "Review movement", "See posture and ergonomic risk across the task."),
    OnboardingStep(3, "Act on feedback", "Use clear findings to improve task execution.")
)

@Composable
private fun HowItWorksSection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "How it works",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        onboardingSteps.forEachIndexed { index, step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = step.number.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (index != onboardingSteps.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 42.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SafetyAITheme {
        HomeScreen()
    }
}
