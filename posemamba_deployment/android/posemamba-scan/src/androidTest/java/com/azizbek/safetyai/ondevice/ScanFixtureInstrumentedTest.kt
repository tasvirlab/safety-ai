package com.azizbek.safetyai.ondevice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class ScanFixtureInstrumentedTest {
    @Test fun uploadedCudaFixtures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val context = instrumentation.targetContext
        val env = OrtEnvironment.getEnvironment()
        val model = assets.open("scan_fixture.onnx").use { it.readBytes() }
        val custom = File(context.applicationInfo.nativeLibraryDir, "libposemamba_scan_ort.so")
        assertTrue("Custom library missing", custom.isFile)
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1); options.setInterOpNumThreads(1)
            options.registerCustomOpLibrary(custom.absolutePath)
            env.createSession(model, options).use { session ->
                for (name in listOf("small_random", "medium_random", "real_capture")) {
                    val root = "fixtures/$name/"
                    val manifest = JSONObject(assets.open(root + "manifest.json").bufferedReader().use { it.readText() })
                    val tensors = manifest.getJSONObject("tensors")
                    val inputs = mutableMapOf<String, OnnxTensor>()
                    try {
                        for ((onnxName, fixtureName) in mapOf("u" to "u", "delta" to "delta", "A" to "A", "B" to "B", "C" to "C", "skip" to "D", "bias" to "delta_bias")) {
                            val spec = tensors.getJSONObject(fixtureName)
                            val dims = spec.getJSONArray("shape")
                            val shape = LongArray(dims.length()) { dims.getLong(it) }
                            val bytes = assets.open(root + spec.getString("file")).use { it.readBytes() }
                            require(bytes.size.toLong() == shape.fold(1L) { a, b -> a * b } * 4L)
                            // Source fixture is explicitly little endian; Android supported ABIs are little endian.
                            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN)
                            buffer.put(bytes).rewind()
                            inputs[onnxName] = OnnxTensor.createTensor(env, buffer.asFloatBuffer(), shape)
                        }
                        val targetSpec = tensors.getJSONObject("out_cuda")
                        val bytes = assets.open(root + targetSpec.getString("file")).use { it.readBytes() }
                        val expectedBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                        val expected = FloatArray(expectedBuffer.remaining()); expectedBuffer.get(expected)
                        var maxDiff = 0.0; var meanDiff = 0.0; var violations = 0
                        session.run(inputs).use { output ->
                            val actualBuffer = (output[0] as OnnxTensor).floatBuffer ?: throw AssertionError("Expected FP32 output")
                            require(actualBuffer.remaining() == expected.size)
                            for (i in expected.indices) {
                                val value = actualBuffer.get().toDouble()
                                val error = abs(value - expected[i])
                                if (!value.isFinite() || error > 2e-5 + 2e-4 * abs(expected[i])) violations++
                                maxDiff = maxOf(maxDiff, error); meanDiff += error
                            }
                        }
                        val times = (0 until 5).map {
                            val start = SystemClock.elapsedRealtimeNanos()
                            session.run(inputs).use { }
                            (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                        }
                        Log.i("PoseMambaScan", JSONObject().put("fixture", name)
                            .put("device", Build.MODEL).put("abi", Build.SUPPORTED_ABIS.first())
                            .put("android", Build.VERSION.SDK_INT).put("max_abs", maxDiff)
                            .put("mean_abs", meanDiff / expected.size).put("violations", violations)
                            .put("warm_median_ms", times.sorted()[2])
                            .put("pss_kib", Debug.getPss()).toString())
                        assertTrue("CUDA operator gate failed for $name: $violations", violations == 0)
                    } finally { inputs.values.forEach { it.close() } }
                }
            }
        }
    }
}

