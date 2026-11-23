package com.azizbek.safetyai.capture

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.azizbek.safetyai.data.video.VideoFileStore
import com.azizbek.safetyai.ui.components.SafetyPrimaryButton
import java.io.File
import java.util.Locale

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private sealed interface PermissionState {
    data object Unknown : PermissionState
    data object Granted : PermissionState
    data object Denied : PermissionState
    data object PermanentlyDenied : PermissionState
}

/**
 * Full-screen CameraX recording UI. Requests CAMERA only — never
 * microphone — and never crashes when a camera can't be bound, falling back
 * to an explicit "Camera unavailable" state instead.
 */
@Composable
fun CameraRecordingScreen(
    videoFileStore: VideoFileStore,
    onBack: () -> Unit,
    onRecorded: (File) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { VideoRecorderController(context) }

    var permissionState by remember {
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                PermissionState.Granted
            } else {
                PermissionState.Unknown
            }
        )
    }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var useFrontCamera by rememberSaveable { mutableStateOf(false) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingOutputFile by remember { mutableStateOf<File?>(null) }
    var framingFeedback by remember { mutableStateOf(
        FramingFeedback(false, "Keep one worker fully visible")
    ) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionState = when {
            granted -> PermissionState.Granted
            activity != null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) ->
                PermissionState.PermanentlyDenied
            else -> PermissionState.Denied
        }
    }

    LaunchedEffect(Unit) {
        if (permissionState == PermissionState.Unknown) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(isRecording) {
        elapsedSeconds = 0
        while (isRecording) {
            kotlinx.coroutines.delay(1_000)
            elapsedSeconds += 1
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.unbind() }
    }

    fun requestBack() {
        if (isRecording) showDiscardDialog = true else onBack()
    }

    BackHandler(enabled = !showDiscardDialog) { requestBack() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (permissionState) {
            PermissionState.Granted -> {
                if (cameraUnavailable) {
                    CameraUnavailableContent(onBack = onBack)
                } else {
                    val previewView = remember {
                        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
                    }
                    // Rebinding tears down any in-progress recording, so this only
                    // reruns when the lens actually changes — never on every
                    // recomposition (e.g. the once-a-second recording timer).
                    LaunchedEffect(useFrontCamera) {
                        framingFeedback = FramingFeedback(false, "Checking full-body framing")
                        cameraUnavailable = !controller.bind(
                            lifecycleOwner, previewView, useFrontCamera
                        ) { feedback -> framingFeedback = feedback }
                    }
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })
                    // Color reports full-body framing, never measured distance.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 48.dp, vertical = 96.dp)
                    ) {
                        val frameColor = if (framingFeedback.ready) {
                            Color(0xFF35C96B)
                        } else {
                            Color(0xFFE35050)
                        }
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(
                                color = frameColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                            )
                        }
                    }

                    if (!isRecording) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 20.dp).padding(bottom = 124.dp)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.68f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Stand about 3 m from the camera",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = framingFeedback.message,
                            color = if (framingFeedback.ready) Color(0xFF35C96B) else Color(0xFFFF7777),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    }

                    CameraTopBar(
                        onBack = ::requestBack,
                        hasFrontCamera = controller.hasFrontCamera,
                        hasFlash = controller.hasFlash,
                        flashOn = torchOn,
                        recordingDisabled = isRecording,
                        onSwitchCamera = {
                            framingFeedback = FramingFeedback(false, "Checking full-body framing")
                            useFrontCamera = !useFrontCamera
                        },
                        onToggleFlash = {
                            torchOn = !torchOn
                            controller.setTorch(torchOn)
                        }
                    )

                    if (isRecording) {
                        RecordingIndicator(
                            elapsedSeconds = elapsedSeconds,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(top = 64.dp)
                        )
                    }

                    RecordButton(
                        isRecording = isRecording,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 32.dp),
                        onClick = {
                            if (!isRecording) {
                                val outputFile = videoFileStore.newRecordingFile()
                                pendingOutputFile = outputFile
                                val started = controller.startRecording(outputFile) { event ->
                                    if (event is VideoRecordEvent.Finalize) {
                                        isRecording = false
                                        if (!event.hasError()) {
                                            pendingOutputFile?.let(onRecorded)
                                        }
                                    }
                                }
                                if (started) isRecording = true
                            } else {
                                controller.stopRecording()
                            }
                        }
                    )
                }
            }
            PermissionState.Unknown, PermissionState.Denied -> {
                PermissionRationaleContent(
                    permanentlyDenied = false,
                    onBack = onBack,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
            PermissionState.PermanentlyDenied -> {
                PermissionRationaleContent(
                    permanentlyDenied = true,
                    onBack = onBack,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard this recording?") },
            text = { Text("Recording in progress will not be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    controller.stopRecording()
                    pendingOutputFile?.let { videoFileStore.delete(it.absolutePath) }
                    isRecording = false
                    showDiscardDialog = false
                    onBack()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep recording", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        )
    }
}

@Composable
private fun CameraTopBar(
    onBack: () -> Unit,
    hasFrontCamera: Boolean,
    hasFlash: Boolean,
    flashOn: Boolean,
    recordingDisabled: Boolean,
    onSwitchCamera: () -> Unit,
    onToggleFlash: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, enabled = !recordingDisabled) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Row {
            if (hasFlash) {
                IconButton(onClick = onToggleFlash) {
                    Icon(
                        imageVector = if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }
            }
            if (hasFrontCamera) {
                IconButton(onClick = onSwitchCamera, enabled = !recordingDisabled) {
                    Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun RecordingIndicator(elapsedSeconds: Int, modifier: Modifier = Modifier) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        Text(
            text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = if (isRecording) "Stop recording" else "Start recording"
    Box(
        modifier = modifier
            .size(76.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Box(modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.error, RoundedSquare))
        }
    }
}

private val RoundedSquare = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)

@Composable
private fun CameraUnavailableContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.VideocamOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(
            text = "Camera unavailable",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "This device's camera can't be opened right now. You can upload an existing video instead.",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        SafetyPrimaryButton(text = "Back", onClick = onBack, modifier = Modifier.padding(top = 24.dp).fillMaxWidth())
    }
}

@Composable
private fun PermissionRationaleContent(
    permanentlyDenied: Boolean,
    onBack: () -> Unit,
    onRequest: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera access needed",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (permanentlyDenied) {
                "Camera access was denied. Enable it in system settings to record a task."
            } else {
                "Safety AI needs camera access to record the worker's task. Audio is never recorded."
            },
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (permanentlyDenied) {
            SafetyPrimaryButton(
                text = "Open settings",
                onClick = { onOpenSettings?.invoke() },
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth()
            )
        } else {
            SafetyPrimaryButton(
                text = "Allow camera access",
                onClick = { onRequest?.invoke() },
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth()
            )
        }
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("Back", color = Color.White)
        }
    }
}
