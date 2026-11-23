package com.azizbek.safetyai.analysis

data class RtmPoseBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val label: Long
)

data class RtmPoseDecodedPose(
    /** Flat x,y coordinates: [x0, y0, x1, y1, ...]. */
    val coordinates: FloatArray,
    val scores: FloatArray
)

object RtmPosePostprocessor {
    private const val DETECTION_SIZE = 5
    private const val POSE_WIDTH = 192f
    private const val POSE_HEIGHT = 256f
    private const val SIMCC_X_BINS = 384
    private const val SIMCC_Y_BINS = 512
    private const val SIMCC_SPLIT_RATIO = 2f
    private const val BBOX_PADDING = 1.25f

    fun decodeDetector(
        detections: FloatArray,
        labels: LongArray,
        resizeRatio: Float,
        scoreThreshold: Float = 0.3f
    ): List<RtmPoseBoundingBox> {
        require(resizeRatio.isFinite() && resizeRatio > 0f)
        require(detections.size % DETECTION_SIZE == 0)

        val count = detections.size / DETECTION_SIZE
        require(labels.size == count)

        return buildList {
            for (index in 0 until count) {
                val offset = index * DETECTION_SIZE
                val score = detections[offset + 4]

                if (!score.isFinite() || score <= scoreThreshold) continue

                val left = detections[offset] / resizeRatio
                val top = detections[offset + 1] / resizeRatio
                val right = detections[offset + 2] / resizeRatio
                val bottom = detections[offset + 3] / resizeRatio

                if (
                    left.isFinite() &&
                    top.isFinite() &&
                    right.isFinite() &&
                    bottom.isFinite() &&
                    right > left &&
                    bottom > top
                ) {
                    add(
                        RtmPoseBoundingBox(
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                            score = score,
                            label = labels[index]
                        )
                    )
                }
            }
        }
    }

    fun decodePose(
        simccX: FloatArray,
        simccY: FloatArray,
        boundingBox: RtmPoseBoundingBox
    ): RtmPoseDecodedPose {
        require(simccX.size % SIMCC_X_BINS == 0)
        require(simccY.size % SIMCC_Y_BINS == 0)

        val keypointCountX = simccX.size / SIMCC_X_BINS
        val keypointCountY = simccY.size / SIMCC_Y_BINS
        require(keypointCountX == keypointCountY)
        require(keypointCountX > 0)

        val centerX = (boundingBox.left + boundingBox.right) * 0.5f
        val centerY = (boundingBox.top + boundingBox.bottom) * 0.5f

        var scaleWidth =
            (boundingBox.right - boundingBox.left) * BBOX_PADDING
        var scaleHeight =
            (boundingBox.bottom - boundingBox.top) * BBOX_PADDING

        val aspectRatio = POSE_WIDTH / POSE_HEIGHT
        if (scaleWidth > scaleHeight * aspectRatio) {
            scaleHeight = scaleWidth / aspectRatio
        } else {
            scaleWidth = scaleHeight * aspectRatio
        }

        val coordinates = FloatArray(keypointCountX * 2)
        val scores = FloatArray(keypointCountX)

        for (keypoint in 0 until keypointCountX) {
            val xOffset = keypoint * SIMCC_X_BINS
            val yOffset = keypoint * SIMCC_Y_BINS

            var bestX = 0
            var bestY = 0
            var maxX = simccX[xOffset]
            var maxY = simccY[yOffset]

            for (index in 1 until SIMCC_X_BINS) {
                val value = simccX[xOffset + index]
                if (value > maxX) {
                    maxX = value
                    bestX = index
                }
            }

            for (index in 1 until SIMCC_Y_BINS) {
                val value = simccY[yOffset + index]
                if (value > maxY) {
                    maxY = value
                    bestY = index
                }
            }

            val score = 0.5f * (maxX + maxY)
            scores[keypoint] = score

            var poseX =
                if (score > 0f) bestX.toFloat() else -1f
            var poseY =
                if (score > 0f) bestY.toFloat() else -1f

            poseX /= SIMCC_SPLIT_RATIO
            poseY /= SIMCC_SPLIT_RATIO

            coordinates[keypoint * 2] =
                poseX / POSE_WIDTH * scaleWidth +
                    centerX - scaleWidth * 0.5f

            coordinates[keypoint * 2 + 1] =
                poseY / POSE_HEIGHT * scaleHeight +
                    centerY - scaleHeight * 0.5f
        }

        require(coordinates.all { it.isFinite() })
        require(scores.all { it.isFinite() })

        return RtmPoseDecodedPose(
            coordinates = coordinates,
            scores = scores
        )
    }
}