package com.azizbek.safetyai.analysis

import kotlin.math.sqrt

/** Conservative check for simultaneous 2D and reconstructed 3D shoulder collapse. */
internal data class PoseGeometryQuality(
    val checkedFrames: Int,
    val collapsedFrames: Int
) {
    val unreliable: Boolean
        get() = checkedFrames >= 120 && collapsedFrames * 2 > checkedFrames

    companion object {
        fun inspect(
            pose2D: VideoPose2DSequence,
            pose3D: PoseMamba3DSequence
        ): PoseGeometryQuality {
            var checked = 0
            var collapsed = 0

            for (frame in 0 until minOf(pose2D.frameCount, pose3D.frameCount)) {
                if (!pose3D.frameValid[frame]) continue
                val points2D = pose2D.frames[frame].coordinates
                val points3D = pose3D.coordinates

                fun distance2D(a: Int, b: Int): Double {
                    val dx = (points2D[a * 2] - points2D[b * 2]).toDouble()
                    val dy = (points2D[a * 2 + 1] - points2D[b * 2 + 1]).toDouble()
                    return sqrt(dx * dx + dy * dy)
                }

                fun distance3D(a: Int, b: Int): Double {
                    val offset = frame * 21 * 3
                    val dx = (points3D[offset + a * 3] - points3D[offset + b * 3]).toDouble()
                    val dy = (points3D[offset + a * 3 + 1] - points3D[offset + b * 3 + 1]).toDouble()
                    val dz = (points3D[offset + a * 3 + 2] - points3D[offset + b * 3 + 2]).toDouble()
                    return sqrt(dx * dx + dy * dy + dz * dz)
                }

                val torso2D = distance2D(1, 3)
                val torso3D = distance3D(1, 3)
                val shoulders2D = distance2D(4, 5)
                val shoulders3D = distance3D(4, 5)
                if (!torso2D.isFinite() || !torso3D.isFinite() ||
                    !shoulders2D.isFinite() || !shoulders3D.isFinite() ||
                    torso2D <= 1.0 || torso3D <= 0.05
                ) continue

                checked++
                if (shoulders2D / torso2D < 0.15 &&
                    shoulders3D / torso3D < 0.25
                ) collapsed++
            }

            return PoseGeometryQuality(checked, collapsed)
        }
    }
}
