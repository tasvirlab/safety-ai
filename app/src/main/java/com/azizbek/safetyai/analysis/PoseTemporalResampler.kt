package com.azizbek.safetyai.analysis

data class VideoPose2DSequence(
    val sourceFps: Int,
    val targetFps: Int,
    val sourceFrameCount: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    /** Always 60 Hz, PoseMamba canonical joint order. */
    val frames: List<CanonicalPose2D>,
    val timestampsUs: LongArray,
    /** Target-frame to original source-frame mapping. */
    val sourceFrameIndices: IntArray,
    val decodeFailureCount: Int,
    val noPersonFrameCount: Int
) {
    init {
        require(sourceFps == 30 || sourceFps == 60)
        require(targetFps == PoseTemporalResampler.TARGET_FPS)
        require(sourceFrameCount >= 0)
        require(frameWidth > 0 && frameHeight > 0)
        require(frames.size == timestampsUs.size)
        require(frames.size == sourceFrameIndices.size)
        require(decodeFailureCount in 0..sourceFrameCount)
        require(noPersonFrameCount in 0..sourceFrameCount)
    }

    val frameCount: Int
        get() = frames.size
}

object PoseTemporalResampler {
    const val TARGET_FPS = 60

    /**
     * The PoseMamba checkpoint was trained with 60 Hz sequences.
     *
     * 60 FPS input is unchanged.
     * 30 FPS input uses zero-order hold: every RTMPose result is emitted
     * twice. RTMPose itself is still executed only once per source frame.
     */
    fun resample(
        sourceFrames: List<CanonicalPose2D>,
        sourceFps: Int,
        frameWidth: Int,
        frameHeight: Int,
        decodeFailureCount: Int = 0,
        noPersonFrameCount: Int = 0
    ): VideoPose2DSequence {
        require(sourceFps == 30 || sourceFps == 60) {
            "Only 30 or 60 FPS input is supported"
        }
        require(frameWidth > 0 && frameHeight > 0)
        require(decodeFailureCount in 0..sourceFrames.size)
        require(noPersonFrameCount in 0..sourceFrames.size)

        val repeatFactor = TARGET_FPS / sourceFps
        require(repeatFactor == 1 || repeatFactor == 2)

        val targetFrameCount =
            sourceFrames.size * repeatFactor

        val targetFrames = List(targetFrameCount) { targetIndex ->
            sourceFrames[targetIndex / repeatFactor]
        }

        val timestampsUs = LongArray(targetFrameCount) { targetIndex ->
            targetIndex * 1_000_000L / TARGET_FPS
        }

        val sourceFrameIndices =
            IntArray(targetFrameCount) { targetIndex ->
                targetIndex / repeatFactor
            }

        return VideoPose2DSequence(
            sourceFps = sourceFps,
            targetFps = TARGET_FPS,
            sourceFrameCount = sourceFrames.size,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            frames = targetFrames,
            timestampsUs = timestampsUs,
            sourceFrameIndices = sourceFrameIndices,
            decodeFailureCount = decodeFailureCount,
            noPersonFrameCount = noPersonFrameCount
        )
    }
}