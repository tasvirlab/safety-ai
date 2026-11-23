package com.azizbek.safetyai.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.res.AssetManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

class RtmPoseRawInstrumentedTest {

    private fun copyAsset(
        assets: AssetManager,
        assetPath: String,
        output: File
    ): File {
        assets.open(assetPath).use { input ->
            output.outputStream().use { input.copyTo(it) }
        }
        return output
    }

    private fun readFloats(
        assets: AssetManager,
        path: String
    ): FloatArray {
        val bytes = assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()

        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun readLongs(
        assets: AssetManager,
        path: String
    ): LongArray {
        val bytes = assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asLongBuffer()

        return LongArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun createTensor(
        env: OrtEnvironment,
        values: FloatArray,
        shape: LongArray
    ): OnnxTensor {
        val buffer = ByteBuffer
            .allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        buffer.put(values)
        buffer.rewind()
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    private fun tensorFloats(tensor: OnnxTensor): FloatArray {
        val buffer = tensor.floatBuffer ?: error("Expected FP32 tensor")
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun tensorLongs(tensor: OnnxTensor): LongArray {
        val buffer = tensor.longBuffer ?: error("Expected INT64 tensor")
        return LongArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun assertClose(
        name: String,
        actual: FloatArray,
        expected: FloatArray
    ) {
        assertEquals("$name size", expected.size, actual.size)

        var violations = 0
        var maxAbs = 0.0f

        for (i in expected.indices) {
            val error = abs(actual[i] - expected[i])
            maxAbs = max(maxAbs, error)

            val tolerance = 1e-4f + 1e-4f * abs(expected[i])
            if (!actual[i].isFinite() || error > tolerance) {
                violations++
            }
        }

        println("$name maxAbs=$maxAbs violations=$violations")
        assertEquals("$name parity failed", 0, violations)
    }

    @Test
    fun rawOnnxMatchesHostGolden() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val mainAssets = context.assets
        val testAssets = instrumentation.context.assets
        val env = OrtEnvironment.getEnvironment()

        val detectorModel = copyAsset(
            mainAssets,
            "models/rtmpose/yolox_m_8xb8-300e_humanart-c2c7a14a.onnx",
            File(context.cacheDir, "rtmpose_detector_test.onnx")
        )

        val poseModel = copyAsset(
            mainAssets,
            "models/rtmpose/rtmw-dw-x-l_simcc-cocktail14_270e-256x192_20231122.onnx",
            File(context.cacheDir, "rtmpose_pose_test.onnx")
        )

        try {
            val detectorInput = readFloats(
                testAssets,
                "rtmpose_golden/detector_input_f32.bin"
            )
            val expectedDets = readFloats(
                testAssets,
                "rtmpose_golden/detector_dets_f32.bin"
            )
            val expectedLabels = readLongs(
                testAssets,
                "rtmpose_golden/detector_labels_i64.bin"
            )

            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(1)
                options.setInterOpNumThreads(1)
                options.setExecutionMode(
                    OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL
                )

                env.createSession(detectorModel.absolutePath, options).use { session ->
                    createTensor(
                        env,
                        detectorInput,
                        longArrayOf(1, 3, 640, 640)
                    ).use { input ->
                        session.run(mapOf("input" to input)).use { outputs ->
                            val actualDets =
                                tensorFloats(outputs[0] as OnnxTensor)
                            val actualLabels =
                                tensorLongs(outputs[1] as OnnxTensor)

                            assertClose(
                                "YOLOX dets",
                                actualDets,
                                expectedDets
                            )
                            assertArrayEquals(
                                "YOLOX labels",
                                expectedLabels,
                                actualLabels
                            )
                        }
                    }
                }
            }

            val poseInput = readFloats(
                testAssets,
                "rtmpose_golden/pose_input_f32.bin"
            )
            val expectedX = readFloats(
                testAssets,
                "rtmpose_golden/simcc_x_f32.bin"
            )
            val expectedY = readFloats(
                testAssets,
                "rtmpose_golden/simcc_y_f32.bin"
            )

            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(1)
                options.setInterOpNumThreads(1)
                options.setExecutionMode(
                    OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL
                )

                env.createSession(poseModel.absolutePath, options).use { session ->
                    createTensor(
                        env,
                        poseInput,
                        longArrayOf(1, 3, 256, 192)
                    ).use { input ->
                        session.run(mapOf("input" to input)).use { outputs ->
                            assertClose(
                                "RTMPose simcc_x",
                                tensorFloats(outputs[0] as OnnxTensor),
                                expectedX
                            )
                            assertClose(
                                "RTMPose simcc_y",
                                tensorFloats(outputs[1] as OnnxTensor),
                                expectedY
                            )
                        }
                    }
                }
            }

            println("ANDROID RTMPOSE RAW ONNX: PASS")
        } finally {
            detectorModel.delete()
            poseModel.delete()
        }
    }
}