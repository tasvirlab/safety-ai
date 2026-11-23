package com.azizbek.safetyai.analysis

/** Temporal 2D repair for short isolated occlusions after resampling to 60 Hz. */
object Pose2DGapRepair {
    const val MAX_INTERPOLATION_FRAMES = 60
    const val MAX_EDGE_HOLD_FRAMES = 10

    data class Result(
        val frames: List<CanonicalPose2D>,
        /** Bit j identifies a joint imputed in this frame. Observations stay untouched. */
        val imputedMaskByFrame: IntArray,
        val imputedCounts: IntArray,
        val longestImputedGap: IntArray,
        val remainingIncompleteFrames: Int
    )

    fun repair(input: List<CanonicalPose2D>): Result {
        val count = input.size
        val coordinates = Array(count) { input[it].coordinates.copyOf() }
        val valid = Array(count) { frame ->
            BooleanArray(21) { joint ->
                input[frame].valid[joint] &&
                    input[frame].coordinates[joint * 2].isFinite() &&
                    input[frame].coordinates[joint * 2 + 1].isFinite()
            }
        }
        val confidence = Array(count) { input[it].confidence.copyOf() }
        val masks = IntArray(count)
        val imputedCounts = IntArray(21)
        val longest = IntArray(21)

        fun observed(frame: Int, joint: Int): Boolean =
            input[frame].valid[joint] &&
                input[frame].coordinates[joint * 2].isFinite() &&
                input[frame].coordinates[joint * 2 + 1].isFinite()

        for (joint in 0 until 21) {
            var cursor = 0
            while (cursor < count) {
                if (observed(cursor, joint)) {
                    cursor++
                    continue
                }
                val start = cursor
                while (cursor < count && !observed(cursor, joint)) cursor++
                val end = cursor // exclusive
                val length = end - start
                val before = start - 1
                val after = end
                val twoSided = before >= 0 && after < count
                if (twoSided && length > MAX_INTERPOLATION_FRAMES) continue
                if (!twoSided && length > MAX_EDGE_HOLD_FRAMES) continue
                if (before < 0 && after >= count) continue // no measurement anywhere

                val x0 = if (before >= 0) input[before].coordinates[joint * 2]
                    else input[after].coordinates[joint * 2]
                val y0 = if (before >= 0) input[before].coordinates[joint * 2 + 1]
                    else input[after].coordinates[joint * 2 + 1]
                val x1 = if (after < count) input[after].coordinates[joint * 2] else x0
                val y1 = if (after < count) input[after].coordinates[joint * 2 + 1] else y0

                for (frame in start until end) {
                    // Two observed boundary frames: interpolate without overshoot.
                    // At the clip edge, a single observed boundary is held for <=10 frames.
                    val alpha = if (twoSided) (frame - before).toFloat() /
                        (after - before).toFloat() else 0f
                    coordinates[frame][joint * 2] = x0 + (x1 - x0) * alpha
                    coordinates[frame][joint * 2 + 1] = y0 + (y1 - y0) * alpha
                    confidence[frame][joint] = 0f // never claim detector confidence
                    valid[frame][joint] = true // finite input for PoseMamba only
                    masks[frame] = masks[frame] or (1 shl joint)
                    imputedCounts[joint]++
                }
                longest[joint] = maxOf(longest[joint], length)
            }
        }

        return Result(
            frames = List(count) { frame ->
                CanonicalPose2D(coordinates[frame], valid[frame], confidence[frame])
            },
            imputedMaskByFrame = masks,
            imputedCounts = imputedCounts,
            longestImputedGap = longest,
            remainingIncompleteFrames = valid.count { joints -> joints.any { !it } }
        )
    }
}
