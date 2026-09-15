package com.azizbek.safetyai.preview

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.sourceLabel
import com.azizbek.safetyai.domain.VideoMetadata
import com.azizbek.safetyai.ui.components.SafetyPrimaryButton
import java.io.File
import java.util.Locale
import kotlin.math.roundToLong

/** Common preview for both a fresh recording and an uploaded video — Step 2 of 3. */
@Composable
fun VideoPreviewScreen(
    source: AssessmentSource,
    videoFilePath: String,
    metadata: VideoMetadata,
    onDiscard: () -> Unit,
    onUseThisVideo: () -> Unit
) {
    val context = LocalContext.current
    var showDiscardDialog by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(metadata.durationMs ?: 0L) }
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
                        Text("Step 2 of 3", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(onClick = ::requestDiscard, modifier = Modifier.weight(1f)) {
                            Text(if (source == AssessmentSource.CAMERA) "Retake" else "Choose another")
                        }
                        SafetyPrimaryButton(
                            text = "Use this video",
                            onClick = onUseThisVideo,
                            enabled = !playbackError,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
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
                        Text(if (isPlaying) "❚❚" else "▶", color = MaterialTheme.colorScheme.primary)
                    }
                    Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium)
                }
            }

            VideoDetailRow(label = "Source", value = source.sourceLabel())
            VideoDetailRow(label = "Duration", value = formatDuration(metadata.durationMs ?: durationMs))
            VideoDetailRow(
                label = "Resolution",
                value = if (metadata.widthPx != null && metadata.heightPx != null) "${metadata.widthPx}×${metadata.heightPx}" else "Unknown"
            )
            VideoDetailRow(label = "File size", value = formatFileSize(metadata.fileSizeBytes))
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard this video?") },
            text = { Text("This video will be deleted and you'll need to record or select another.") },
            confirmButton = { TextButton(onClick = { showDiscardDialog = false; onDiscard() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Keep video") } }
        )
    }
}

@Composable
private fun VideoDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
