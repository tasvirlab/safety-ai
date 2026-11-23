package com.azizbek.safetyai.reports

import android.view.LayoutInflater
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.azizbek.safetyai.R
import com.azizbek.safetyai.analysis.FrameAnalysis

/** Video and 2D joints use the same oriented image dimensions and playback clock. */
@OptIn(UnstableApi::class)
@Composable
internal fun ReportVideoPosePlayer(
    player: ExoPlayer,
    frame: FrameAnalysis,
    widthPx: Int?,
    heightPx: Int?
) {
    if (widthPx == null || heightPx == null || widthPx <= 0 || heightPx <= 0) {
        Text("Video dimensions are unavailable for the 2D overlay.")
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                (LayoutInflater.from(context).inflate(
                    R.layout.report_video_player,
                    null
                ) as PlayerView).apply {
                    setUseController(false)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
            update = { it.player = player }
        )

        Canvas(Modifier.fillMaxSize()) {
            val scale = maxOf(size.width / widthPx, size.height / heightPx)
            val offsetX = (size.width - widthPx * scale) * 0.5f
            val offsetY = (size.height - heightPx * scale) * 0.5f
            val points = frame.keypoints2D.map { joint ->
                if (joint.valid && joint.x.isFinite() && joint.y.isFinite()) {
                    Offset(offsetX + joint.x * scale, offsetY + joint.y * scale)
                } else {
                    null
                }
            }
            REPORT_POSE_BONES.forEach { (startIndex, endIndex) ->
                val start = points.getOrNull(startIndex)
                val end = points.getOrNull(endIndex)
                if (start != null && end != null) {
                    drawLine(Color(0xFFFFC857), start, end, strokeWidth = 3f)
                }
            }
            points.forEach { point ->
                if (point != null) drawCircle(Color(0xFF37B8A5), 3.5f, point)
            }
        }
    }
}
