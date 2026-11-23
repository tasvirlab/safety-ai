package com.azizbek.safetyai.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.azizbek.safetyai.analysis.RapidMotionEvent
import kotlin.math.abs

/** Stored detector events only: this is not a reconstructed angular-velocity trace. */
@Composable
internal fun RapidMotionTimeline(
    events: List<RapidMotionEvent>,
    status: String,
    durationMs: Long,
    playbackTimestampMs: Long?,
    onSeek: ((Long) -> Unit)?
) {
    val endMs = durationMs.coerceAtLeast(1L)
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val cursor = MaterialTheme.colorScheme.primary
    val eventColor = Color(0xFFE35B60)
    // A brief presentation cue around an event timestamp, not an event duration.
    val nearby = playbackTimestampMs?.let { time ->
        events.minByOrNull { abs(it.timestampMs - time) }
            ?.takeIf { abs(it.timestampMs - time) <= 250L }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rapid motion", style = MaterialTheme.typography.titleSmall)
        Canvas(Modifier.fillMaxWidth().height(64.dp)
            .pointerInput(events, endMs, onSeek) {
                detectTapGestures { point ->
                    val time = (point.x / size.width.coerceAtLeast(1) * endMs).toLong()
                        .coerceIn(0L, endMs)
                    val nearest = events.minByOrNull { abs(it.timestampMs - time) }
                    val snapDistance = (endMs * 24.0 / size.width.coerceAtLeast(1)).toLong()
                    onSeek?.invoke(if (nearest != null && abs(nearest.timestampMs - time) <= snapDistance)
                        nearest.timestampMs else time)
                }
            }) {
            val baseline = size.height * 0.75f
            drawLine(neutral.copy(alpha = 0.4f), Offset(0f, baseline), Offset(size.width, baseline), 2f)
            events.forEach { event ->
                val x = (event.timestampMs.toFloat() / endMs).coerceIn(0f, 1f) * size.width
                drawLine(eventColor, Offset(x, baseline), Offset(x, size.height * 0.25f), 3f)
                drawCircle(eventColor, 4f, Offset(x, size.height * 0.25f))
            }
            playbackTimestampMs?.let { time ->
                val x = (time.toFloat() / endMs).coerceIn(0f, 1f) * size.width
                drawLine(cursor, Offset(x, 0f), Offset(x, size.height), 3f)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 s", style = MaterialTheme.typography.labelSmall)
            Text("%.1f s".format(endMs / 1000.0), style = MaterialTheme.typography.labelSmall)
        }
        Text(if (nearby != null) "Rapid motion at %.2f s".format(nearby.timestampMs / 1000.0)
            else if (events.isNotEmpty()) "Red markers: detected events. Tap to review."
            else when (status.lowercase()) {
                "negative" -> "No rapid motion detected."
                "insufficient", "insufficient_evidence" -> "Insufficient evidence."
                else -> "Rapid motion timing is unavailable."
            }, color = if (nearby != null) eventColor else neutral,
            style = MaterialTheme.typography.bodySmall)
    }
}
