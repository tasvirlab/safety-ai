package com.azizbek.safetyai.capture

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * Thin, non-Compose wrapper around CameraX lifecycle binding + video
 * recording. Owned by [CameraRecordingScreen] via `remember`, bound/unbound
 * through a `DisposableEffect` so it never outlives the screen. No audio
 * source is ever attached — ergonomic analysis needs no audio.
 */
class VideoRecorderController(private val context: Context) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    var hasFrontCamera: Boolean = false
        private set
    var hasFlash: Boolean = false
        private set

    /** Binds preview + video-capture use cases. Returns false (without throwing) if the camera can't be bound. */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        useFrontCamera: Boolean
    ): Boolean {
        val provider = cameraProvider ?: runCatching {
            ProcessCameraProvider.getInstance(context).get()
        }.getOrNull()?.also { cameraProvider = it } ?: return false

        val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (!provider.hasCamera(selector)) return false
        hasFrontCamera = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)

        // Preview.setSurfaceProvider has no matching getter, so Kotlin can't
        // expose it as a `var` — call the setter directly.
        val preview = CameraPreview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.FHD, Quality.HD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
        )
        val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
        val capture = VideoCapture.withOutput(recorder)

        return runCatching {
            provider.unbindAll()
            val boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            camera = boundCamera
            hasFlash = !useFrontCamera && boundCamera.cameraInfo.hasFlashUnit()
            videoCapture = capture
            true
        }.getOrElse {
            Log.w("VideoRecorderController", "Camera bind failed: ${it.javaClass.simpleName}")
            false
        }
    }

    /** No-ops on lenses/devices without a flash unit. */
    fun setTorch(enabled: Boolean) {
        if (hasFlash) runCatching { camera?.cameraControl?.enableTorch(enabled) }
    }

    /** Starts recording to [outputFile]; [onEvent] receives Start/Finalize so the screen can react without polling. */
    fun startRecording(outputFile: File, onEvent: (VideoRecordEvent) -> Unit): Boolean {
        val capture = videoCapture ?: return false
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        val pending: PendingRecording = capture.output.prepareRecording(context, outputOptions)
        // No withAudioEnabled() call — audio is never captured.
        // Explicit Consumer wrap: a Kotlin function *value* (as opposed to a
        // lambda literal) isn't auto-SAM-converted to the Java Consumer type.
        activeRecording = pending.start(
            ContextCompat.getMainExecutor(context),
            androidx.core.util.Consumer<VideoRecordEvent> { event -> onEvent(event) }
        )
        return true
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun unbind() {
        runCatching { activeRecording?.close() }
        activeRecording = null
        cameraProvider?.unbindAll()
        camera = null
    }
}
