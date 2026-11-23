package com.azizbek.safetyai.analysis

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

data class RtmPoseDetectorInput(
    val tensor: FloatArray,
    val resizeRatio: Float
)

object RtmPoseImagePreprocessor {
    private const val DETECTOR_WIDTH = 640
    private const val DETECTOR_HEIGHT = 640
    private const val POSE_WIDTH = 192
    private const val POSE_HEIGHT = 256
    private const val PADDING_VALUE = 114f
    private const val BBOX_PADDING = 1.25f

    private val mean = floatArrayOf(123.675f, 116.28f, 103.53f)
    private val std = floatArrayOf(58.395f, 57.12f, 57.375f)

    /**
     * RTMLib YOLOX preprocessing:
     * BGR, top-left letterbox, value 114, FP32 CHW, no /255 normalization.
     */
    fun createDetectorInput(bitmap: Bitmap): RtmPoseDetectorInput {
        require(bitmap.width > 0 && bitmap.height > 0)

        val sourceWidth = bitmap.width
        val sourceHeight = bitmap.height
        val pixels = IntArray(sourceWidth * sourceHeight)

        bitmap.getPixels(
            pixels,
            0,
            sourceWidth,
            0,
            0,
            sourceWidth,
            sourceHeight
        )

        val ratio = min(
            DETECTOR_HEIGHT.toFloat() / sourceHeight,
            DETECTOR_WIDTH.toFloat() / sourceWidth
        )

        val resizedWidth = (sourceWidth * ratio).toInt()
            .coerceIn(1, DETECTOR_WIDTH)
        val resizedHeight = (sourceHeight * ratio).toInt()
            .coerceIn(1, DETECTOR_HEIGHT)

        val planeSize = DETECTOR_WIDTH * DETECTOR_HEIGHT
        val tensor = FloatArray(planeSize * 3) { PADDING_VALUE }

        if (
            resizedWidth == sourceWidth &&
            resizedHeight == sourceHeight
        ) {
            for (y in 0 until resizedHeight) {
                val sourceRow = y * sourceWidth
                val destinationRow = y * DETECTOR_WIDTH

                for (x in 0 until resizedWidth) {
                    val color = pixels[sourceRow + x]
                    val destination = destinationRow + x

                    tensor[destination] =
                        (color and 0xff).toFloat()
                    tensor[planeSize + destination] =
                        ((color ushr 8) and 0xff).toFloat()
                    tensor[2 * planeSize + destination] =
                        ((color ushr 16) and 0xff).toFloat()
                }
            }
        } else {
            val x0 = IntArray(resizedWidth)
            val x1 = IntArray(resizedWidth)
            val fx = FloatArray(resizedWidth)

            for (x in 0 until resizedWidth) {
                val sourceX =
                    (x + 0.5f) * sourceWidth /
                        resizedWidth - 0.5f
                val sourceFloor = floor(sourceX)

                x0[x] = sourceFloor.toInt()
                    .coerceIn(0, sourceWidth - 1)
                x1[x] = (x0[x] + 1)
                    .coerceAtMost(sourceWidth - 1)
                fx[x] = (sourceX - sourceFloor)
                    .coerceIn(0f, 1f)
            }

            val y0 = IntArray(resizedHeight)
            val y1 = IntArray(resizedHeight)
            val fy = FloatArray(resizedHeight)

            for (y in 0 until resizedHeight) {
                val sourceY =
                    (y + 0.5f) * sourceHeight /
                        resizedHeight - 0.5f
                val sourceFloor = floor(sourceY)

                y0[y] = sourceFloor.toInt()
                    .coerceIn(0, sourceHeight - 1)
                y1[y] = (y0[y] + 1)
                    .coerceAtMost(sourceHeight - 1)
                fy[y] = (sourceY - sourceFloor)
                    .coerceIn(0f, 1f)
            }

            for (y in 0 until resizedHeight) {
                val sourceRow0 = y0[y] * sourceWidth
                val sourceRow1 = y1[y] * sourceWidth
                val destinationRow = y * DETECTOR_WIDTH
                val verticalFraction = fy[y]

                for (x in 0 until resizedWidth) {
                    val color = interpolateColor(
                        pixels[sourceRow0 + x0[x]],
                        pixels[sourceRow0 + x1[x]],
                        pixels[sourceRow1 + x0[x]],
                        pixels[sourceRow1 + x1[x]],
                        fx[x],
                        verticalFraction
                    )

                    val destination = destinationRow + x

                    tensor[destination] =
                        (color and 0xff).toFloat()
                    tensor[planeSize + destination] =
                        ((color ushr 8) and 0xff).toFloat()
                    tensor[2 * planeSize + destination] =
                        ((color ushr 16) and 0xff).toFloat()
                }
            }
        }

        require(tensor.all { it.isFinite() })
        return RtmPoseDetectorInput(tensor, ratio)
    }

    /**
     * RTMLib top-down affine preprocessing:
     * bbox padding 1.25, 192x256 aspect ratio, BGR mean/std normalization.
     */
    fun createPoseInput(
        bitmap: Bitmap,
        boundingBox: RtmPoseBoundingBox
    ): FloatArray {
        require(bitmap.width > 0 && bitmap.height > 0)

        val sourceWidth = bitmap.width
        val sourceHeight = bitmap.height
        val pixels = IntArray(sourceWidth * sourceHeight)
        bitmap.getPixels(
            pixels,
            0,
            sourceWidth,
            0,
            0,
            sourceWidth,
            sourceHeight
        )

        val centerX = (boundingBox.left + boundingBox.right) * 0.5f
        val centerY = (boundingBox.top + boundingBox.bottom) * 0.5f

        var scaleWidth =
            (boundingBox.right - boundingBox.left) * BBOX_PADDING
        var scaleHeight =
            (boundingBox.bottom - boundingBox.top) * BBOX_PADDING

        val aspectRatio = POSE_WIDTH.toFloat() / POSE_HEIGHT
        if (scaleWidth > scaleHeight * aspectRatio) {
            scaleHeight = scaleWidth / aspectRatio
        } else {
            scaleWidth = scaleHeight * aspectRatio
        }

        val planeSize = POSE_WIDTH * POSE_HEIGHT
        val tensor = FloatArray(planeSize * 3)

        for (y in 0 until POSE_HEIGHT) {
            val sourceY =
                centerY +
                    (y - POSE_HEIGHT * 0.5f) *
                    scaleHeight / POSE_HEIGHT

            for (x in 0 until POSE_WIDTH) {
                val sourceX =
                    centerX +
                        (x - POSE_WIDTH * 0.5f) *
                        scaleWidth / POSE_WIDTH

                val color = warpBilinear(
                    pixels = pixels,
                    width = sourceWidth,
                    height = sourceHeight,
                    sourceX = sourceX,
                    sourceY = sourceY
                )

                val destination = y * POSE_WIDTH + x
                val blue = (color and 0xff).toFloat()
                val green = ((color ushr 8) and 0xff).toFloat()
                val red = ((color ushr 16) and 0xff).toFloat()

                tensor[destination] = (blue - mean[0]) / std[0]
                tensor[planeSize + destination] =
                    (green - mean[1]) / std[1]
                tensor[2 * planeSize + destination] =
                    (red - mean[2]) / std[2]
            }
        }

        require(tensor.all { it.isFinite() })
        return tensor
    }

    private fun resizeBilinear(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        destinationX: Int,
        destinationY: Int,
        destinationWidth: Int,
        destinationHeight: Int
    ): Int {
        val sourceX =
            (destinationX + 0.5f) * sourceWidth / destinationWidth - 0.5f
        val sourceY =
            (destinationY + 0.5f) * sourceHeight / destinationHeight - 0.5f

        val x0 = floor(sourceX).toInt().coerceIn(0, sourceWidth - 1)
        val y0 = floor(sourceY).toInt().coerceIn(0, sourceHeight - 1)
        val x1 = (x0 + 1).coerceAtMost(sourceWidth - 1)
        val y1 = (y0 + 1).coerceAtMost(sourceHeight - 1)

        val fx = (sourceX - floor(sourceX)).coerceIn(0f, 1f)
        val fy = (sourceY - floor(sourceY)).coerceIn(0f, 1f)

        return interpolateColor(
            pixels[y0 * sourceWidth + x0],
            pixels[y0 * sourceWidth + x1],
            pixels[y1 * sourceWidth + x0],
            pixels[y1 * sourceWidth + x1],
            fx,
            fy
        )
    }

    /**
     * Approximates OpenCV INTER_LINEAR's 1/32 interpolation table and
     * BORDER_CONSTANT=0 behavior used by cv2.warpAffine.
     */
    private fun warpBilinear(
        pixels: IntArray,
        width: Int,
        height: Int,
        sourceX: Float,
        sourceY: Float
    ): Int {
        val fixedX = (sourceX * 32f).roundToInt()
        val fixedY = (sourceY * 32f).roundToInt()

        val x0 = Math.floorDiv(fixedX, 32)
        val y0 = Math.floorDiv(fixedY, 32)
        val fx = (fixedX - x0 * 32) / 32f
        val fy = (fixedY - y0 * 32) / 32f

        fun pixel(x: Int, y: Int): Int =
            if (x in 0 until width && y in 0 until height) {
                pixels[y * width + x]
            } else {
                0xff000000.toInt()
            }

        return interpolateColor(
            pixel(x0, y0),
            pixel(x0 + 1, y0),
            pixel(x0, y0 + 1),
            pixel(x0 + 1, y0 + 1),
            fx,
            fy
        )
    }

    private fun interpolateColor(
        topLeft: Int,
        topRight: Int,
        bottomLeft: Int,
        bottomRight: Int,
        fx: Float,
        fy: Float
    ): Int {
        fun channel(color: Int, shift: Int): Int =
            (color ushr shift) and 0xff

        fun interpolate(shift: Int): Int {
            val top =
                channel(topLeft, shift) * (1f - fx) +
                    channel(topRight, shift) * fx
            val bottom =
                channel(bottomLeft, shift) * (1f - fx) +
                    channel(bottomRight, shift) * fx

            return (top * (1f - fy) + bottom * fy)
                .roundToInt()
                .coerceIn(0, 255)
        }

        val red = interpolate(16)
        val green = interpolate(8)
        val blue = interpolate(0)

        return (0xff shl 24) or
            (red shl 16) or
            (green shl 8) or
            blue
    }
}