package com.azizbek.safetyai.analysis

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Callable
import java.util.concurrent.Executors

data class RtmPosePerson2D(
    val boundingBox: RtmPoseBoundingBox,
    /** Flat coordinates: [x0, y0, x1, y1, ...]. */
    val coordinates: FloatArray,
    val scores: FloatArray
) {
    init {
        require(coordinates.size == 133 * 2)
        require(scores.size == 133)
        require(coordinates.all { it.isFinite() })
        require(scores.all { it.isFinite() })
    }
}

/**
 * Full on-device RTMPose-WB frame estimator:
 * Bitmap -> YOLOX detector -> person boxes -> RTMPose -> 133 keypoints.
 *
 * Run on a background dispatcher.
 */
class RtmPoseFrameEstimator(
    context: Context,
    private val useXnnpack: Boolean = false,
    private val parallelPoseEnabled: Boolean = false
) : AutoCloseable {
    private val session =
        RtmPoseOnDeviceSession(context.applicationContext, useXnnpack, FrozenPipeline.RTMPOSE_THREADS)

    private val secondSession = lazy {
        RtmPoseOnDeviceSession(context.applicationContext, useXnnpack, FrozenPipeline.RTMPOSE_THREADS)
    }
    private val parallelWorker = lazy {
        Executors.newSingleThreadExecutor()
    }

    private var closed = false

    private var processedFrameCount = 0
    private var cachedCentralBox: RtmPoseBoundingBox? = null
    private val bodyKeypointCount = 17
    private val bodyConfidenceThreshold = 0.30f
    private val minimumConfidentBodyKeypoints = 8

    @Synchronized
    fun estimatePeople(
        bitmap: Bitmap,
        detectorScoreThreshold: Float = 0.3f
    ): List<RtmPosePerson2D> {
        check(!closed) { "RTMPose frame estimator is closed" }
        require(!bitmap.isRecycled) { "Bitmap is recycled" }
        require(
            detectorScoreThreshold.isFinite() &&
                detectorScoreThreshold >= 0f
        )

        val frameStartedNs = SystemClock.elapsedRealtimeNanos()
        val frameIndex = processedFrameCount
        processedFrameCount += 1

        /*
         * Controlled single-person recording:
         * YOLO runs initially, then only after RTMPose confidence drops.
         */
        val detectorRan = cachedCentralBox == null

        var detectorPreprocessNs = 0L
        var detectorRunNs = 0L
        var detectorPostprocessNs = 0L
        var detectedBoxes: List<RtmPoseBoundingBox> = emptyList()

        if (detectorRan) {
            var stageStartedNs = SystemClock.elapsedRealtimeNanos()

            val detectorInput =
                RtmPoseImagePreprocessor.createDetectorInput(bitmap)

            detectorPreprocessNs =
                SystemClock.elapsedRealtimeNanos() - stageStartedNs

            stageStartedNs = SystemClock.elapsedRealtimeNanos()

            val detectorOutput =
                session.runDetector(detectorInput.tensor)

            detectorRunNs =
                SystemClock.elapsedRealtimeNanos() - stageStartedNs

            stageStartedNs = SystemClock.elapsedRealtimeNanos()

            detectedBoxes = RtmPosePostprocessor.decodeDetector(
                detections = detectorOutput.detections,
                labels = detectorOutput.labels,
                resizeRatio = detectorInput.resizeRatio,
                scoreThreshold = detectorScoreThreshold
            )

            val frameCenterX = bitmap.width * 0.5f
            val frameCenterY = bitmap.height * 0.5f

            cachedCentralBox = detectedBoxes.minByOrNull { box ->
                val boxCenterX = (box.left + box.right) * 0.5f
                val boxCenterY = (box.top + box.bottom) * 0.5f
                val normalizedX =
                    (boxCenterX - frameCenterX) / bitmap.width
                val normalizedY =
                    (boxCenterY - frameCenterY) / bitmap.height

                normalizedX * normalizedX +
                    normalizedY * normalizedY
            }

            detectorPostprocessNs =
                SystemClock.elapsedRealtimeNanos() - stageStartedNs
        }

        val selectedBoxes =
            cachedCentralBox?.let { listOf(it) }.orEmpty()

        var posePreprocessNs = 0L
        var poseRunNs = 0L
        var posePostprocessNs = 0L

        val people = selectedBoxes.map { box ->
            var stageStartedNs = SystemClock.elapsedRealtimeNanos()

            val poseInput =
                RtmPoseImagePreprocessor.createPoseInput(bitmap, box)

            posePreprocessNs +=
                SystemClock.elapsedRealtimeNanos() - stageStartedNs

            stageStartedNs = SystemClock.elapsedRealtimeNanos()

            val simcc = session.runPose(poseInput)

            poseRunNs +=
                SystemClock.elapsedRealtimeNanos() - stageStartedNs

            stageStartedNs = SystemClock.elapsedRealtimeNanos()

            val decoded = RtmPosePostprocessor.decodePose(
                simccX = simcc.simccX,
                simccY = simcc.simccY,
                boundingBox = box
            )

            posePostprocessNs +=
                SystemClock.elapsedRealtimeNanos() - stageStartedNs

            RtmPosePerson2D(
                boundingBox = box,
                coordinates = decoded.coordinates,
                scores = decoded.scores
            )
        }

        val confidentBodyJointCount =
            people.firstOrNull()
                ?.scores
                ?.take(bodyKeypointCount)
                ?.count { score ->
                    score.isFinite() &&
                        score >= bodyConfidenceThreshold
                }
                ?: 0

        /*
         * A weak cached crop is not silently retained. The next source
         * frame runs YOLO again and reacquires the central person.
         */
        if (
            people.isEmpty() ||
            confidentBodyJointCount <
                minimumConfidentBodyKeypoints
        ) {
            cachedCentralBox = null
        }
        val totalNs =
            SystemClock.elapsedRealtimeNanos() - frameStartedNs

        val detectedPeopleForLog =
            if (detectorRan) detectedBoxes.size else -1

        Log.i(
            "SafetyAI.RtmPose",
            "frame_index=$frameIndex;" +
                "detector_ran=$detectorRan;" +
                "frame_ms=${totalNs / 1_000_000.0};" +
                "detector_pre_ms=${detectorPreprocessNs / 1_000_000.0};" +
                "detector_run_ms=${detectorRunNs / 1_000_000.0};" +
                "detector_post_ms=${detectorPostprocessNs / 1_000_000.0};" +
                "detected_people=$detectedPeopleForLog;" +
                "pose_people=${selectedBoxes.size};" +
                "body_confident_joints=$confidentBodyJointCount;" +
                "bbox_cached=${cachedCentralBox != null};" +
                "pose_pre_ms=${posePreprocessNs / 1_000_000.0};" +
                "pose_run_ms=${poseRunNs / 1_000_000.0};" +
                "pose_post_ms=${posePostprocessNs / 1_000_000.0}"
        )

        return people
    }

    private data class TimedPose(
        val person: RtmPosePerson2D,
        val totalNs: Long,
        val preprocessNs: Long,
        val inferenceNs: Long,
        val postprocessNs: Long
    )

    /**
     * Speculatively processes the second frame with the cached crop.
     * If the first frame loses confidence, the second is rerun through
     * the ordinary detection path before its result is returned.
     */
    @Synchronized
    fun estimatePair(
        firstBitmap: Bitmap,
        secondBitmap: Bitmap
    ): Pair<List<RtmPosePerson2D>, List<RtmPosePerson2D>> {
        check(!closed) { "RTMPose frame estimator is closed" }
        require(!firstBitmap.isRecycled && !secondBitmap.isRecycled)

        val box = cachedCentralBox
        if (!parallelPoseEnabled || box == null) {
            return estimatePeople(firstBitmap) to
                estimatePeople(secondBitmap)
        }

        val future = parallelWorker.value.submit(Callable {
            val startedNs = SystemClock.elapsedRealtimeNanos()
            var stageNs = startedNs

            val input = RtmPoseImagePreprocessor.createPoseInput(
                secondBitmap,
                box
            )
            val preprocessNs =
                SystemClock.elapsedRealtimeNanos() - stageNs

            stageNs = SystemClock.elapsedRealtimeNanos()
            val simcc = secondSession.value.runPose(input)
            val inferenceNs =
                SystemClock.elapsedRealtimeNanos() - stageNs

            stageNs = SystemClock.elapsedRealtimeNanos()
            val decoded = RtmPosePostprocessor.decodePose(
                simccX = simcc.simccX,
                simccY = simcc.simccY,
                boundingBox = box
            )
            val postprocessNs =
                SystemClock.elapsedRealtimeNanos() - stageNs

            TimedPose(
                person = RtmPosePerson2D(
                    boundingBox = box,
                    coordinates = decoded.coordinates,
                    scores = decoded.scores
                ),
                totalNs = SystemClock.elapsedRealtimeNanos() - startedNs,
                preprocessNs = preprocessNs,
                inferenceNs = inferenceNs,
                postprocessNs = postprocessNs
            )
        })

        val firstPeople = try {
            estimatePeople(firstBitmap)
        } catch (error: Throwable) {
            try {
                future.get()
            } catch (_: Throwable) {
                // Wait for the second frame to stop using its bitmap.
            }
            throw error
        }

        val speculative = future.get()
        if (cachedCentralBox == null) {
            return firstPeople to estimatePeople(secondBitmap)
        }

        val frameIndex = processedFrameCount
        processedFrameCount++

        val confidentBodyJointCount = speculative.person.scores
            .take(bodyKeypointCount)
            .count { score ->
                score.isFinite() &&
                    score >= bodyConfidenceThreshold
            }

        if (confidentBodyJointCount < minimumConfidentBodyKeypoints) {
            cachedCentralBox = null
        }

        Log.i(
            "SafetyAI.RtmPose",
            "frame_index=$frameIndex;" +
                "detector_ran=false;" +
                "frame_ms=${speculative.totalNs / 1_000_000.0};" +
                "detector_pre_ms=0.0;" +
                "detector_run_ms=0.0;" +
                "detector_post_ms=0.0;" +
                "detected_people=-1;" +
                "pose_people=1;" +
                "body_confident_joints=$confidentBodyJointCount;" +
                "bbox_cached=${cachedCentralBox != null};" +
                "pose_pre_ms=${speculative.preprocessNs / 1_000_000.0};" +
                "pose_run_ms=${speculative.inferenceNs / 1_000_000.0};" +
                "pose_post_ms=${speculative.postprocessNs / 1_000_000.0}"
        )

        return firstPeople to listOf(speculative.person)
    }
    @Synchronized
    fun resetTemporalState() {
        processedFrameCount = 0
        cachedCentralBox = null
    }
    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            try {
                session.close()
            } finally {
                if (parallelWorker.isInitialized()) {
                    parallelWorker.value.shutdown()
                }
                if (secondSession.isInitialized()) {
                    secondSession.value.close()
                }
            }
        }
    }
}
