package com.azizbek.safetyai.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azizbek.safetyai.analysis.FrameRebaResult
import com.azizbek.safetyai.analysis.RiskLevel
import kotlin.math.max

private val chartColors = listOf(
    Color(0xFF687782), // Unscored
    Color(0xFF3EB8A1), // Low
    Color(0xFFFFC857), // Medium
    Color(0xFFF39044), // High
    Color(0xFFE35B60)  // Very high
)

internal data class TimelineColumns(val risks: IntArray, val scores: IntArray)

/** A display summary only. Each pixel column retains the highest scored value present there. */
internal fun timelineColumns(
    frames: List<FrameRebaResult>,
    columnCount: Int
): TimelineColumns {
    if (frames.isEmpty() || columnCount <= 0) return TimelineColumns(IntArray(0), IntArray(0))
    val firstUs = frames.first().timestampUs
    val last = frames.last()
    val endUs = max(last.timestampUs + last.durationUs, firstUs + 1L)
    val lengthUs = (endUs - firstUs).toDouble()
    val columns = IntArray(columnCount)
    val scores = IntArray(columnCount) { -1 }
    frames.forEach { frame ->
        val level = if (frame.valid && frame.finalScore != null) {
            when (frame.riskLevel) {
                RiskLevel.LOW -> 1
                RiskLevel.MEDIUM -> 2
                RiskLevel.HIGH -> 3
                RiskLevel.VERY_HIGH -> 4
                null -> 0
            }
        } else 0
        val start = (((frame.timestampUs - firstUs) / lengthUs) * columnCount)
            .toInt().coerceIn(0, columnCount - 1)
        val frameEndUs = max(frame.timestampUs + frame.durationUs, frame.timestampUs + 1L)
        val end = (((frameEndUs - firstUs) / lengthUs) * columnCount)
            .toInt().coerceIn(start, columnCount - 1)
        for (column in start..end) {
            columns[column] = max(columns[column], level)
            if (level != 0) {
                scores[column] = max(scores[column], frame.finalScore ?: -1)
            }
        }
    }
    return TimelineColumns(columns, scores)
}

@Composable
internal fun RebaTimelineChart(
    frames: List<FrameRebaResult>,
    peakTimestampMs: Long,
    playbackTimestampMs: Long? = null,
    onSeek: ((Long) -> Unit)? = null
) {
    val firstUs = frames.first().timestampUs
    val endUs = max(frames.last().timestampUs + frames.last().durationUs, firstUs + 1L)
    val peakFraction = ((peakTimestampMs * 1_000L - firstUs).toDouble() / (endUs - firstUs))
        .toFloat().coerceIn(0f, 1f)
    val currentFraction = playbackTimestampMs?.let {
        ((it * 1_000L - firstUs).toDouble() / (endUs - firstUs))
            .toFloat().coerceIn(0f, 1f)
    }
    val currentFrame = playbackTimestampMs?.let { timeMs ->
        val timestampUs = timeMs * 1_000L
        val found = frames.binarySearchBy(timestampUs) { it.timestampUs }
        frames[if (found >= 0) found else (-found - 2).coerceIn(0, frames.lastIndex)]
    }

    fun seekAt(x: Float, width: Int) {
        val fraction = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        onSeek?.invoke((firstUs + (endUs - firstUs) * fraction).toLong() / 1_000L)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(54.dp)
                .pointerInput(onSeek, firstUs, endUs) {
                    detectTapGestures { position ->
                        seekAt(position.x, size.width)
                    }
                }
                .pointerInput(onSeek, firstUs, endUs) {
                    detectDragGestures { change, _ ->
                        seekAt(change.position.x, size.width)
                        change.consume()
                    }
                }
        ) {
            val width = size.width.toInt().coerceAtLeast(1)
            val columns = timelineColumns(frames, width)
            columns.risks.forEachIndexed { x, level ->
                drawRect(
                    color = chartColors[level],
                    topLeft = Offset(x.toFloat(), 0f),
                    size = Size(1f, size.height)
                )
            }
            for (x in 1 until width) {
                val previous = columns.scores[x - 1]
                val current = columns.scores[x]
                if (previous > 0 && current > 0) {
                    val previousY = size.height * (1f - (previous - 1).coerceIn(0, 14) / 14f)
                    val currentY = size.height * (1f - (current - 1).coerceIn(0, 14) / 14f)
                    drawLine(Color.White, Offset((x - 1).toFloat(), previousY),
                        Offset(x.toFloat(), currentY), strokeWidth = 2f)
                }
            }
            val peakX = size.width * peakFraction
            drawLine(Color(0xFF84D5FF), Offset(peakX, 0f), Offset(peakX, size.height), strokeWidth = 3f)
            currentFraction?.let { fraction ->
                val currentX = size.width * fraction
                drawLine(Color(0xFFFFC857), Offset(0f, size.height - 3f),
                    Offset(currentX, size.height - 3f), strokeWidth = 5f)
                drawLine(Color(0xFF111820), Offset(currentX, 0f),
                    Offset(currentX, size.height), strokeWidth = 7f)
                drawLine(Color(0xFFFFC857), Offset(currentX, 0f),
                    Offset(currentX, size.height), strokeWidth = 4f)
                drawCircle(Color.White, radius = 6f, center = Offset(currentX, 6f))
            }
        }
        playbackTimestampMs?.let { timeMs ->
          currentFrame?.let { frame ->
            val scoreText = frame.finalScore?.let { "REBA $it" } ?: "Not scored"
            val riskText = frame.riskLevel?.name?.replace('_', ' ')?.lowercase()
            Text(
                text = "Now %.1f s  •  %s%s".format(
                    timeMs / 1000.0,
                    scoreText,
                    if (riskText == null) "" else "  •  $riskText risk"
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
          }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 s", style = MaterialTheme.typography.labelSmall)
            Text("%.1f s".format((endUs - firstUs) / 1_000_000.0),
                style = MaterialTheme.typography.labelSmall)
        }
        Text("White: REBA  •  Blue: peak  •  Yellow: now",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold)
        Text("Low: teal  •  Medium: yellow  •  High: orange  •  Very high: red  •  Unscored: gray",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
