package com.azizbek.safetyai.analysis

import kotlin.math.abs

data class RebaManualInputs(
    val loadKg: Double,
    val suddenForce: Boolean,
    val coupling: Int,
    val activityScore: Int = 0,
    val activityPending: Boolean = true,
    val unilateral: Boolean = false,
    val shoulderRaisedR: Boolean = false,
    val shoulderRaisedL: Boolean = false,
    val supportedOrLeaningR: Boolean = false,
    val supportedOrLeaningL: Boolean = false,
    val neckTwistModifier: Boolean? = null,
    val upperArmRotatedR: Boolean? = null,
    val upperArmRotatedL: Boolean? = null,
    val wristDeviationR: Boolean? = null,
    val wristDeviationL: Boolean? = null
) {
    init {
        require(loadKg.isFinite() && loadKg >= 0.0)
        require(coupling in 0..3)
        require(activityScore in 0..3)
        // An incomplete Activity assessment can have positive evidence for
        // one criterion while another criterion remains unevaluable.
    }
}

data class RebaSegmentScore(
    val score: Int?,
    val base: Int?,
    val modifier: Int?,
    val valid: Boolean,
    val evaluated: Boolean,
    val invalidReasons: List<String>,
    val unsupported: List<String>
)

data class RebaScoreBreakdown(
    val trunk: RebaSegmentScore,
    val neck: RebaSegmentScore,
    val legs: RebaSegmentScore,
    val upperArmR: RebaSegmentScore,
    val upperArmL: RebaSegmentScore,
    val lowerArmR: RebaSegmentScore,
    val lowerArmL: RebaSegmentScore,
    val wristR: RebaSegmentScore,
    val wristL: RebaSegmentScore,
    val load: RebaSegmentScore
)

data class RebaFrameScore(
    val valid: Boolean,
    val invalidReasons: List<String>,
    val scoreA: Int?,
    val scoreB: Int?,
    val tableCScore: Int?,
    val activityScore: Int,
    val finalRebaScore: Int?,
    val actionLevel: Int?,
    val riskLabel: String?,
    val actionText: String?,
    val dominantArm: String?,
    val postureFullyEvaluated: Boolean,
    val fullyEvaluated: Boolean,
    val activityPending: Boolean,
    val unsupportedComponents: List<String>,
    val components: RebaScoreBreakdown
)

object RebaScorer {
    private const val TRUNK_MILD_BOUND = 20.0
    private const val TRUNK_SEVERE_FLEX_BOUND = 60.0
    private const val NECK_MILD_BOUND = 20.0

    private const val LEGS_MILD_BOUND = 30.0
    private const val LEGS_SEVERE_BOUND = 60.0

    private const val UPPER_ARM_NEUTRAL_BOUND = 20.0
    private const val UPPER_ARM_MODERATE_BOUND = 45.0
    private const val UPPER_ARM_SEVERE_BOUND = 90.0

    private const val LOWER_ARM_MIN_BOUND = 60.0
    private const val LOWER_ARM_MAX_BOUND = 100.0
    private const val WRIST_MILD_BOUND = 15.0

    private const val TRUNK_TWIST_THRESHOLD = 10.0
    private const val TRUNK_LATERAL_THRESHOLD = 10.0
    private const val NECK_LATERAL_THRESHOLD = 15.0
    private const val UPPER_ARM_ABDUCTION_THRESHOLD = 20.0

    private val tableA = arrayOf(
        arrayOf(
            intArrayOf(1, 2, 3, 4),
            intArrayOf(1, 2, 3, 4),
            intArrayOf(3, 3, 5, 6)
        ),
        arrayOf(
            intArrayOf(2, 3, 4, 5),
            intArrayOf(3, 4, 5, 6),
            intArrayOf(4, 5, 6, 7)
        ),
        arrayOf(
            intArrayOf(2, 4, 5, 6),
            intArrayOf(4, 5, 6, 7),
            intArrayOf(5, 6, 7, 8)
        ),
        arrayOf(
            intArrayOf(3, 5, 6, 7),
            intArrayOf(5, 6, 7, 8),
            intArrayOf(6, 7, 8, 9)
        ),
        arrayOf(
            intArrayOf(4, 6, 7, 8),
            intArrayOf(6, 7, 8, 9),
            intArrayOf(7, 8, 9, 9)
        )
    )

    private val tableB = arrayOf(
        arrayOf(
            intArrayOf(1, 2, 2),
            intArrayOf(1, 2, 3)
        ),
        arrayOf(
            intArrayOf(1, 2, 3),
            intArrayOf(2, 3, 4)
        ),
        arrayOf(
            intArrayOf(3, 4, 5),
            intArrayOf(4, 5, 5)
        ),
        arrayOf(
            intArrayOf(4, 5, 5),
            intArrayOf(5, 6, 7)
        ),
        arrayOf(
            intArrayOf(6, 7, 8),
            intArrayOf(7, 8, 8)
        ),
        arrayOf(
            intArrayOf(7, 8, 8),
            intArrayOf(8, 9, 9)
        )
    )

    private val tableC = arrayOf(
        intArrayOf(1, 1, 1, 2, 3, 3, 4, 5, 6, 7, 7, 7),
        intArrayOf(1, 2, 2, 3, 4, 4, 5, 6, 6, 7, 7, 8),
        intArrayOf(2, 3, 3, 3, 4, 5, 6, 7, 7, 8, 8, 8),
        intArrayOf(3, 4, 4, 4, 5, 6, 7, 8, 8, 9, 9, 9),
        intArrayOf(4, 4, 4, 5, 6, 7, 8, 8, 9, 9, 9, 9),
        intArrayOf(6, 6, 6, 7, 8, 8, 9, 9, 10, 10, 10, 10),
        intArrayOf(7, 7, 7, 8, 9, 9, 9, 10, 10, 11, 11, 11),
        intArrayOf(8, 8, 8, 9, 10, 10, 10, 10, 10, 11, 11, 11),
        intArrayOf(9, 9, 9, 10, 10, 10, 11, 11, 11, 12, 12, 12),
        intArrayOf(10, 10, 10, 11, 11, 11, 11, 12, 12, 12, 12, 12),
        intArrayOf(11, 11, 11, 11, 12, 12, 12, 12, 12, 12, 12, 12),
        intArrayOf(12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12)
    )

    fun scoreSequence(
        angles: List<RebaFrameAngles>,
        manual: RebaManualInputs
    ): List<RebaFrameScore> =
        angles.map { scoreFrame(it, manual) }

    fun scoreFrame(
        angles: RebaFrameAngles,
        manual: RebaManualInputs
    ): RebaFrameScore {
        val trunk = trunkScore(angles)
        val neck = neckScore(angles, manual.neckTwistModifier)
        val legs = legsScore(angles.kneeMaxDeg, manual.unilateral)

        val upperArmR = upperArmScore(
            angles.upperArmRSagittalDeg,
            angles.upperArmRAbductionDeg,
            manual.shoulderRaisedR,
            manual.supportedOrLeaningR,
            manual.upperArmRotatedR
        )
        val upperArmL = upperArmScore(
            angles.upperArmLSagittalDeg,
            angles.upperArmLAbductionDeg,
            manual.shoulderRaisedL,
            manual.supportedOrLeaningL,
            manual.upperArmRotatedL
        )

        val lowerArmR = lowerArmScore(angles.lowerArmRDeg)
        val lowerArmL = lowerArmScore(angles.lowerArmLDeg)
        val wristR = wristScore(
            angles.wristRDeg,
            manual.wristDeviationR
        )
        val wristL = wristScore(
            angles.wristLDeg,
            manual.wristDeviationL
        )
        val load = loadScore(manual.loadKg, manual.suddenForce)

        val breakdown = RebaScoreBreakdown(
            trunk = trunk,
            neck = neck,
            legs = legs,
            upperArmR = upperArmR,
            upperArmL = upperArmL,
            lowerArmR = lowerArmR,
            lowerArmL = lowerArmL,
            wristR = wristR,
            wristL = wristL,
            load = load
        )

        val allComponents = listOf(
            trunk,
            neck,
            legs,
            upperArmR,
            upperArmL,
            lowerArmR,
            lowerArmL,
            wristR,
            wristL,
            load
        )

        val invalidReasons =
            allComponents.flatMap { it.invalidReasons }

        if (invalidReasons.isNotEmpty()) {
            return RebaFrameScore(
                valid = false,
                invalidReasons = invalidReasons,
                scoreA = null,
                scoreB = null,
                tableCScore = null,
                activityScore = manual.activityScore,
                finalRebaScore = null,
                actionLevel = null,
                riskLabel = null,
                actionText = null,
                dominantArm = null,
                postureFullyEvaluated = false,
                fullyEvaluated = false,
                activityPending = manual.activityPending,
                unsupportedComponents = emptyList(),
                components = breakdown
            )
        }

        val rawA = tableA[
            trunk.score!! - 1
        ][
            neck.score!! - 1
        ][
            legs.score!! - 1
        ]
        val scoreA = minOf(rawA + load.score!!, 12)

        val rawBRight = tableB[
            upperArmR.score!! - 1
        ][
            lowerArmR.score!! - 1
        ][
            wristR.score!! - 1
        ]

        val rawBLeft = tableB[
            upperArmL.score!! - 1
        ][
            lowerArmL.score!! - 1
        ][
            wristL.score!! - 1
        ]

        val rawB: Int
        val dominantArm: String

        when {
            rawBRight > rawBLeft -> {
                rawB = rawBRight
                dominantArm = "right"
            }
            rawBLeft > rawBRight -> {
                rawB = rawBLeft
                dominantArm = "left"
            }
            else -> {
                rawB = rawBRight
                dominantArm = "tie"
            }
        }

        val scoreB = minOf(rawB + manual.coupling, 12)
        val tableCScore = tableC[scoreA - 1][scoreB - 1]
        val finalRebaScore =
            tableCScore + manual.activityScore

        val action = actionFor(finalRebaScore)

        val unsupported = mutableListOf<String>()
        unsupported += neck.unsupported

        if (upperArmR.unsupported.isNotEmpty()) {
            unsupported += "upper_arm_r_rotation"
        }
        if (upperArmL.unsupported.isNotEmpty()) {
            unsupported += "upper_arm_l_rotation"
        }
        if (wristR.unsupported.isNotEmpty()) {
            unsupported += "wrist_r_deviation"
        }
        if (wristL.unsupported.isNotEmpty()) {
            unsupported += "wrist_l_deviation"
        }

        val postureFullyEvaluated = unsupported.isEmpty()

        return RebaFrameScore(
            valid = true,
            invalidReasons = emptyList(),
            scoreA = scoreA,
            scoreB = scoreB,
            tableCScore = tableCScore,
            activityScore = manual.activityScore,
            finalRebaScore = finalRebaScore,
            actionLevel = action.first,
            riskLabel = action.second,
            actionText = action.third,
            dominantArm = dominantArm,
            postureFullyEvaluated = postureFullyEvaluated,
            fullyEvaluated =
                postureFullyEvaluated && !manual.activityPending,
            activityPending = manual.activityPending,
            unsupportedComponents = unsupported,
            components = breakdown
        )
    }

    private fun trunkScore(
        angles: RebaFrameAngles
    ): RebaSegmentScore {
        val sagittal = angles.trunkSagittalDeg
        val twist = angles.trunkTwistDeg
        val lateral = angles.trunkLateralDeg

        if (!sagittal.isFinite() ||
            !twist.isFinite() ||
            !lateral.isFinite()
        ) {
            return invalid("trunk_invalid_input")
        }

        val base = when {
            sagittal > TRUNK_SEVERE_FLEX_BOUND -> 4
            sagittal > TRUNK_MILD_BOUND -> 3
            sagittal < -TRUNK_MILD_BOUND -> 3
            sagittal > 0.0 -> 2
            sagittal < 0.0 -> 2
            else -> 1
        }

        val modifier = if (
            abs(twist) > TRUNK_TWIST_THRESHOLD ||
            abs(lateral) > TRUNK_LATERAL_THRESHOLD
        ) 1 else 0

        return valid(
            score = minOf(base + modifier, 5),
            base = base,
            modifier = modifier
        )
    }

    private fun neckScore(
        angles: RebaFrameAngles,
        twistOverride: Boolean?
    ): RebaSegmentScore {
        val flexion = angles.neckFlexDeg
        val lateral = angles.neckLateralDeg

        if (!flexion.isFinite() || !lateral.isFinite()) {
            return invalid("neck_invalid_input")
        }

        val base = if (flexion < 0.0) {
            2
        } else if (flexion <= NECK_MILD_BOUND) {
            1
        } else {
            2
        }

        val unsupported = mutableListOf<String>()
        val twisted = if (twistOverride == null) {
            unsupported += "neck_twist"
            false
        } else {
            twistOverride
        }

        val modifier = if (
            lateral > NECK_LATERAL_THRESHOLD || twisted
        ) 1 else 0

        return valid(
            score = minOf(base + modifier, 3),
            base = base,
            modifier = modifier,
            unsupported = unsupported
        )
    }

    private fun legsScore(
        kneeMaxDeg: Double,
        unilateral: Boolean
    ): RebaSegmentScore {
        if (!kneeMaxDeg.isFinite()) {
            return invalid("legs_invalid_input")
        }

        val base = if (unilateral) 2 else 1
        val modifier = when {
            kneeMaxDeg > LEGS_SEVERE_BOUND -> 2
            kneeMaxDeg >= LEGS_MILD_BOUND -> 1
            else -> 0
        }

        return valid(
            score = minOf(base + modifier, 4),
            base = base,
            modifier = modifier
        )
    }

    private fun upperArmScore(
        sagittalDeg: Double,
        abductionDeg: Double,
        shoulderRaised: Boolean,
        supportedOrLeaning: Boolean,
        rotationOverride: Boolean?
    ): RebaSegmentScore {
        if (!sagittalDeg.isFinite() ||
            !abductionDeg.isFinite()
        ) {
            return invalid("upper_arm_invalid_input")
        }

        val base = when {
            sagittalDeg > UPPER_ARM_SEVERE_BOUND -> 4
            sagittalDeg > UPPER_ARM_MODERATE_BOUND -> 3
            sagittalDeg > UPPER_ARM_NEUTRAL_BOUND -> 2
            sagittalDeg >= -UPPER_ARM_NEUTRAL_BOUND -> 1
            else -> 2
        }

        val unsupported = mutableListOf<String>()
        val rotated = if (rotationOverride == null) {
            unsupported += "upper_arm_rotation"
            false
        } else {
            rotationOverride
        }

        val abducted =
            abductionDeg > UPPER_ARM_ABDUCTION_THRESHOLD

        val modifier =
            (if (abducted || rotated) 1 else 0) +
                (if (shoulderRaised) 1 else 0) -
                (if (supportedOrLeaning) 1 else 0)

        return valid(
            score = (base + modifier).coerceIn(1, 6),
            base = base,
            modifier = modifier,
            unsupported = unsupported
        )
    }

    private fun lowerArmScore(
        angle: Double
    ): RebaSegmentScore {
        if (!angle.isFinite()) {
            return invalid("lower_arm_invalid_input")
        }

        val base = if (
            angle in LOWER_ARM_MIN_BOUND..LOWER_ARM_MAX_BOUND
        ) 1 else 2

        return valid(base, base, 0)
    }

    private fun wristScore(
        angle: Double,
        deviationOverride: Boolean?
    ): RebaSegmentScore {
        if (!angle.isFinite()) {
            return invalid("wrist_invalid_input")
        }

        val base =
            if (angle <= WRIST_MILD_BOUND) 1 else 2

        val unsupported = mutableListOf<String>()
        val deviated = if (deviationOverride == null) {
            unsupported += "wrist_deviation"
            false
        } else {
            deviationOverride
        }

        val modifier = if (deviated) 1 else 0

        return valid(
            score = minOf(base + modifier, 3),
            base = base,
            modifier = modifier,
            unsupported = unsupported
        )
    }

    private fun loadScore(
        loadKg: Double,
        suddenForce: Boolean
    ): RebaSegmentScore {
        val base = when {
            loadKg < 5.0 -> 0
            loadKg <= 10.0 -> 1
            else -> 2
        }

        val modifier = if (suddenForce) 1 else 0
        return valid(
            score = base + modifier,
            base = base,
            modifier = modifier
        )
    }

    private fun actionFor(
        score: Int
    ): Triple<Int, String, String> = when {
        score <= 1 -> Triple(
            0,
            "Negligible",
            "None necessary"
        )
        score <= 3 -> Triple(
            1,
            "Low",
            "May be necessary"
        )
        score <= 7 -> Triple(
            2,
            "Medium",
            "Necessary"
        )
        score <= 10 -> Triple(
            3,
            "High",
            "Necessary soon"
        )
        else -> Triple(
            4,
            "Very high",
            "Necessary NOW"
        )
    }

    private fun invalid(
        reason: String
    ) = RebaSegmentScore(
        score = null,
        base = null,
        modifier = null,
        valid = false,
        evaluated = false,
        invalidReasons = listOf(reason),
        unsupported = emptyList()
    )

    private fun valid(
        score: Int,
        base: Int,
        modifier: Int,
        unsupported: List<String> = emptyList()
    ) = RebaSegmentScore(
        score = score,
        base = base,
        modifier = modifier,
        valid = true,
        evaluated = unsupported.isEmpty(),
        invalidReasons = emptyList(),
        unsupported = unsupported
    )
}
