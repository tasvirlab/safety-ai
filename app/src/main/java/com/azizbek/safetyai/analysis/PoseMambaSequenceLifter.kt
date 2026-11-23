package com.azizbek.safetyai.analysis

import kotlinx.coroutines.async

data class PoseMamba3DSequence(
    val frameCount: Int,
    /** Flat [frame][21][xyz], metres, NaN for skipped frames. */
    val coordinates: FloatArray,
    val frameValid: BooleanArray,
    val skippedWindowIndices: IntArray
) {
    init {
        require(frameCount >= 0)
        require(coordinates.size == frameCount * 21 * 3)
        require(frameValid.size == frameCount)
    }
}

/**
 * Exact backend temporal behavior:
 * - valid 243-frame windows are lifted;
 * - invalid windows are skipped;
 * - overlap from the backward-aligned tail is not written twice.
 */
class PoseMambaSequenceLifter(
    private val lifter: PoseMambaLifter,
    private val secondaryLifter: PoseMambaLifter? = null
) : AutoCloseable {

    suspend fun lift(
        sequence: NormalizedPose2DSequence,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): PoseMamba3DSequence {
        val windows = PoseMambaTemporalInput.createWindows(sequence)
        val valuesPerOutputFrame = 21 * 3
        val output = FloatArray(
            sequence.frameCount * valuesPerOutputFrame
        ) { Float.NaN }
        val frameValid = BooleanArray(sequence.frameCount)
        val skipped = mutableListOf<Int>()
        var coveredUntil = 0

        fun consume(windowIndex: Int, prediction: FloatArray?) {
            val window = windows[windowIndex]

            if (!window.valid) {
                skipped += windowIndex
                coveredUntil =
                    window.startFrame + PoseMambaTemporalInput.WINDOW_SIZE
                onProgress?.invoke(windowIndex + 1, windows.size)
                return
            }

            val result = requireNotNull(prediction)
            require(
                result.size ==
                    PoseMambaTemporalInput.WINDOW_SIZE *
                    valuesPerOutputFrame
            ) {
                "Unexpected PoseMamba output size: ${result.size}"
            }
            require(result.all { it.isFinite() }) {
                "PoseMamba produced nonfinite 3D output"
            }

            val emitFrom = maxOf(window.startFrame, coveredUntil)
            val emitTo =
                window.startFrame + PoseMambaTemporalInput.WINDOW_SIZE
            val localFrameOffset = emitFrom - window.startFrame
            val emittedFrames = emitTo - emitFrom

            if (emittedFrames > 0) {
                System.arraycopy(
                    result,
                    localFrameOffset * valuesPerOutputFrame,
                    output,
                    emitFrom * valuesPerOutputFrame,
                    emittedFrames * valuesPerOutputFrame
                )
                for (frame in emitFrom until emitTo) {
                    frameValid[frame] = true
                }
            }

            coveredUntil = emitTo
            onProgress?.invoke(windowIndex + 1, windows.size)
        }

        var index = 0
        while (index < windows.size) {
            val second = secondaryLifter

            if (
                second != null &&
                index + 1 < windows.size &&
                windows[index].valid &&
                windows[index + 1].valid
            ) {
                val firstWindow = windows[index]
                val secondWindow = windows[index + 1]

                val predictions = kotlinx.coroutines.coroutineScope {
                    val firstJob = async(
                        kotlinx.coroutines.Dispatchers.IO
                    ) {
                        lifter.lift(firstWindow.input)
                    }
                    val secondJob = async(
                        kotlinx.coroutines.Dispatchers.IO
                    ) {
                        second.lift(secondWindow.input)
                    }
                    firstJob.await() to secondJob.await()
                }

                consume(index, predictions.first)
                consume(index + 1, predictions.second)
                index += 2
            } else {
                val prediction = if (windows[index].valid) {
                    lifter.lift(windows[index].input)
                } else {
                    null
                }
                consume(index, prediction)
                index++
            }
        }

        return PoseMamba3DSequence(
            frameCount = sequence.frameCount,
            coordinates = output,
            frameValid = frameValid,
            skippedWindowIndices = skipped.toIntArray()
        )
    }

    override fun close() {
        try {
            lifter.close()
        } finally {
            secondaryLifter?.close()
        }
    }
}
