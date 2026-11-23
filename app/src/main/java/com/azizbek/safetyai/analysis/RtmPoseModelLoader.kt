package com.azizbek.safetyai.analysis

import android.content.Context
import java.io.File
import java.security.MessageDigest

data class RtmPoseModelPaths(
    val detector: File,
    val pose: File
)

object RtmPoseModelLoader {

    private const val DETECTOR_ASSET =
        "models/rtmpose/yolox_m_8xb8-300e_humanart-c2c7a14a.onnx"

    private const val POSE_ASSET =
        "models/rtmpose/rtmw-dw-x-l_simcc-cocktail14_270e-256x192_20231122.onnx"

    const val DETECTOR_SHA256 =
        "3dea6513388889f0fff4b77bf7a26013600321b9eb9ceb0e9a400a82572f5f23"

    const val POSE_SHA256 =
        "9a9bc17c13ff0a1e37507f45a037a21bc410cd08374b0e664c57d0080843aa58"

    @Volatile
    private var cached: RtmPoseModelPaths? = null

    @Synchronized
    fun prepare(context: Context): RtmPoseModelPaths {
        cached?.let { paths ->
            if (paths.detector.isFile && paths.pose.isFile) {
                return paths
            }
        }

        val directory = File(
            context.applicationContext.noBackupFilesDir,
            "rtmpose"
        )

        require(directory.isDirectory || directory.mkdirs()) {
            "Cannot create RTMPose model directory"
        }

        val detector = materialize(
            context = context.applicationContext,
            assetPath = DETECTOR_ASSET,
            destination = File(directory, "detector.onnx"),
            expectedSha256 = DETECTOR_SHA256
        )

        val pose = materialize(
            context = context.applicationContext,
            assetPath = POSE_ASSET,
            destination = File(directory, "pose.onnx"),
            expectedSha256 = POSE_SHA256
        )

        return RtmPoseModelPaths(detector, pose).also {
            cached = it
        }
    }

    private fun materialize(
        context: Context,
        assetPath: String,
        destination: File,
        expectedSha256: String
    ): File {
        if (
            destination.isFile &&
            sha256(destination) == expectedSha256
        ) {
            return destination
        }

        val temporary = File(
            destination.parentFile,
            "${destination.name}.tmp"
        )

        if (temporary.exists()) {
            require(temporary.delete()) {
                "Cannot clear temporary RTMPose model"
            }
        }

        try {
            val digest = MessageDigest.getInstance("SHA-256")

            context.assets.open(assetPath).use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)

                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break

                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
            }

            val actualSha256 = digest.digest().toHex()
            require(actualSha256 == expectedSha256) {
                "RTMPose asset hash mismatch: $assetPath"
            }

            if (destination.exists()) {
                require(destination.delete()) {
                    "Cannot replace invalid RTMPose model"
                }
            }

            require(temporary.renameTo(destination)) {
                "Cannot install RTMPose model"
            }

            return destination
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)

            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }

        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") {
            "%02x".format(it.toInt() and 0xff)
        }
}
