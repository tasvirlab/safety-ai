package com.azizbek.safetyai.detail

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.azizbek.safetyai.analysis.SafetyAnalysisEngine
import com.azizbek.safetyai.assessment.label
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.label
import com.azizbek.safetyai.ui.components.SafetyOutlinedButton
import com.azizbek.safetyai.ui.components.StatusChip
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssessmentDetailScreen(
    repository: AssessmentRepository,
    analysisEngine: SafetyAnalysisEngine,
    assessmentId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onReplaceVideo: (String) -> Unit,
    onDeleted: () -> Unit,
    onViewReport: (String) -> Unit,
    viewModel: AssessmentDetailViewModel = viewModel(
        factory = AssessmentDetailViewModel.factory(repository, analysisEngine, assessmentId)
    )
) {
    val assessment by viewModel.assessment.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnavailableDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(modifier = Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Text("Assessment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    ) { innerPadding ->
        val current = assessment
        if (current == null) {
            Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                DetailVideoPlayer(filePath = current.video.filePath)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(current.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
                StatusChip(status = current.status)

                DetailSection(
                    title = "Task",
                    rows = listOfNotNull(
                        "Task type" to (current.customTaskDescription ?: current.taskType.label()),
                        current.projectOrSite?.let { "Project or site" to it },
                        current.workerReference?.let { "Worker reference" to it },
                        "Created" to formatDate(current.createdAtEpochMs)
                    )
                )
                DetailSection(
                    title = "Load and grip",
                    rows = listOf(
                        "Load handled" to "${formatLoad(current.loadKg)} kg",
                        "Sudden or rapid force" to if (current.suddenForce) "Yes" else "No",
                        "Grip and coupling" to current.coupling.label()
                    )
                )
                DetailSection(
                    title = "Video",
                    rows = listOf(
                        "Duration" to formatDuration(current.video.durationMs),
                        "Resolution" to if (current.video.widthPx != null && current.video.heightPx != null) {
                            "${current.video.widthPx}×${current.video.heightPx}"
                        } else "Unknown",
                        "File size" to formatFileSize(current.video.fileSizeBytes)
                    )
                )

                Button(
                    onClick = {
                        if (viewModel.analysisEngineAvailable) {
                            // No build of this app ships a validated engine yet — nothing to wire here.
                        } else {
                            viewModel.requestAnalysis()
                            showUnavailableDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Analyze assessment") }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SafetyOutlinedButton(text = "Edit metadata", onClick = { onEdit(current.id) }, modifier = Modifier.weight(1f))
                    SafetyOutlinedButton(text = "Replace video", onClick = { onReplaceVideo(current.id) }, modifier = Modifier.weight(1f))
                }

                if (current.status == AssessmentStatus.COMPLETED) {
                    SafetyOutlinedButton(text = "View report", onClick = { onViewReport(current.id) }, modifier = Modifier.fillMaxWidth())
                }

                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete assessment", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this assessment?") },
            text = { Text("This assessment and its saved video will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete(onDeleted) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        )
    }

    if (showUnavailableDialog) {
        AlertDialog(
            onDismissRequest = { showUnavailableDialog = false },
            title = { Text("Analysis engine unavailable") },
            text = { Text("This build does not yet include the validated Safety AI analysis engine. The assessment and video have been saved.") },
            confirmButton = {
                TextButton(onClick = { showUnavailableDialog = false }) {
                    Text("Done", color = MaterialTheme.colorScheme.onBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnavailableDialog = false }) {
                    Text("View assessment", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        )
    }
}

@Composable
private fun DetailVideoPlayer(filePath: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> PlayerView(ctx).apply { this.player = player } }
        )
    }
}

@Composable
private fun DetailSection(title: String, rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

private fun formatDate(epochMs: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(epochMs))
private fun formatLoad(loadKg: Double): String = if (loadKg == loadKg.toLong().toDouble()) loadKg.toLong().toString() else loadKg.toString()
private fun formatDuration(ms: Long?): String {
    if (ms == null) return "Unknown"
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}
private fun formatFileSize(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
