package com.azizbek.safetyai.analysis

import kotlin.math.sqrt

data class CanonicalPose2D(
    /** PoseMamba order, flat [x0,y0,...], NaN for invalid joints. */
    val coordinates: FloatArray,
    val valid: BooleanArray,
    /** Raw confidence, including below-threshold measurements. */
    val confidence: FloatArray
) {
    init {
        require(coordinates.size == 21 * 2)
        require(valid.size == 21)
        require(confidence.size == 21)
    }
}

object RtmPoseCanonical21 {
    val jointNames: List<String> = listOf(
        "HD", "C7", "T10", "PELCEN",
        "LSHO", "RSHO", "LELB", "RELB",
        "LWR", "RWR", "LHAND", "RHAND",
        "LHIP", "RHIP", "LKNE", "RKNE",
        "LANK", "RANK", "LTOE", "RTOE", "L45"
    )

    const val ROOT_INDEX = 3
    const val CONFIDENCE_THRESHOLD = 0.3f
    const val T_T10 = 0.35f
    const val T_L45 = 0.95f

    private const val L_EYE = 1
    private const val R_EYE = 2
    private const val L_EAR = 3
    private const val R_EAR = 4

    private const val L_SHO = 5
    private const val R_SHO = 6
    private const val L_ELB = 7
    private const val R_ELB = 8
    private const val L_WR = 9
    private const val R_WR = 10
    private const val L_HIP = 11
    private const val R_HIP = 12
    private const val L_KNE = 13
    private const val R_KNE = 14
    private const val L_ANK = 15
    private const val R_ANK = 16
    private const val L_SMALL_TOE = 18
    private const val R_SMALL_TOE = 21
    private const val L_HAND_MIDDLE_MCP = 100
    private const val R_HAND_MIDDLE_MCP = 121

    private val directMapping = arrayOf(
        4 to L_SHO,
        5 to R_SHO,
        6 to L_ELB,
        7 to R_ELB,
        8 to L_WR,
        9 to R_WR,
        10 to L_HAND_MIDDLE_MCP,
        11 to R_HAND_MIDDLE_MCP,
        12 to L_HIP,
        13 to R_HIP,
        14 to L_KNE,
        15 to R_KNE,
        16 to L_ANK,
        17 to R_ANK,
        18 to L_SMALL_TOE,
        19 to R_SMALL_TOE
    )

    fun map(
        person: RtmPosePerson2D,
        confidenceThreshold: Float = CONFIDENCE_THRESHOLD,
        tT10: Float = T_T10,
        tL45: Float = T_L45
    ): CanonicalPose2D {
        require(confidenceThreshold.isFinite())
        require(tT10.isFinite() && tL45.isFinite())

        val output = FloatArray(21 * 2) { Float.NaN }
        val valid = BooleanArray(21)
        val confidence = FloatArray(21) { Float.NaN }

        fun sourceX(index: Int) = person.coordinates[index * 2]
        fun sourceY(index: Int) = person.coordinates[index * 2 + 1]

        fun writeDirect(outputIndex: Int, sourceIndex: Int) {
            val score = person.scores[sourceIndex]
            confidence[outputIndex] = score

            if (score >= confidenceThreshold) {
                output[outputIndex * 2] = sourceX(sourceIndex)
                output[outputIndex * 2 + 1] = sourceY(sourceIndex)
                valid[outputIndex] = true
            }
        }

        for ((outputIndex, sourceIndex) in directMapping) {
            writeDirect(outputIndex, sourceIndex)
        }

        // HD: mean of visible left/right eyes and ears; never use nose.
        val headSources = intArrayOf(L_EYE, R_EYE, L_EAR, R_EAR)
        confidence[0] =
            headSources.sumOf { person.scores[it].toDouble() }.toFloat() /
                headSources.size

        var headX = 0f
        var headY = 0f
        var headCount = 0
        for (source in headSources) {
            if (person.scores[source] >= confidenceThreshold) {
                headX += sourceX(source)
                headY += sourceY(source)
                headCount++
            }
        }

        if (headCount >= 2) {
            output[0] = headX / headCount
            output[1] = headY / headCount
            valid[0] = true
        }

        val leftShoulderValid =
            person.scores[L_SHO] >= confidenceThreshold
        val rightShoulderValid =
            person.scores[R_SHO] >= confidenceThreshold
        val leftHipValid =
            person.scores[L_HIP] >= confidenceThreshold
        val rightHipValid =
            person.scores[R_HIP] >= confidenceThreshold

        val shoulderConfidence =
            (person.scores[L_SHO] + person.scores[R_SHO]) * 0.5f
        val hipConfidence =
            (person.scores[L_HIP] + person.scores[R_HIP]) * 0.5f

        val shouldersValid = leftShoulderValid && rightShoulderValid
        val hipsValid = leftHipValid && rightHipValid

        val c7X = (sourceX(L_SHO) + sourceX(R_SHO)) * 0.5f
        val c7Y = (sourceY(L_SHO) + sourceY(R_SHO)) * 0.5f
        val pelvisX = (sourceX(L_HIP) + sourceX(R_HIP)) * 0.5f
        val pelvisY = (sourceY(L_HIP) + sourceY(R_HIP)) * 0.5f

        confidence[1] = shoulderConfidence
        if (shouldersValid) {
            output[2] = c7X
            output[3] = c7Y
            valid[1] = true
        }

        confidence[ROOT_INDEX] = hipConfidence
        if (hipsValid) {
            output[ROOT_INDEX * 2] = pelvisX
            output[ROOT_INDEX * 2 + 1] = pelvisY
            valid[ROOT_INDEX] = true
        }

        val spineValid = shouldersValid && hipsValid
        val spineConfidence =
            (shoulderConfidence + hipConfidence) * 0.5f

        fun writeSpine(outputIndex: Int, t: Float) {
            confidence[outputIndex] = spineConfidence
            if (spineValid) {
                output[outputIndex * 2] =
                    (1f - t) * c7X + t * pelvisX
                output[outputIndex * 2 + 1] =
                    (1f - t) * c7Y + t * pelvisY
                valid[outputIndex] = true
            }
        }

        writeSpine(outputIndex = 2, t = tT10)
        writeSpine(outputIndex = 20, t = tL45)

        return CanonicalPose2D(
            coordinates = output,
            valid = valid,
            confidence = confidence
        )
    }
}

/**
 * Deployment subject selection:
 * first frame = highest mean body confidence;
 * later frames = hip midpoint nearest to the previous selected person.
 */
class RtmPosePersonTracker {
    private var previousHipX = Float.NaN
    private var previousHipY = Float.NaN

    fun reset() {
        previousHipX = Float.NaN
        previousHipY = Float.NaN
    }

    fun select(people: List<RtmPosePerson2D>): RtmPosePerson2D? {
        if (people.isEmpty()) {
            reset()
            return null
        }

        val selectedIndex =
            if (previousHipX.isFinite() && previousHipY.isFinite()) {
                var bestIndex = 0
                var bestDistance = Float.POSITIVE_INFINITY

                for (index in people.indices) {
                    val person = people[index]
                    val hipX =
                        (person.coordinates[11 * 2] +
                            person.coordinates[12 * 2]) * 0.5f
                    val hipY =
                        (person.coordinates[11 * 2 + 1] +
                            person.coordinates[12 * 2 + 1]) * 0.5f

                    val dx = hipX - previousHipX
                    val dy = hipY - previousHipY
                    val distance = sqrt(dx * dx + dy * dy)

                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestIndex = index
                    }
                }
                bestIndex
            } else {
                val bodyIndices = intArrayOf(5, 6, 11, 12, 13, 14)
                var bestIndex = 0
                var bestConfidence = Float.NEGATIVE_INFINITY

                for (index in people.indices) {
                    val meanConfidence =
                        bodyIndices.sumOf {
                            people[index].scores[it].toDouble()
                        }.toFloat() / bodyIndices.size

                    if (meanConfidence > bestConfidence) {
                        bestConfidence = meanConfidence
                        bestIndex = index
                    }
                }
                bestIndex
            }

        val selected = people[selectedIndex]
        previousHipX =
            (selected.coordinates[11 * 2] +
                selected.coordinates[12 * 2]) * 0.5f
        previousHipY =
            (selected.coordinates[11 * 2 + 1] +
                selected.coordinates[12 * 2 + 1]) * 0.5f

        return selected
    }
}