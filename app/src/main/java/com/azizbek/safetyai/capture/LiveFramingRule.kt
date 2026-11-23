package com.azizbek.safetyai.capture

/** A framing check, not a measurement of camera-to-worker distance. */
data class FramingFeedback(val ready: Boolean, val message: String)

internal object LiveFramingRule {
    private const val EDGE_MARGIN = 0.07f
    private const val JOINT_MARGIN = 0.05f
    private const val MIN_BODY_HEIGHT = 0.45f
    private const val MAX_BODY_HEIGHT = 0.82f

    fun evaluate(
        width: Int,
        height: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        joints: FloatArray,
        valid: BooleanArray
    ): FramingFeedback {
        require(width > 0 && height > 0 && joints.size == 42 && valid.size == 21)
        if (!floatArrayOf(left, top, right, bottom).all { it.isFinite() } ||
            right <= left || bottom <= top) {
            return FramingFeedback(false, "Keep one worker fully visible")
        }

        val l = left / width
        val r = right / width
        val t = top / height
        val b = bottom / height
        val bodyHeight = b - t
        if (l < EDGE_MARGIN || r > 1f - EDGE_MARGIN ||
            t < EDGE_MARGIN || b > 1f - EDGE_MARGIN ||
            bodyHeight > MAX_BODY_HEIGHT) {
            return FramingFeedback(false, "Move back so your whole body fits")
        }
        if (bodyHeight < MIN_BODY_HEIGHT) {
            return FramingFeedback(false, "Move closer while keeping your whole body visible")
        }

        if (valid.any { !it }) {
            return FramingFeedback(false, "Show both hands and feet clearly")
        }
        for (joint in 0 until 21) {
            val x = joints[joint * 2] / width
            val y = joints[joint * 2 + 1] / height
            if (!x.isFinite() || !y.isFinite() ||
                x < JOINT_MARGIN || x > 1f - JOINT_MARGIN ||
                y < JOINT_MARGIN || y > 1f - JOINT_MARGIN) {
                return FramingFeedback(false, "Keep your whole body inside the frame")
            }
        }
        return FramingFeedback(true, "Full body visible. Keep this position")
    }
}
