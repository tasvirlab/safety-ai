package com.azizbek.safetyai.analysis

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Video -> RTMPose-WB 133 -> tracked person -> canonical 21 joints -> 60 Hz.
 *
 * RTMPose runs once per original source frame:
 * - 30 FPS video: inference at 30 FPS, then 2D results are duplicated to 60 Hz.
 * - 60 FPS video: inference and output remain one-to-one.
 */
class RtmPoseVideoEstimator(
    context: Context,
    private val decoder: VideoFrameDecoder = VideoFrameDecoder(),
    private val requestedInferenceFps: Int? = FrozenPipeline.INFERENCE_FPS,
    private val useXnnpack: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            Build.MODEL == FrozenPipeline.PARALLEL_PHONE_MODEL &&
            Build.SOC_MODEL == FrozenPipeline.PARALLEL_PHONE_SOC,
    private val useParallelPose: Boolean = useXnnpack
) : AutoCloseable {
    fun executionProfile(): Map<String, String> = linkedMapOf(
        "rtmpose_backend" to if (useXnnpack) "XNNPACK" else "CPU",
        "rtmpose_parallel_sessions" to if (useParallelPose) "2" else "1",
        "requested_inference_fps" to (requestedInferenceFps?.toString() ?: "source_rate")
    )
    private val appContext = context.applicationContext
    private val runMutex = Mutex()
    private val estimatorLock = Any()

    private var frameEstimator: RtmPoseFrameEstimator? = null

    @Volatile
    private var closed = false

    suspend fun estimate(
        videoFile: File,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): VideoPose2DSequence = runMutex.withLock {
        check(!closed) {
            "RTMPose video estimator is closed"
        }

        val inspectedInfo = decoder.inspect(videoFile)

        val targetInferenceFps =
            requestedInferenceFps ?: inspectedInfo.sourceFps
        require(
            inspectedInfo.sourceFps % targetInferenceFps == 0
        ) {
            "Source FPS must be divisible by $targetInferenceFps"
        }

        val inferenceStride =
            inspectedInfo.sourceFps / targetInferenceFps

        val sampledFrames =
            arrayOfNulls<CanonicalPose2D>(
                inspectedInfo.sourceFrameCount
            )

        val tracker = RtmPosePersonTracker()
        val estimator = activeEstimator()
        estimator.resetTemporalState()

        var decodeFailureCount = 0
        var noPersonFrameCount = 0
        var inferenceCount = 0

        val pendingFrames = ArrayList<Pair<Int, Bitmap>>(2)

        fun acceptPeople(
            sourceIndex: Int,
            people: List<RtmPosePerson2D>
        ) {
            val selected = tracker.select(people)
            sampledFrames[sourceIndex] =
                if (selected == null) {
                    noPersonFrameCount++
                    invalidCanonicalPose()
                } else {
                    RtmPoseCanonical21.map(selected)
                }
        }

        fun flushPendingFrames() {
            if (pendingFrames.isEmpty()) return

            try {
                if (pendingFrames.size == 2) {
                    val results = estimator.estimatePair(
                        pendingFrames[0].second,
                        pendingFrames[1].second
                    )
                    acceptPeople(pendingFrames[0].first, results.first)
                    acceptPeople(pendingFrames[1].first, results.second)
                } else {
                    val frame = pendingFrames[0]
                    acceptPeople(
                        frame.first,
                        estimator.estimatePeople(frame.second)
                    )
                }
            } finally {
                pendingFrames.forEach { (_, bitmap) ->
                    bitmap.recycle()
                }
                pendingFrames.clear()
            }
        }

        val decodedInfo = decoder.decode(
            file = videoFile,
            onProgress = onProgress
        ) { decodedFrame ->
            val sourceIndex = decodedFrame.sourceIndex
            val bitmap = decodedFrame.bitmap

            val shouldInfer =
                sourceIndex % inferenceStride == 0 ||
                    sourceIndex ==
                    inspectedInfo.sourceFrameCount - 1

            if (bitmap == null) {
                if (useParallelPose) {
                    flushPendingFrames()
                }
                decodeFailureCount++

                if (shouldInfer) {
                    tracker.select(emptyList())
                    sampledFrames[sourceIndex] =
                        invalidCanonicalPose()
                }
            } else if (shouldInfer) {
                require(
                    bitmap.width == inspectedInfo.widthPx &&
                        bitmap.height == inspectedInfo.heightPx
                ) {
                    "Decoded frame dimensions changed: " +
                        "${bitmap.width}x${bitmap.height}, expected " +
                        "${inspectedInfo.widthPx}x" +
                        "${inspectedInfo.heightPx}"
                }

                inferenceCount++

                if (useParallelPose) {
                    val copy = bitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                    ) ?: error("Could not copy an inference frame")

                    pendingFrames += sourceIndex to copy
                    if (pendingFrames.size == 2) {
                        flushPendingFrames()
                    }
                } else {
                    acceptPeople(
                        sourceIndex,
                        estimator.estimatePeople(bitmap)
                    )
                }
            }
        }

        flushPendingFrames()
        require(
            decodedInfo.sourceFps == inspectedInfo.sourceFps &&
                decodedInfo.sourceFrameCount ==
                inspectedInfo.sourceFrameCount
        ) {
            "Video metadata changed while decoding"
        }

        require(inferenceCount > 0) {
            "No RTMPose inference frames were processed"
        }

        val sourceFrames =
            interpolateSourcePoses(sampledFrames.toList())

        require(
            sourceFrames.size ==
                inspectedInfo.sourceFrameCount
        ) {
            "Interpolated ${sourceFrames.size} frames, expected " +
                "${inspectedInfo.sourceFrameCount}"
        }

        Log.i(
            "SafetyAI.RtmPose",
            "sampling_source_fps=${inspectedInfo.sourceFps};" +
                "inference_fps=$targetInferenceFps;" +
                "inference_stride=$inferenceStride;" +
                "source_frames=${inspectedInfo.sourceFrameCount};" +
                "pose_inferences=$inferenceCount"
        )

        PoseTemporalResampler.resample(
            sourceFrames = sourceFrames,
            sourceFps = inspectedInfo.sourceFps,
            frameWidth = inspectedInfo.widthPx,
            frameHeight = inspectedInfo.heightPx,
            decodeFailureCount = decodeFailureCount,
            noPersonFrameCount = noPersonFrameCount
        )
    }

    /**
     * Restores the original source-frame timeline from 15 FPS RTMPose
     * keyframes. Exact inferred keyframes are preserved unchanged.
     */
    private fun interpolateSourcePoses(
        sampledFrames: List<CanonicalPose2D?>
    ): List<CanonicalPose2D> {
        require(sampledFrames.isNotEmpty())

        val sampledIndices =
            sampledFrames.indices.filter { index ->
                sampledFrames[index] != null
            }

        require(sampledIndices.isNotEmpty()) {
            "No sampled RTMPose frames are available"
        }

        require(sampledIndices.first() == 0) {
            "The first video frame was not sampled"
        }

        require(
            sampledIndices.last() ==
                sampledFrames.lastIndex
        ) {
            "The final video frame was not sampled"
        }

        val interpolated =
            arrayOfNulls<CanonicalPose2D>(
                sampledFrames.size
            )

        sampledIndices.forEach { index ->
            interpolated[index] = sampledFrames[index]
        }

        sampledIndices.zipWithNext().forEach {
                (startIndex, endIndex) ->

            val start = requireNotNull(
                sampledFrames[startIndex]
            )
            val end = requireNotNull(
                sampledFrames[endIndex]
            )

            val distance = endIndex - startIndex
            require(distance > 0)

            for (
                frameIndex in
                startIndex + 1 until endIndex
            ) {
                val alpha =
                    (frameIndex - startIndex).toFloat() /
                        distance.toFloat()

                val coordinates =
                    FloatArray(start.coordinates.size) {
                            coordinateIndex ->

                        val startValue =
                            start.coordinates[coordinateIndex]
                        val endValue =
                            end.coordinates[coordinateIndex]

                        if (
                            startValue.isFinite() &&
                            endValue.isFinite()
                        ) {
                            startValue +
                                (endValue - startValue) * alpha
                        } else {
                            Float.NaN
                        }
                    }

                val confidence =
                    FloatArray(start.confidence.size) {
                            jointIndex ->

                        val startValue =
                            start.confidence[jointIndex]
                        val endValue =
                            end.confidence[jointIndex]

                        if (
                            startValue.isFinite() &&
                            endValue.isFinite()
                        ) {
                            startValue +
                                (endValue - startValue) * alpha
                        } else {
                            Float.NaN
                        }
                    }

                val valid =
                    BooleanArray(start.valid.size) {
                            jointIndex ->

                        val coordinateIndex =
                            jointIndex * 2

                        start.valid[jointIndex] &&
                            end.valid[jointIndex] &&
                            coordinates[
                                coordinateIndex
                            ].isFinite() &&
                            coordinates[
                                coordinateIndex + 1
                            ].isFinite()
                    }

                interpolated[frameIndex] =
                    CanonicalPose2D(
                        coordinates = coordinates,
                        valid = valid,
                        confidence = confidence
                    )
            }
        }

        return interpolated.mapIndexed {
                frameIndex, pose ->
            pose ?: error(
                "Frame $frameIndex was not interpolated"
            )
        }
    }
    private fun activeEstimator(): RtmPoseFrameEstimator =
        synchronized(estimatorLock) {
            check(!closed) {
                "RTMPose video estimator is closed"
            }

            frameEstimator
                ?: RtmPoseFrameEstimator(
                    appContext,
                    useXnnpack,
                    useParallelPose
                ).also {
                    frameEstimator = it
                }
        }

    private fun invalidCanonicalPose(): CanonicalPose2D =
        CanonicalPose2D(
            coordinates = FloatArray(21 * 2) { Float.NaN },
            valid = BooleanArray(21),
            confidence = FloatArray(21) { Float.NaN }
        )

    override fun close() {
        synchronized(estimatorLock) {
            if (!closed) {
                closed = true
                frameEstimator?.close()
                frameEstimator = null
            }
        }
    }
}
