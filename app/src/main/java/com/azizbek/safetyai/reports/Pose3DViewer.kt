package com.azizbek.safetyai.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import com.azizbek.safetyai.analysis.FrameAnalysis
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun Pose3DViewer(
    frames: List<FrameAnalysis>,
    modifier: Modifier = Modifier,
    playbackFrameIndex: Int? = null,
    playbackPlaying: Boolean? = null,
    onSeek: ((Int) -> Unit)? = null,
    onTogglePlay: (() -> Unit)? = null
) {
    if (frames.isEmpty()) {
        Text("No 3D pose frames are available.")
        return
    }

    var localFrameIndex by remember(frames) {
        mutableIntStateOf(0)
    }
    var localPlaying by remember {
        mutableStateOf(false)
    }
    val frameIndex = (playbackFrameIndex ?: localFrameIndex).coerceIn(0, frames.lastIndex)
    val playing = playbackPlaying ?: localPlaying
    var yaw by remember {
        mutableFloatStateOf(-0.65f)
    }
    var pitch by remember {
        mutableFloatStateOf(0.30f)
    }

    val scaleReference = remember(frames) {
        var maximumDistance = 0.5f

        frames.forEach { frame ->
            val root = frame.pose3D.getOrNull(ROOT_INDEX)

            if (root != null && root.valid) {
                frame.pose3D.forEach { joint ->
                    if (
                        joint.valid &&
                        joint.x.isFinite() &&
                        joint.y.isFinite() &&
                        joint.z.isFinite()
                    ) {
                        val dx = joint.x - root.x
                        val dy = joint.y - root.y
                        val dz = joint.z - root.z
                        val distance = sqrt(
                            dx * dx + dy * dy + dz * dz
                        )

                        if (distance > maximumDistance) {
                            maximumDistance = distance
                        }
                    }
                }
            }
        }

        maximumDistance
    }
    val floorOffsets = remember(frames) {
        poseFloorOffsets(frames)
    }

    LaunchedEffect(localPlaying, frames.size, playbackFrameIndex) {
        while (localPlaying && playbackFrameIndex == null) {
            delay(33L)
            localFrameIndex = (localFrameIndex + PLAYBACK_STEP) % frames.size
        }
    }

    val frame = frames[frameIndex]
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val viewerBackground = if (dark) Color(0xFF111820) else Color(0xFFFAFAFA)
    val gridColor = if (dark) Color(0xFF52616D) else Color(0xFFB8C3CB)
    val centerColor = if (dark) Color(0xFFE6EBEF) else Color(0xFF26343D)
    val leftColor = if (dark) Color(0xFF37B8A5) else Color(0xFF087F72)
    val rightColor = if (dark) Color(0xFFFFC857) else Color(0xFFB76A08)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Time in video",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "%.2f s".format(frame.timestampMs / 1000f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(viewerBackground)

        ) {
            val root = frame.pose3D.getOrNull(ROOT_INDEX)
                ?: return@Canvas

            if (!root.valid) {
                return@Canvas
            }

            /*
             * PoseMamba / CoMo3D coordinate convention:
             * X = horizontal
             * Y = depth
             * Z = vertical
             *
             * Yaw therefore rotates around Z. Front view projects X/Z;
             * side view projects Y/Z.
             */
            val cosineYaw = cos(yaw)
            val sineYaw = sin(yaw)
            val cosinePitch = cos(pitch)
            val sinePitch = sin(pitch)

            val scale =
                size.minDimension * 0.34f / scaleReference

            val center = Offset(
                x = size.width * 0.5f,
                y = size.height * 0.79f
            )

            // The model is pelvis-relative. Place the lowest visible foot at
            // the same ground height, while letting the pelvis rise and fall.
            val groundZ = floorOffsets[frameIndex]
            fun project(x: Float, y: Float, z: Float): Offset {
                val rotatedX = cosineYaw * x - sineYaw * y
                val rotatedY = sineYaw * x + cosineYaw * y
                return Offset(center.x - rotatedX * scale,
                    center.y - (sinePitch * rotatedY + z) * scale)
            }
            val extent = scaleReference * 0.8f
            val wallHeight = scaleReference * 1.7f

            // Spatial scene surfaces: display only, no pose changes.
            fun scenePlane(points: List<Offset>, color: Color) {
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, color)
            }

            scenePlane(
                listOf(
                    project(-extent, -extent, 0f),
                    project(extent, -extent, 0f),
                    project(extent, extent, 0f),
                    project(-extent, extent, 0f)
                ),
                if (dark) Color(0xFF22313C) else Color(0xFFE1E9EE)
            )
            scenePlane(
                listOf(
                    project(-extent, extent, 0f),
                    project(extent, extent, 0f),
                    project(extent, extent, wallHeight),
                    project(-extent, extent, wallHeight)
                ),
                if (dark) Color(0xFF18232C) else Color(0xFFF1F5F7)
            )
            scenePlane(
                listOf(
                    project(-extent, -extent, 0f),
                    project(-extent, extent, 0f),
                    project(-extent, extent, wallHeight),
                    project(-extent, -extent, wallHeight)
                ),
                if (dark) Color(0xFF1C2933) else Color(0xFFEAF0F4)
            )
            for (step in 0..6) {
                val position = -extent + 2f * extent * step / 6f
                val height = wallHeight * step / 6f
                // Floor and two vertical planes share the skeleton projection.
                drawLine(gridColor, project(position, -extent, 0f), project(position, extent, 0f), 1f)
                drawLine(gridColor, project(-extent, position, 0f), project(extent, position, 0f), 1f)
                drawLine(gridColor, project(position, extent, 0f), project(position, extent, wallHeight), 1f)
                drawLine(gridColor, project(-extent, position, 0f), project(-extent, position, wallHeight), 1f)
                drawLine(gridColor, project(-extent, extent, height), project(extent, extent, height), 1f)
                drawLine(gridColor, project(-extent, -extent, height), project(-extent, extent, height), 1f)
            }
            val origin = project(-extent, extent, 0f)
            drawLine(centerColor.copy(alpha = 0.65f), origin, project(extent, extent, 0f), 2f)
            drawLine(centerColor.copy(alpha = 0.65f), origin, project(-extent, -extent, 0f), 2f)
            drawLine(centerColor.copy(alpha = 0.65f), origin, project(-extent, extent, wallHeight), 2f)

            val projected = frame.pose3D.map { joint ->
                if (
                    !joint.valid ||
                    !joint.x.isFinite() ||
                    !joint.y.isFinite() ||
                    !joint.z.isFinite()
                ) {
                    null
                } else {
                    val x = joint.x - root.x
                    val y = joint.y - root.y
                    val z = joint.z - root.z - groundZ

                    // Horizontal rotation around the vertical Z axis.
                    val yawX =
                        cosineYaw * x - sineYaw * y
                    val yawY =
                        sineYaw * x + cosineYaw * y

                    // Vertical tilt around the X axis.
                    val pitchY =
                        cosinePitch * yawY - sinePitch * z
                    val pitchZ =
                        sinePitch * yawY + z

                    Offset(
                        // PoseMamba X is reversed relative to the video pixel X axis.
                        // Reflect only the preview; retain the stored 3D coordinates.
                        x = center.x - yawX * scale,
                        // Positive Z is upward in 3D, while Canvas Y
                        // increases downward.
                        y = center.y - pitchZ * scale
                    )
                }
            }

            REPORT_POSE_BONES.forEach { (startIndex, endIndex) ->
                val start = projected.getOrNull(startIndex)
                val end = projected.getOrNull(endIndex)

                if (start != null && end != null) {
                    val color = when {
                        startIndex in LEFT_JOINTS ||
                            endIndex in LEFT_JOINTS ->
                            leftColor

                        startIndex in RIGHT_JOINTS ||
                            endIndex in RIGHT_JOINTS ->
                            rightColor

                        else -> centerColor
                    }

                    drawLine(
                        color = color,
                        start = start,
                        end = end,
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }

            projected.forEachIndexed { index, point ->
                if (point != null) {
                    val color = when (index) {
                        in LEFT_JOINTS -> leftColor
                        in RIGHT_JOINTS -> rightColor
                        else -> centerColor
                    }

                    drawCircle(
                        color = color,
                        radius = 3.5.dp.toPx(),
                        center = point
                    )
                }
            }
        }

        Slider(
            value = frameIndex.toFloat(),
            onValueChange = { value ->
                val selected = value.toInt().coerceIn(0, frames.lastIndex)
                if (onSeek == null) {
                    localPlaying = false
                    localFrameIndex = selected
                } else {
                    onSeek(selected)
                }
            },
            valueRange =
                0f..frames.lastIndex.coerceAtLeast(1).toFloat()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (onTogglePlay == null) {
                        localPlaying = !localPlaying
                    } else {
                        onTogglePlay()
                    }
                }
            ) {
                Text(if (playing) "Pause" else "Play")
            }

            TextButton(
                onClick = {
                    yaw = 0f
                    pitch = 0f
                }
            ) {
                Text("Front")
            }

            TextButton(
                onClick = {
                    yaw = (PI / 2.0).toFloat()
                    pitch = 0f
                }
            ) {
                Text("Side")
            }
        }

        Text(
            text = "Use Front or Side to change the view.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val ROOT_INDEX = 3
private const val PLAYBACK_STEP = 2

/** Ground height relative to the pelvis. A short median suppresses one-frame foot spikes. */
internal fun poseFloorOffsets(frames: List<FrameAnalysis>): FloatArray {
    val measured = FloatArray(frames.size) { Float.NaN }
    frames.forEachIndexed { index, frame ->
        val root = frame.pose3D.getOrNull(ROOT_INDEX)
        if (root != null && root.valid && root.z.isFinite()) {
            val feet = listOf(16, 17, 18, 19).mapNotNull { footIndex ->
                frame.pose3D.getOrNull(footIndex)?.let { foot ->
                    if (foot.valid && foot.z.isFinite()) foot.z - root.z else null
                }
            }
            if (feet.isNotEmpty()) measured[index] = feet.minOrNull()!!
        }
    }

    val smoothed = FloatArray(frames.size) { index ->
        val neighbors = ((index - 2).coerceAtLeast(0)..
            (index + 2).coerceAtMost(frames.lastIndex))
            .map { measured[it] }
            .filter { it.isFinite() }
            .sorted()
        if (neighbors.isEmpty()) Float.NaN else neighbors[neighbors.size / 2]
    }

    // Keep a stable ground when the feet disappear briefly from one frame.
    var last = smoothed.firstOrNull { it.isFinite() } ?: 0f
    for (index in smoothed.indices) {
        if (smoothed[index].isFinite()) last = smoothed[index]
        else smoothed[index] = last
    }
    return smoothed
}

private val LEFT_JOINTS = setOf(
    4, 6, 8, 10,
    12, 14, 16, 18
)

private val RIGHT_JOINTS = setOf(
    5, 7, 9, 11,
    13, 15, 17, 19
)

internal val REPORT_POSE_BONES = listOf(
    0 to 1,
    1 to 2,
    2 to 20,
    20 to 3,

    1 to 4,
    4 to 6,
    6 to 8,
    8 to 10,

    1 to 5,
    5 to 7,
    7 to 9,
    9 to 11,

    3 to 12,
    12 to 14,
    14 to 16,
    16 to 18,

    3 to 13,
    13 to 15,
    15 to 17,
    17 to 19
)
