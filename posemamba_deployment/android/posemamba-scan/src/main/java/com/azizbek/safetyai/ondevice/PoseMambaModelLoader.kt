package com.azizbek.safetyai.ondevice

import android.content.Context
import java.io.File
import java.security.MessageDigest

object PoseMambaModelLoader {
    const val MODEL_SHA256 =
        "9313221f443d5f1f8b3169ef828e73da804d04b46941d629e8934671262d8c16"

    private const val ASSET_PATH = "model/posemamba.onnx"

    /**
     * Copies the verified model from APK assets into app-private storage
     * and opens the on-device PoseMamba session.
     * Call from a background dispatcher.
     */
    @Synchronized
    fun open(context: Context, intraOpThreads: Int = 4): PoseMambaSession {
        val appContext = context.applicationContext
        val modelDirectory = File(appContext.noBackupFilesDir, "posemamba")

        require(
            modelDirectory.isDirectory ||
                    (!modelDirectory.exists() && modelDirectory.mkdirs())
        ) {
            "Cannot create PoseMamba model directory"
        }

        val model = File(modelDirectory, "posemamba.onnx")

        if (!model.isFile || sha256(model) != MODEL_SHA256) {
            installModel(appContext, model)
        }

        return PoseMambaSession(
            context = appContext,
            model = model,
            expectedSha256 = MODEL_SHA256,
            intraOpThreads = intraOpThreads
        )
    }

    private fun installModel(context: Context, target: File) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.delete()

        context.assets.open(ASSET_PATH).use { input ->
            temporary.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (target.exists()) {
            require(target.delete()) {
                "Cannot replace existing PoseMamba model"
            }
        }

        require(temporary.renameTo(target)) {
            "Cannot install PoseMamba model"
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        file.inputStream().use { input ->
            val buffer = ByteArray(65_536)

            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }

        return digest.digest().joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }
}