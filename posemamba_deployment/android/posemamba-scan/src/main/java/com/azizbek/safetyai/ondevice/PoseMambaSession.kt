package com.azizbek.safetyai.ondevice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Local FP32 3D lifter. Input must already follow the validated normalization.
 * Call on a background dispatcher. This is not a video/REBA SafetyAnalysisEngine.
 * Native registration and actual Android execution still require device tests.
 */
class PoseMambaSession(context: Context, model: File, expectedSha256: String, intraOpThreads: Int = 4) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions()
    private val session: OrtSession
    private var closed = false
    private val inputBuffer = ByteBuffer.allocateDirect(243 * 21 * 2 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    init {
        var opened: OrtSession? = null
        try {
            require(model.isFile) { "PoseMamba model file is missing" }
            require(expectedSha256.matches(Regex("[0-9a-f]{64}"))) { "Verified model SHA-256 required" }
            val digest = MessageDigest.getInstance("SHA-256")
            model.inputStream().use { input ->
                val block = ByteArray(65536)
                while (true) {
                    val count = input.read(block)
                    if (count < 0) break
                    digest.update(block, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(actual == expectedSha256) { "PoseMamba model hash mismatch" }
            val custom = File(context.applicationInfo.nativeLibraryDir, "libposemamba_scan_ort.so")
            require(custom.isFile) { "Native scan library missing; enable legacy JNI packaging" }
            require(intraOpThreads in 1..8) { "Unsupported intra-op thread count" }
            options.setIntraOpNumThreads(intraOpThreads)
            options.setInterOpNumThreads(1)
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            options.registerCustomOpLibrary(custom.absolutePath)
            opened = env.createSession(model.absolutePath, options)
            require(opened.inputNames == setOf("input_2d")) { "Unexpected model input contract" }
            session = opened
        } catch (error: Throwable) {
            opened?.close()
            options.close()
            throw error
        }
    }

    /** [frame][canonical joint][xy], normalized FP32, 243 x 21 x 2 elements.
     * Returns model-unit (metre) xyz values in the same frame/joint order.
     * Missing/NaN windows are rejected, never replaced with zero risk.
     */
    @Synchronized
    fun lift(normalized: FloatArray): FloatArray {
        check(!closed) { "Session is closed" }
        require(normalized.size == 243 * 21 * 2 && normalized.all { it.isFinite() })
        inputBuffer.clear()
        inputBuffer.put(normalized)
        inputBuffer.rewind()
        OnnxTensor.createTensor(env, inputBuffer, longArrayOf(1, 243, 21, 2)).use { input ->
            session.run(mapOf("input_2d" to input)).use { results ->
                val output = results[0] as OnnxTensor
                require(output.info.shape.contentEquals(longArrayOf(1, 243, 21, 3)))
                val buffer = output.floatBuffer ?: throw IllegalStateException("Expected FP32 output")
                val result = FloatArray(243 * 21 * 3)
                buffer.get(result)
                require(result.all { it.isFinite() }) { "Nonfinite model output" }
                return result
            }
        }
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            try { session.close() } finally { options.close() }
        }
        // OrtEnvironment is a process singleton and may serve RTMPose sessions.
    }
}



