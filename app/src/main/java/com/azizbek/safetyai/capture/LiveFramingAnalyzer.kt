package com.azizbek.safetyai.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.azizbek.safetyai.analysis.RtmPoseCanonical21
import com.azizbek.safetyai.analysis.RtmPoseFrameEstimator

/** Runs at most once per second on a dedicated analyzer thread. */
internal class LiveFramingAnalyzer(
    private val context: Context,
    private val onFeedback: (FramingFeedback) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    private var estimator: RtmPoseFrameEstimator? = null
    private var lastRunMs = 0L
    private var consecutiveReady = 0
    private var closed = false

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (closed || now - lastRunMs < 1_000L) {
            image.close()
            return
        }
        lastRunMs = now

        var imageClosed = false
        var original: Bitmap? = null
        var upright: Bitmap? = null
        try {
            val source = image.toBitmap()
            original = source
            val rotation = image.imageInfo.rotationDegrees
            image.close()
            imageClosed = true
            val rotated = if (rotation == 0) source else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            }
            upright = rotated

            val active = estimator ?: RtmPoseFrameEstimator(context).also { estimator = it }
            val people = active.estimatePeople(rotated)
            val selected = people.minByOrNull { person ->
                val box = person.boundingBox
                val dx = (box.left + box.right) * 0.5f / rotated.width - 0.5f
                val dy = (box.top + box.bottom) * 0.5f / rotated.height - 0.5f
                dx * dx + dy * dy
            }
            val feedback = if (selected == null) {
                FramingFeedback(false, "Keep one worker fully visible")
            } else {
                val box = selected.boundingBox
                val pose = RtmPoseCanonical21.map(selected)
                LiveFramingRule.evaluate(
                    rotated.width, rotated.height,
                    box.left, box.top, box.right, box.bottom,
                    pose.coordinates, pose.valid
                )
            }
            consecutiveReady = if (feedback.ready) consecutiveReady + 1 else 0
            Log.i("SafetyAI.Framing", "detected_people=${people.size};" +
                "ready=${feedback.ready};stable_checks=$consecutiveReady;" +
                "box_height_fraction=" +
                (selected?.let { (it.boundingBox.bottom - it.boundingBox.top) / rotated.height } ?: -1f))
            onFeedback(if (consecutiveReady >= 2) feedback else
                FramingFeedback(false, if (feedback.ready) "Hold still for framing check" else feedback.message))
        } catch (error: Exception) {
            consecutiveReady = 0
            Log.w("SafetyAI.Framing", "Live framing unavailable", error)
            onFeedback(FramingFeedback(false, "Automatic check unavailable. Keep your whole body visible"))
        } finally {
            if (!imageClosed) image.close()
            if (upright !== original) upright?.recycle()
            original?.recycle()
        }
    }

    override fun close() {
        closed = true
        estimator?.close()
        estimator = null
    }
}
