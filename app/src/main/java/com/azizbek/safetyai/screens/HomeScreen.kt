package com.azizbek.safetyai.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.azizbek.safetyai.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.ui.components.AssessmentCard
import com.azizbek.safetyai.ui.components.AssessmentSourceSheet
import com.azizbek.safetyai.ui.components.SafetyCard
import com.azizbek.safetyai.ui.components.SafetyPrimaryButton
import com.azizbek.safetyai.ui.theme.SafetyAITheme
import kotlinx.coroutines.launch

/**
 * Home: one heading, one sentence, one primary action, up to three recent
 * assessments. Deliberately minimal — every other action on this screen
 * duplicated something reachable one tap away, so it was removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: AssessmentRepository? = null,
    onRecordTask: () -> Unit = {},
    onUploadVideo: () -> Unit = {},
    onViewAllAssessments: () -> Unit = {},
    onOpenAssessment: (String) -> Unit = {}
) {
    var showAssessmentSheet by remember { mutableStateOf(false) }
    // Preview builds pass no repository — an empty list keeps the empty state visible there.
    val recentAssessments: List<Assessment> = if (repository != null) {
        val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
        val state by viewModel.recentAssessments.collectAsStateWithLifecycle()
        state
    } else {
        emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        item {
            Image(
                painter = painterResource(R.drawable.home_rebar_pose_phone),
                contentDescription = "Construction worker tying rebar with a posture overlay and a phone on a tripod",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(16.dp))
            )
        }
        item { HomeHeading(onStartAssessment = { showAssessmentSheet = true }) }
        item {
            RecentAssessmentsSection(
                assessments = recentAssessments,
                onViewAllAssessments = onViewAllAssessments,
                onOpenAssessment = onOpenAssessment,
                onStartAssessment = { showAssessmentSheet = true }
            )
        }
        item { CaptureTipCard() }
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
private fun HomeHeading(onStartAssessment: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Assess construction work posture",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Record one worker or upload a video to review posture-related ergonomic risk.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SafetyPrimaryButton(
            text = "Record or upload task",
            onClick = onStartAssessment,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RecentAssessmentsSection(
    assessments: List<Assessment>,
    onViewAllAssessments: () -> Unit,
    onOpenAssessment: (String) -> Unit,
    onStartAssessment: () -> Unit
) {
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
            if (assessments.isNotEmpty()) {
                TextButton(onClick = onViewAllAssessments) {
                    Text("View all", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (assessments.isEmpty()) {
            SafetyCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                                text = "Record or upload a task video to begin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = onStartAssessment) {
                        Text("Start assessment", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                assessments.forEach { assessment ->
                    AssessmentCard(assessment = assessment, onClick = { onOpenAssessment(assessment.id) })
                }
            }
        }
    }
}

@Composable
private fun CaptureTipCard() {
    SafetyCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Capture tip",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Record one worker from the side. Keep the full body, hands and feet visible throughout the task.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Preview(showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
    SafetyAITheme(darkTheme = true) {
        HomeScreen()
    }
}
