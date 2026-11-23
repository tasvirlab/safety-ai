package com.azizbek.safetyai.reports

import android.net.Uri
import android.content.Intent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import com.azizbek.safetyai.analysis.RiskLevel
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.analysis.caseStudySummary
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.data.result.AnalysisResultStore
import com.azizbek.safetyai.domain.Assessment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReportDetailScreen(
    repository: AssessmentRepository,
    resultStore: AnalysisResultStore,
    assessmentId: String,
    onBack: () -> Unit,
    viewModel: ReportDetailViewModel = viewModel(
        factory = ReportDetailViewModel.factory(
            repository = repository,
            resultStore = resultStore,
            assessmentId = assessmentId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector =
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }

                        Text(
                            text = "Ergonomic report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()
                }
            }
        }
    ) { innerPadding ->
        when (val state = uiState) {
            ReportDetailUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ReportDetailUiState.Unavailable -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            is ReportDetailUiState.Ready -> {
                ReportContent(
                    modifier = Modifier.padding(innerPadding),
                    assessment = state.assessment,
                    result = state.result
                )
            }
        }
    }
}

@Composable
private fun ReportContent(
    modifier: Modifier,
    assessment: Assessment,
    result: SafetyAnalysisResult
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    var showResearchDetails by remember { mutableStateOf(false) }
    val study = remember(result) { result.caseStudySummary() }
    val geometryUnreliable = result.inputQualityChecks.any {
        !it.passed && it.description == "2D and 3D shoulder geometry is reliable"
    }
    val repetitionValidatedForTask = assessment.taskType == TaskType.LIFTING ||
        assessment.taskType == TaskType.MATERIAL_LIFTING
    val videoPath = assessment.video.filePath
    val player = remember(videoPath) {
        if (File(videoPath).isFile) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
                prepare()
            }
        } else null
    }
    var playbackMs by remember(videoPath) { mutableLongStateOf(0L) }
    var playbackPlaying by remember(videoPath) { mutableStateOf(false) }
    // Compensates preview rendering lag; analysis timestamps and scores are untouched.
    val poseLeadMs = 32L

    DisposableEffect(player) {
        onDispose { player?.release() }
    }
    LaunchedEffect(player) {
        while (player != null) {
            withFrameNanos { }
            playbackMs = player.currentPosition.coerceAtLeast(0L)
            playbackPlaying = player.isPlaying
        }
    }
    val displayMs = (playbackMs + poseLeadMs).coerceAtLeast(0L)
    val seekPlayer: ((Long) -> Unit)? = player?.let { active ->
        { timeMs: Long -> active.seekTo((timeMs - poseLeadMs).coerceAtLeast(0L)) }
    }
    val playbackFrameIndex = remember(result.frames, displayMs) {
        if (result.frames.isEmpty()) 0 else {
            val index = result.frames.binarySearchBy(displayMs) { it.timestampMs }
            if (index >= 0) index else {
            val next = (-index - 1).coerceAtMost(result.frames.lastIndex)
            val previous = (next - 1).coerceAtLeast(0)
            if (displayMs - result.frames[previous].timestampMs <=
                result.frames[next].timestampMs - displayMs
            ) previous else next
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(2.dp))

            Text(
                text = assessment.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (geometryUnreliable) {
                        Text(
                            text = "Pose tracking was unreliable in this video. Re-record before using its 3D pose or REBA scores.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                    Text(
                        text = "Highest ergonomic risk in this video",
                        style = MaterialTheme.typography.labelLarge
                    )

                    Text(
                        text = result.overallRiskLevel.name
                            .replace('_', ' '),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Peak final REBA: ${result.reba.finalScore} at %.2f s"
                            .format(result.highestRiskTimestampMs / 1000.0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    study?.let { summary ->
                        val elevatedMs = summary.riskTimes
                            .filter { it.level == RiskLevel.HIGH || it.level == RiskLevel.VERY_HIGH }
                            .sumOf { it.durationMs }
                        Text(
                            text = "High or very high risk for %.1f s of %.1f s."
                                .format(elevatedMs / 1000.0, summary.videoDurationMs / 1000.0),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    }
                }
            }
        }

        item {
            ReportSection(title = "Activity score") {
                val activity = result.activity
                if (geometryUnreliable) {
                    Text("Movement scoring is incomplete because pose tracking was unreliable.",
                        color = MaterialTheme.colorScheme.error)
                } else {
                Text(
                    text = "+${result.reba.activityModifier}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Movement points added to the posture score.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }
                ActivityCriterionRow("Static posture", activity.staticStatus)
                ActivityCriterionRow(
                    "Repetition",
                    activity.repetitionStatus,
                    needsReview = !repetitionValidatedForTask,
                    repetitionCount = activity.repetitionCount,
                    repetitionReason = activity.repetitionReason
                )
                ActivityCriterionRow("Rapid motion", activity.rapidMotionStatus)
            }
        }

        item {
            OutlinedButton(
                onClick = { showResearchDetails = !showResearchDetails },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showResearchDetails) "Hide research details" else "Show research details")
            }
        }

        if (showResearchDetails) {
        item {
            ReportSection(title = "REBA components") {
                ReportValue(
                    "Neck",
                    result.reba.neckScore.toString()
                )
                ReportValue(
                    "Trunk",
                    result.reba.trunkScore.toString()
                )
                ReportValue(
                    "Legs",
                    result.reba.legsScore.toString()
                )
                ReportValue(
                    "Upper arm",
                    result.reba.upperArmScore.toString()
                )
                ReportValue(
                    "Lower arm",
                    result.reba.lowerArmScore.toString()
                )
                ReportValue(
                    "Wrist",
                    result.reba.wristScore.toString()
                )
            }
        }

        item {
            ReportSection(title = "Analysis quality") {
                ReportValue(
                    "Pose coverage",
                    "${result.coveragePercent}%"
                )
                ReportValue(
                    "Analyzed frames",
                    result.frames.size.toString()
                )
                ReportValue(
                    "Highest-risk time",
                    "${result.highestRiskTimestampMs / 1000f} s"
                )
            }
        }

        item {
            ReportSection(title = "Case study results") {
                if (study == null) {
                    Text("Per-frame REBA data were not saved for this earlier assessment. Reanalyze the video to export a case study timeline.")
                } else {
                    ReportValue("Video duration", "%.2f s".format(study.videoDurationMs / 1000.0))
                    ReportValue("Scored frames", "${study.scoredFrames} / ${study.totalFrames}")
                    ReportValue("Minimum REBA", "${study.minimumScore}, frame ${study.minimumFrameIndex + 1} at %.2f s".format(study.minimumTimestampMs / 1000.0))
                    ReportValue("Maximum REBA", "${study.maximumScore}, frame ${study.maximumFrameIndex + 1} at %.2f s".format(study.maximumTimestampMs / 1000.0))
                    ReportValue("Time-weighted mean REBA", "%.2f".format(study.timeWeightedMeanScore))
                    study.riskTimes.forEach { risk ->
                        ReportValue(risk.level.name.replace('_', ' '),
                            "%.2f s (%.1f%%); %d frames".format(risk.durationMs / 1000.0, risk.percentOfVideo, risk.frames))
                    }
                    ReportValue("Unscored time", "%.2f s; %d frames".format(study.unscoredDurationMs / 1000.0, study.unscoredFrames))
                    result.pose2DRepair?.let { repair ->
                        ReportValue("2D repaired frames", "${repair.imputedFrameCount} / ${study.totalFrames}")
                        ReportValue("3D model affected frames", "${repair.modelAffectedFrameCount} / ${study.totalFrames}")
                        ReportValue("Still incomplete", repair.remainingIncompleteFrames.toString())
                        repair.imputedByJoint.forEach { (joint, count) ->
                            ReportValue("Repaired $joint", "$count frames")
                        }
                        if (repair.imputedFrameCount > 0) {
                            Text("Scores from 3D windows containing reconstructed 2D joints are estimates. " +
                                "The case study ZIP identifies repaired joints and affected scores.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Risk percentages use the whole video duration. Reported scores remain conditional where criteria were not assessed.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        }

        if (player != null && result.frames.isNotEmpty()) {
            item {
                ReportSection(title = "Video with 2D pose") {
                    ReportVideoPosePlayer(
                        player = player,
                        frame = result.frames[playbackFrameIndex],
                        widthPx = assessment.video.widthPx,
                        heightPx = assessment.video.heightPx
                    )
                    result.frameRebaResults?.takeIf { it.isNotEmpty() }?.let { timeline ->
                        if (geometryUnreliable) {
                            Text("Timeline shown for review only; re-record before interpreting the scores.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        RebaTimelineChart(
                            timeline,
                            result.highestRiskTimestampMs,
                            playbackTimestampMs = displayMs,
                            onSeek = seekPlayer
                        )
                    }
                }
            }
        }

        if (player == null) {
            item {
                ReportSection(title = "Risk through the video") {
                    val timeline = result.frameRebaResults
                    if (timeline.isNullOrEmpty()) {
                        Text("A time graph is unavailable for this older report. Reanalyze the video to create it.")
                    } else {
                        RebaTimelineChart(timeline, result.highestRiskTimestampMs)
                    }
                }
            }
        }

        item {
            ReportSection(title = "3D movement") {
                Pose3DViewer(
                    frames = result.frames,
                    playbackFrameIndex = if (player == null) null else playbackFrameIndex,
                    playbackPlaying = if (player == null) null else playbackPlaying,
                    onSeek = player?.let { active ->
                        { index: Int ->
                            val timeMs = result.frames[index].timestampMs
                            val seekMs = (timeMs - poseLeadMs).coerceAtLeast(0L)
                            active.seekTo(seekMs)
                            playbackMs = seekMs
                        }
                    },
                    onTogglePlay = player?.let { active ->
                        {
                            if (active.isPlaying) active.pause()
                            else {
                                if (active.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                                    active.seekTo(0L)
                                }
                                active.play()
                            }
                        }
                    }
                )
            }
        }

        if (showResearchDetails) {
        item {
            ReportSection(title = "Activity Layer") {
                if (result.schemaVersion < 2) {
                    Text("This older report used Activity Score 0; activity criteria were not assessed.")
                } else {
                    val activity = result.activity
                    ReportValue("Activity criteria", if (activity.fullyEvaluated) "Complete" else "Conditional / incomplete")
                    ReportValue("Static hold", activityStatusLabel(activity.staticStatus))
                    ReportValue("Repetition", repetitionStatusLabel(
                        activity.repetitionStatus,
                        activity.repetitionCount,
                        activity.repetitionReason,
                        needsReview = !repetitionValidatedForTask
                    ))
                    ReportValue("Repetition rule", if (result.schemaVersion >= 3) {
                        "At least 4 cycles and 4/min"
                    } else {
                        "Previous rate-only rule (>4/min)"
                    })
                    ReportValue("Rapid motion", activityStatusLabel(activity.rapidMotionStatus))
                    ReportValue(
                        if (activity.repetitionReason == "pose_geometry_unreliable")
                            "Raw cycles (unreliable)" else "Counted repetitions",
                        activity.repetitionCount.toString()
                    )
                    activity.repetitionChannel?.let { ReportValue("Selected motion", it) }
                    activity.repetitionRatePerMinute?.let { rate ->
                        ReportValue("Repetitions per minute", "%.1f".format(rate))
                    }
                    if (!repetitionValidatedForTask) {
                        Text(
                            "The count and activity modifier use the current generic motion detector. Verify repetitions against the video before using them in the case study.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    listOfNotNull(
                        activity.staticReason,
                        activity.repetitionReason,
                        activity.rapidMotionReason
                    ).forEach { reason ->
                        Text(
                            text = reason.replace('_', ' '),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!activity.fullyEvaluated) {
                        Text(
                            "The score includes confirmed activity modifiers only. " +
                                "An unevaluable criterion contributes no point and remains unresolved.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        }

        if (!geometryUnreliable && result.recommendations.isNotEmpty()) {
            item {
                ReportSection(title = "Recommendations") {
                    result.recommendations.forEach { recommendation ->
                        Text(
                            text = "\u2022 " + (if (recommendation.equals("Necessary NOW", ignoreCase = true)) {
                                "Action needed now: review the posture and task setup."
                            } else recommendation),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (showResearchDetails && result.warnings.isNotEmpty()) {
            item {
                ReportSection(title = "Warnings") {
                    result.warnings.forEach { warning ->
                        Text(
                            text = "\u2022 $warning",
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    sharing = true
                    shareError = null

                    coroutineScope.launch {
                        val outcome = runCatching {
                            withContext(Dispatchers.IO) {
                                PdfReportGenerator.generate(
                                    context = context,
                                    assessment = assessment,
                                    result = result
                                )
                            }
                        }

                        outcome.onSuccess { file ->
                            val uri =
                                PdfReportGenerator.shareUriFor(
                                    context,
                                    file
                                )

                            val intent = Intent(
                                Intent.ACTION_SEND
                            ).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }

                            context.startActivity(
                                Intent.createChooser(
                                    intent,
                                    "Share Safety AI report"
                                )
                            )
                        }

                        outcome.onFailure { error ->
                            shareError =
                                error.message ?:
                                "PDF report could not be created."
                        }

                        sharing = false
                    }
                },
                enabled = !sharing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (sharing) {
                        "Creating PDF..."
                    } else {
                        "Export and share PDF"
                    }
                )
            }
        }

        if (showResearchDetails && study != null) {
            item {
                Button(
                    onClick = {
                        sharing = true
                        shareError = null
                        coroutineScope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    CaseStudyExport.generate(context, assessment, result)
                                }
                            }.onSuccess { file ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, CaseStudyExport.shareUriFor(context, file))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share case study data"))
                            }.onFailure { error ->
                                shareError = error.message ?: "Case study export could not be created."
                            }
                            sharing = false
                        }
                    },
                    enabled = !sharing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (sharing) "Preparing export..." else "Export case study data (ZIP)")
                }
            }
        }

        shareError?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            Text(
                text =
                    "Safety AI supports ergonomic task assessment " +
                        "and does not provide a medical diagnosis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

private fun activityStatusLabel(status: String): String = when (status) {
    "positive" -> "Detected"
    "negative" -> "Not detected"
    "insufficient" -> "Insufficient evidence"
    "unavailable" -> "Not assessed"
    else -> "Unknown"
}

private fun repetitionStatusLabel(
    status: String,
    count: Int,
    reason: String?,
    needsReview: Boolean
): String {
    val description = when {
        status == "positive" -> "$count counted; scoring threshold met"
        reason == "locomotion_task_excluded" -> "$count counted; not scored for locomotion"
        status == "negative" -> "Insufficient evidence"
        status == "insufficient" -> "Insufficient evidence to assess repetitions"
        status == "unavailable" -> "Repetition not assessed"
        else -> "Repetition status unknown"
    }
    return if (needsReview && status == "positive") "$description; review video" else description
}

@Composable
private fun ActivityCriterionRow(
    label: String,
    status: String,
    needsReview: Boolean = false,
    repetitionCount: Int? = null,
    repetitionReason: String? = null
) {
    val points = if (status == "positive") 1 else 0
    val description = if (repetitionCount != null) {
        repetitionStatusLabel(status, repetitionCount, repetitionReason, needsReview)
    } else if (needsReview && status == "positive") {
        "Needs video review"
    } else {
        activityStatusLabel(status)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Text("+$points", fontWeight = FontWeight.SemiBold)
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReportSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ReportValue(
    label: String,
    value: String
) {
    if (label.length <= 15 && value.length <= 14) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}
