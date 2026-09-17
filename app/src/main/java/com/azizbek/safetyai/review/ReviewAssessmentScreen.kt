package com.azizbek.safetyai.review

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.azizbek.safetyai.assessment.AssessmentDraft
import com.azizbek.safetyai.assessment.label
import com.azizbek.safetyai.assessment.sourceLabel
import com.azizbek.safetyai.domain.VideoMetadata
import com.azizbek.safetyai.ui.components.SafetyOutlinedButton
import com.azizbek.safetyai.ui.components.SafetyPrimaryButton
import com.azizbek.safetyai.ui.components.StepProgressBar
import java.io.File
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Step 5 of 5 — the final chance to watch the clip back, review the entered
 * details, and either re-record/choose again or save. Combines what used to
 * be two screens (video preview, then a separate summary) into one, matching
 * the redesigned wizard's step count.
 */
@Composable
fun ReviewAssessmentScreen(
    draft: AssessmentDraft,
    videoFilePath: String,
    videoMetadata: VideoMetadata,
    isSaving: Boolean,
    onDiscardVideo: () -> Unit,
    onEditDetails: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    var showDiscardDialog by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(videoMetadata.durationMs ?: 0L) }
    var isBuffering by remember { mutableStateOf(true) }

    val player = remember {
        val exoPlayer = ExoPlayer.Builder(context).build()
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoFilePath))))
        exoPlayer.prepare()
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY && exoPlayer.duration > 0) durationMs = exoPlayer.duration
            }
            override fun onPlayerError(error: PlaybackException) { playbackError = true }
        })
        exoPlayer
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            kotlinx.coroutines.delay(250)
        }
    }

    fun requestDiscard() { showDiscardDialog = true }

    BackHandler(enabled = !showDiscardDialog) { requestDiscard() }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(modifier = Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::requestDiscard) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Column {
                            Text("Review & confirm", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                            Text("Step 5 of 5", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    StepProgressBar(currentStep = 5, totalSteps = 5, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
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
                        enabled = !isSaving && !playbackError,
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (playbackError) {
                        Text(
                            "This video couldn't be played back.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    setUseController(false)
                                    this.player = player
                                }
                            }
                        )
                        if (isBuffering) CircularProgressIndicator()
                    }
                }

                if (!playbackError) {
                    Slider(
                        value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                        onValueChange = { fraction -> player.seekTo((fraction * durationMs).roundToLong()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(positionMs), style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                            Text(if (isPlaying) "❚❚" else "▶", color = MaterialTheme.colorScheme.onBackground)
                        }
                        Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            SummarySection(
                title = "Assessment",
                rows = listOfNotNull(
                    "Assessment name" to draft.name,
                    draft.projectOrSite?.let { "Work area" to it },
                    draft.workerReference?.let { "Worker code" to it },
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
                    "Duration" to formatDuration(videoMetadata.durationMs ?: durationMs),
                    "Resolution" to if (videoMetadata.widthPx != null && videoMetadata.heightPx != null) {
                        "${videoMetadata.widthPx}×${videoMetadata.heightPx}"
                    } else "Unknown",
                    "File size" to formatFileSize(videoMetadata.fileSizeBytes)
                )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier)
                Text(
                    text = "Analysis runs on-device. Nothing is uploaded and the clip stays in app storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SafetyOutlinedButton(text = "Edit details", onClick = onEditDetails, modifier = Modifier.weight(1f))
                SafetyOutlinedButton(
                    text = if (draft.source == com.azizbek.safetyai.assessment.AssessmentSource.CAMERA) "Retake" else "Choose another",
                    onClick = ::requestDiscard,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard this video?") },
            text = { Text("This video will be deleted and you'll need to record or select another.") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onDiscardVideo() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep video", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        )
    }
}

@Composable
private fun SummarySection(title: String, rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
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
