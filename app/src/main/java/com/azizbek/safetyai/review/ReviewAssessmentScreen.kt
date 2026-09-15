package com.azizbek.safetyai.review

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azizbek.safetyai.assessment.AssessmentDraft
import com.azizbek.safetyai.assessment.label
import com.azizbek.safetyai.assessment.sourceLabel
import com.azizbek.safetyai.domain.VideoMetadata
import com.azizbek.safetyai.ui.components.SafetyPrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Step 3 of 3 — the final chance to review before [onSave] persists the assessment. */
@Composable
fun ReviewAssessmentScreen(
    draft: AssessmentDraft,
    videoMetadata: VideoMetadata,
    isSaving: Boolean,
    onBack: () -> Unit,
    onEditDetails: () -> Unit,
    onChangeVideo: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(videoMetadata.filePath) {
        thumbnail = withContext(Dispatchers.IO) {
            // Not MediaMetadataRetriever.use { } — Closeable conformance is API 29+
            // and minSdk here is 24; release() in finally works on every version.
            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(videoMetadata.filePath)
                retriever.getFrameAtTime(0)
            }.getOrNull().also { retriever.release() }
        }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(modifier = Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Column {
                            Text("Review assessment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Text("Step 3 of 3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SafetyPrimaryButton(
                        text = if (isSaving) "Saving…" else "Save assessment",
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().padding(20.dp)
                    )
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                thumbnail?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Video thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(8.dp))
            }

            SummarySection(
                title = "Assessment",
                rows = listOfNotNull(
                    "Assessment name" to draft.name,
                    draft.projectOrSite?.let { "Project or site" to it },
                    draft.workerReference?.let { "Worker reference" to it },
                    "Task type" to (draft.customTaskDescription ?: draft.taskType.label())
                )
            )
            SummarySection(
                title = "Load and grip",
                rows = listOf(
                    "Load handled" to "${formatLoad(draft.loadKg)} kg",
                    "Sudden or rapid force" to if (draft.suddenForce) "Yes" else "No",
                    "Grip and coupling" to draft.coupling.label()
                )
            )
            SummarySection(
                title = "Video",
                rows = listOf(
                    "Source" to draft.source.sourceLabel(),
                    "Duration" to formatDuration(videoMetadata.durationMs),
                    "Resolution" to if (videoMetadata.widthPx != null && videoMetadata.heightPx != null) {
                        "${videoMetadata.widthPx}×${videoMetadata.heightPx}"
                    } else "Unknown",
                    "File size" to formatFileSize(videoMetadata.fileSizeBytes)
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onEditDetails, modifier = Modifier.weight(1f)) { Text("Edit details") }
                OutlinedButton(onClick = onChangeVideo, modifier = Modifier.weight(1f)) { Text("Change video") }
            }
        }
    }
}

@Composable
private fun SummarySection(title: String, rows: List<Pair<String, String>>) {
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

private fun formatLoad(loadKg: Double): String =
    if (loadKg == loadKg.toLong().toDouble()) loadKg.toLong().toString() else loadKg.toString()

private fun formatDuration(ms: Long?): String {
    if (ms == null) return "Unknown"
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun formatFileSize(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
