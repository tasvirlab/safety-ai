package com.azizbek.safetyai.ondevice

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class PoseMambaModelInstrumentedTest {
    @Test fun golden3dWindows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val assets = instrumentation.context.assets
        val meta = JSONObject(assets.open("model/model_manifest.json").bufferedReader().use { it.readText() })
        val model = File(context.cacheDir, "posemamba_device_test.onnx")
        assets.open("model/posemamba.onnx").use { input -> model.outputStream().use { input.copyTo(it) } }
        fun floats(path: String): FloatArray {
            val bytes = assets.open(path).use { it.readBytes() }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            return FloatArray(buffer.remaining()).also { buffer.get(it) }
        }
        val inputs = floats("model/input.bin")
        val expected = floats("model/expected.bin")
        val count = meta.getInt("n_windows")
        require(count >= 2 && inputs.size == count * 10206 && expected.size == count * 15309)
        try {
            PoseMambaSession(context, model, meta.getString("model_sha256")).use { session ->
                for (i in 0 until count) {
                    val actual = session.lift(inputs.copyOfRange(i * 10206, (i + 1) * 10206))
                    var bad = 0
                    for (j in actual.indices) {
                        val target = expected[i * 15309 + j]
                        if (abs(actual[j] - target) > 1e-4f + 1e-4f * abs(target)) bad++
                    }
                    assertTrue("3D golden window $i differs in $bad coordinates", bad == 0)
                }
            }
        } finally { model.delete() }
    }
}
