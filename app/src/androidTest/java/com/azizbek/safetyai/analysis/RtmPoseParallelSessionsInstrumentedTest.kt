package com.azizbek.safetyai.analysis

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RtmPoseParallelSessionsInstrumentedTest {
    @Test
    fun compareTwoPoseSessionsOnRealFrames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val video = File(
            context.filesDir,
            "videos/2a048946-9671-4c3d-b1bf-13de9727a71d.mp4"
        )
        require(video.isFile && video.length() > 0L) {
            "Benchmark video is missing"
        }

        val inputs = mutableListOf<FloatArray>()
        var selectedBox: RtmPoseBoundingBox? = null
        val estimator = RtmPoseFrameEstimator(context)

        try {
            VideoFrameDecoder().decode(video) { frame ->
                val bitmap = frame.bitmap ?: return@decode

                if (frame.sourceIndex == 0) {
                    val person = estimator.estimatePeople(bitmap).firstOrNull()
                        ?: error("No person detected in the first frame")
                    selectedBox = person.boundingBox
                    inputs += RtmPoseImagePreprocessor.createPoseInput(
                        bitmap,
                        person.boundingBox
                    )
                } else if (frame.sourceIndex == 2) {
                    inputs += RtmPoseImagePreprocessor.createPoseInput(
                        bitmap,
                        requireNotNull(selectedBox)
                    )
                }
            }
        } finally {
            estimator.close()
        }

        require(inputs.size == 2) {
            "Two real pose inputs are required"
        }
        val box = requireNotNull(selectedBox)

        val first = RtmPoseOnDeviceSession(
            context,
            useXnnpack = true,
            xnnpackThreads = 4
        )
        val second = try {
            RtmPoseOnDeviceSession(
                context,
                useXnnpack = true,
                xnnpackThreads = 4
            )
        } catch (error: Throwable) {
            first.close()
            throw error
        }
        val executor = Executors.newFixedThreadPool(2)

        try {
            first.runPose(inputs[0])
            second.runPose(inputs[1])

            fun sequentialPair(): Pair<RtmPoseSimccOutput, RtmPoseSimccOutput> =
                first.runPose(inputs[0]) to first.runPose(inputs[1])

            fun parallelPair(): Pair<RtmPoseSimccOutput, RtmPoseSimccOutput> {
                val a = executor.submit(Callable {
                    first.runPose(inputs[0])
                })
                val b = executor.submit(Callable {
                    second.runPose(inputs[1])
                })
                return a.get() to b.get()
            }

            fun measured(
                name: String,
                action: () -> Pair<RtmPoseSimccOutput, RtmPoseSimccOutput>
            ): Run {
                val started = SystemClock.elapsedRealtimeNanos()
                var last: Pair<RtmPoseSimccOutput, RtmPoseSimccOutput>? = null
                repeat(20) {
                    last = action()
                }
                return Run(
                    name,
                    (SystemClock.elapsedRealtimeNanos() - started) /
                        1_000_000.0,
                    requireNotNull(last)
                )
            }

            val runs = listOf(
                measured("sequential_0", ::sequentialPair),
                measured("parallel_0", ::parallelPair),
                measured("parallel_1", ::parallelPair),
                measured("sequential_1", ::sequentialPair)
            )

            fun maxDifference(a: FloatArray, b: FloatArray): Float {
                require(a.size == b.size)
                var maximum = 0f
                for (i in a.indices) {
                    maximum = maxOf(maximum, abs(a[i] - b[i]))
                }
                return maximum
            }

            val reference = runs.first().output
            val referencePoses = listOf(reference.first, reference.second)
                .map { output ->
                    RtmPosePostprocessor.decodePose(
                        simccX = output.simccX,
                        simccY = output.simccY,
                        boundingBox = box
                    )
                }

            var parityPassed = true
            val report = buildString {
                appendLine("RTMPOSE TWO-SESSION REAL-FRAME BENCHMARK")
                appendLine("pairs_per_run=20")
                appendLine("backend=XNNPACK")
                appendLine("threads_per_session=4")
                appendLine("warmup_excluded=true")

                for (run in runs) {
                    val outputs = listOf(run.output.first, run.output.second)
                    var simccDifference = 0f
                    var coordinateDifference = 0f
                    var scoreDifference = 0f

                    for (i in 0..1) {
                        simccDifference = maxOf(
                            simccDifference,
                            maxDifference(
                                outputs[i].simccX,
                                referencePoses.let {
                                    if (i == 0) reference.first.simccX
                                    else reference.second.simccX
                                }
                            ),
                            maxDifference(
                                outputs[i].simccY,
                                if (i == 0) reference.first.simccY
                                else reference.second.simccY
                            )
                        )

                        val pose = RtmPosePostprocessor.decodePose(
                            simccX = outputs[i].simccX,
                            simccY = outputs[i].simccY,
                            boundingBox = box
                        )
                        coordinateDifference = maxOf(
                            coordinateDifference,
                            maxDifference(
                                pose.coordinates,
                                referencePoses[i].coordinates
                            )
                        )
                        scoreDifference = maxOf(
                            scoreDifference,
                            maxDifference(
                                pose.scores,
                                referencePoses[i].scores
                            )
                        )
                    }

                    appendLine(
                        "${run.name}_total_ms=${"%.3f".format(
                            java.util.Locale.US,
                            run.millis
                        )}"
                    )
                    appendLine(
                        "${run.name}_ms_per_pair=${"%.3f".format(
                            java.util.Locale.US,
                            run.millis / 20.0
                        )}"
                    )
                    appendLine("${run.name}_simcc_max_abs=$simccDifference")
                    appendLine(
                        "${run.name}_joint_xy_max_abs_px=$coordinateDifference"
                    )
                    appendLine("${run.name}_joint_score_max_abs=$scoreDifference")

                    if (
                        coordinateDifference != 0f ||
                        scoreDifference > 0.0001f
                    ) {
                        parityPassed = false
                    }
                }
                appendLine("pose_parity_passed=$parityPassed")
            }

            File(
                context.cacheDir,
                "rtmpose_parallel_sessions_report.txt"
            ).writeText(report)

            require(parityPassed) {
                "Pose output differs; inspect the benchmark report"
            }
        } finally {
            executor.shutdown()
            first.close()
            second.close()
        }
    }

    private data class Run(
        val name: String,
        val millis: Double,
        val output: Pair<RtmPoseSimccOutput, RtmPoseSimccOutput>
    )
}