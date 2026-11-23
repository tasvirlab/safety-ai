package com.azizbek.safetyai.analysis

data class NormalizedPose2DSequence(
    val frameCount: Int,
    /** Flat [frame][joint][xy], PoseMamba canonical order. */
    val coordinates: FloatArray,
    val frameValid: BooleanArray
) {
    init {
        require(frameCount >= 0)
        require(coordinates.size == frameCount * 21 * 2)
        require(frameValid.size == frameCount)
    }
}

data class PoseMambaInputWindow(
    val startFrame: Int,
    /** Flat [243][21][2]. May contain NaN only when [valid] is false. */
    val input: FloatArray,
    val valid: Boolean
) {
    init {
        require(startFrame >= 0)
        require(input.size == PoseMambaTemporalInput.WINDOW_SIZE * 21 * 2)
    }
}

object PoseMambaTemporalInput {
    const val WINDOW_SIZE = 243
    private const val JOINT_COUNT = 21
    private const val COORDINATE_COUNT = 2
    private const val VALUES_PER_FRAME =
        JOINT_COUNT * COORDINATE_COUNT

    /**
     * Exact vendored normalize_2d contract:
     * subtract PELCEN (index 3), then divide by frameWidth / 2.
     */
    fun normalize(
        frames: List<CanonicalPose2D>,
        frameWidth: Int
    ): NormalizedPose2DSequence {
        require(frameWidth > 0)

        val frameCount = frames.size
        val normalized =
            FloatArray(frameCount * VALUES_PER_FRAME) { Float.NaN }
        val frameValid = BooleanArray(frameCount)
        val divisor = frameWidth / 2f
        val pelvisIndex = RtmPoseCanonical21.ROOT_INDEX

        for (frameIndex in frames.indices) {
            val source = frames[frameIndex].coordinates
            val pelvisX = source[pelvisIndex * 2]
            val pelvisY = source[pelvisIndex * 2 + 1]
            val pelvisValid = pelvisX.isFinite() && pelvisY.isFinite()

            var allJointsFinite = true
            val destinationOffset = frameIndex * VALUES_PER_FRAME

            for (joint in 0 until JOINT_COUNT) {
                val x = source[joint * 2]
                val y = source[joint * 2 + 1]

                if (!x.isFinite() || !y.isFinite()) {
                    allJointsFinite = false
                }

                normalized[destinationOffset + joint * 2] =
                    (x - pelvisX) / divisor
                normalized[destinationOffset + joint * 2 + 1] =
                    (y - pelvisY) / divisor
            }

            frameValid[frameIndex] =
                allJointsFinite && pelvisValid
        }

        return NormalizedPose2DSequence(
            frameCount = frameCount,
            coordinates = normalized,
            frameValid = frameValid
        )
    }

    /**
     * Sequential non-overlapping windows plus one backward-aligned tail.
     */
    fun windowStarts(
        frameCount: Int,
        windowSize: Int = WINDOW_SIZE
    ): IntArray {
        require(windowSize > 0)
        require(frameCount >= windowSize) {
            "Video has $frameCount frames, needs at least $windowSize"
        }

        val starts = mutableListOf<Int>()
        var start = 0

        while (start <= frameCount - windowSize) {
            starts += start
            start += windowSize
        }

        val coveredUntil = starts.last() + windowSize
        if (coveredUntil < frameCount) {
            starts += frameCount - windowSize
        }

        return starts.toIntArray()
    }

    fun createWindows(
        sequence: NormalizedPose2DSequence
    ): List<PoseMambaInputWindow> {
        val starts = windowStarts(sequence.frameCount)
        val valuesPerWindow =
            WINDOW_SIZE * VALUES_PER_FRAME

        return starts.map { startFrame ->
            val input = FloatArray(valuesPerWindow)
            val sourceOffset = startFrame * VALUES_PER_FRAME

            System.arraycopy(
                sequence.coordinates,
                sourceOffset,
                input,
                0,
                valuesPerWindow
            )

            var valid = true
            for (frame in startFrame until startFrame + WINDOW_SIZE) {
                if (!sequence.frameValid[frame]) {
                    valid = false
                    break
                }
            }

            PoseMambaInputWindow(
                startFrame = startFrame,
                input = input,
                valid = valid
            )
        }
    }
}