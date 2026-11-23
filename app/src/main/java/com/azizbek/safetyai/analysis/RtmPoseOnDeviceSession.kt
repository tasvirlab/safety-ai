package com.azizbek.safetyai.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class RtmPoseDetectorOutput(
    val detections: FloatArray,
    val labels: LongArray
)

data class RtmPoseSimccOutput(
    val simccX: FloatArray,
    val simccY: FloatArray
)

class RtmPoseOnDeviceSession(
    context: Context,
    private val useXnnpack: Boolean = false,
    private val xnnpackThreads: Int = 2
) : AutoCloseable {

    private val environment = OrtEnvironment.getEnvironment()
    private val detectorOptions = createOptions()
    private val poseOptions = createOptions(useXnnpack, xnnpackThreads)

    private val detectorSession: OrtSession
    private val poseSession: OrtSession

    private var closed = false

    init {
        require(xnnpackThreads in 1..8)
        val models = RtmPoseModelLoader.prepare(context)

        var openedDetector: OrtSession? = null
        var openedPose: OrtSession? = null

        try {
            openedDetector = environment.createSession(
                models.detector.absolutePath,
                detectorOptions
            )

            require(openedDetector.inputNames == setOf("input")) {
                "Unexpected YOLOX input contract"
            }

            require(
                openedDetector.outputNames ==
                        setOf("dets", "labels")
            ) {
                "Unexpected YOLOX output contract"
            }

            openedPose = environment.createSession(
                models.pose.absolutePath,
                poseOptions
            )

            require(openedPose.inputNames == setOf("input")) {
                "Unexpected RTMPose input contract"
            }

            require(
                openedPose.outputNames ==
                        setOf("simcc_x", "simcc_y")
            ) {
                "Unexpected RTMPose output contract"
            }

            detectorSession = openedDetector
            poseSession = openedPose
        } catch (error: Throwable) {
            openedPose?.close()
            openedDetector?.close()
            poseOptions.close()
            detectorOptions.close()
            throw error
        }
    }

    @Synchronized
    fun runDetector(input: FloatArray): RtmPoseDetectorOutput {
        check(!closed) { "RTMPose session is closed" }

        require(input.size == 1 * 3 * 640 * 640) {
            "YOLOX input must contain 1x3x640x640 FP32 values"
        }

        require(input.all { it.isFinite() }) {
            "YOLOX input contains nonfinite values"
        }

        createTensor(
            input,
            longArrayOf(1, 3, 640, 640)
        ).use { tensor ->
            detectorSession.run(
                mapOf("input" to tensor)
            ).use { outputs ->
                val detections = readFloats(
                    outputs[0] as OnnxTensor
                )

                val labels = readLongs(
                    outputs[1] as OnnxTensor
                )

                require(detections.all { it.isFinite() }) {
                    "YOLOX returned nonfinite values"
                }

                return RtmPoseDetectorOutput(
                    detections = detections,
                    labels = labels
                )
            }
        }
    }

    @Synchronized
    fun runPose(input: FloatArray): RtmPoseSimccOutput {
        check(!closed) { "RTMPose session is closed" }

        require(input.size == 1 * 3 * 256 * 192) {
            "RTMPose input must contain 1x3x256x192 FP32 values"
        }

        require(input.all { it.isFinite() }) {
            "RTMPose input contains nonfinite values"
        }

        createTensor(
            input,
            longArrayOf(1, 3, 256, 192)
        ).use { tensor ->
            poseSession.run(
                mapOf("input" to tensor)
            ).use { outputs ->
                val simccX = readFloats(
                    outputs[0] as OnnxTensor
                )

                val simccY = readFloats(
                    outputs[1] as OnnxTensor
                )

                require(simccX.all { it.isFinite() }) {
                    "RTMPose simcc_x contains nonfinite values"
                }

                require(simccY.all { it.isFinite() }) {
                    "RTMPose simcc_y contains nonfinite values"
                }

                return RtmPoseSimccOutput(
                    simccX = simccX,
                    simccY = simccY
                )
            }
        }
    }

    private fun createTensor(
        values: FloatArray,
        shape: LongArray
    ): OnnxTensor {
        val buffer = ByteBuffer
            .allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        buffer.put(values)
        buffer.rewind()

        return OnnxTensor.createTensor(
            environment,
            buffer,
            shape
        )
    }

    private fun readFloats(tensor: OnnxTensor): FloatArray {
        val buffer = tensor.floatBuffer
            ?: error("Expected FP32 ONNX output")

        return FloatArray(buffer.remaining()).also {
            buffer.get(it)
        }
    }

    private fun readLongs(tensor: OnnxTensor): LongArray {
        val buffer = tensor.longBuffer
            ?: error("Expected INT64 ONNX output")

        return LongArray(buffer.remaining()).also {
            buffer.get(it)
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true

        try {
            detectorSession.close()
        } finally {
            try {
                poseSession.close()
            } finally {
                try {
                    detectorOptions.close()
                } finally {
                    poseOptions.close()
                }
            }
        }
    }

    private companion object {
        fun createOptions(
            useXnnpack: Boolean = false,
            xnnpackThreads: Int = 2
        ): OrtSession.SessionOptions =
            OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(if (useXnnpack) 1 else 4)
                if (useXnnpack) {
                    addXnnpack(mapOf("intra_op_num_threads" to xnnpackThreads.toString()))
                    addConfigEntry("session.intra_op.allow_spinning", "0")
                }
                setInterOpNumThreads(1)
                setExecutionMode(
                    OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL
                )
            }
    }
}