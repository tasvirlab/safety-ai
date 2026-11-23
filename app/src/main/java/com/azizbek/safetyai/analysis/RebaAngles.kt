package com.azizbek.safetyai.analysis

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

data class RebaFrameAngles(
    val trunkSagittalDeg: Double,
    val trunkLateralDeg: Double,
    val trunkTwistDeg: Double,
    val neckFlexDeg: Double,
    val neckLateralDeg: Double,
    val upperArmRSagittalDeg: Double,
    val upperArmLSagittalDeg: Double,
    val upperArmRAbductionDeg: Double,
    val upperArmLAbductionDeg: Double,
    val lowerArmRDeg: Double,
    val lowerArmLDeg: Double,
    val wristRDeg: Double,
    val wristLDeg: Double,
    val kneeRDeg: Double,
    val kneeLDeg: Double,
    val kneeMaxDeg: Double
) {
    val isFinite: Boolean
        get() = listOf(
            trunkSagittalDeg,
            trunkLateralDeg,
            trunkTwistDeg,
            neckFlexDeg,
            neckLateralDeg,
            upperArmRSagittalDeg,
            upperArmLSagittalDeg,
            upperArmRAbductionDeg,
            upperArmLAbductionDeg,
            lowerArmRDeg,
            lowerArmLDeg,
            wristRDeg,
            wristLDeg,
            kneeRDeg,
            kneeLDeg,
            kneeMaxDeg
        ).all { it.isFinite() }

    companion object {
        fun invalid() = RebaFrameAngles(
            trunkSagittalDeg = Double.NaN,
            trunkLateralDeg = Double.NaN,
            trunkTwistDeg = Double.NaN,
            neckFlexDeg = Double.NaN,
            neckLateralDeg = Double.NaN,
            upperArmRSagittalDeg = Double.NaN,
            upperArmLSagittalDeg = Double.NaN,
            upperArmRAbductionDeg = Double.NaN,
            upperArmLAbductionDeg = Double.NaN,
            lowerArmRDeg = Double.NaN,
            lowerArmLDeg = Double.NaN,
            wristRDeg = Double.NaN,
            wristLDeg = Double.NaN,
            kneeRDeg = Double.NaN,
            kneeLDeg = Double.NaN,
            kneeMaxDeg = Double.NaN
        )
    }
}

object RebaAngleCalculator {
    private const val JOINT_COUNT = 21
    private const val VALUES_PER_FRAME = JOINT_COUNT * 3
    private const val EPSILON = 1e-9
    private const val RAD_TO_DEG = 180.0 / Math.PI

    // Final PoseMamba canonical joint order.
    private const val HD = 0
    private const val C7 = 1
    private const val PELCEN = 3
    private const val LSHO = 4
    private const val RSHO = 5
    private const val LELB = 6
    private const val RELB = 7
    private const val LWR = 8
    private const val RWR = 9
    private const val LHAND = 10
    private const val RHAND = 11
    private const val LHIP = 12
    private const val RHIP = 13
    private const val LKNE = 14
    private const val RKNE = 15
    private const val LANK = 16
    private const val RANK = 17

    private data class Vec3(
        val x: Double,
        val y: Double,
        val z: Double
    )

    fun compute(sequence: PoseMamba3DSequence): List<RebaFrameAngles> {
        require(
            sequence.coordinates.size ==
                sequence.frameCount * VALUES_PER_FRAME
        )

        return List(sequence.frameCount) { frameIndex ->
            if (!sequence.frameValid[frameIndex]) {
                RebaFrameAngles.invalid()
            } else {
                computeFrame(
                    coordinates = sequence.coordinates,
                    offset = frameIndex * VALUES_PER_FRAME
                )
            }
        }
    }

    fun computeFrame(coordinates: FloatArray): RebaFrameAngles {
        require(coordinates.size == VALUES_PER_FRAME)
        return computeFrame(coordinates, 0)
    }

    private fun computeFrame(
        coordinates: FloatArray,
        offset: Int
    ): RebaFrameAngles {
        fun joint(index: Int): Vec3 {
            val start = offset + index * 3
            return Vec3(
                coordinates[start].toDouble(),
                coordinates[start + 1].toDouble(),
                coordinates[start + 2].toDouble()
            )
        }

        val hd = joint(HD)
        val c7 = joint(C7)
        val pelvis = joint(PELCEN)

        val leftShoulder = joint(LSHO)
        val rightShoulder = joint(RSHO)
        val leftElbow = joint(LELB)
        val rightElbow = joint(RELB)
        val leftWrist = joint(LWR)
        val rightWrist = joint(RWR)
        val leftHand = joint(LHAND)
        val rightHand = joint(RHAND)

        val leftHip = joint(LHIP)
        val rightHip = joint(RHIP)
        val leftKnee = joint(LKNE)
        val rightKnee = joint(RKNE)
        val leftAnkle = joint(LANK)
        val rightAnkle = joint(RANK)

        val up = Vec3(0.0, 0.0, 1.0)
        val right = unit(subtract(rightHip, leftHip))
        val forward = unit(cross(up, right))

        val shoulderMidpoint = multiply(
            add(rightShoulder, leftShoulder),
            0.5
        )
        val trunkUnit = unit(subtract(shoulderMidpoint, pelvis))

        val trunkUpComponent = dot(trunkUnit, up)
        val trunkForwardComponent = dot(trunkUnit, forward)
        val trunkRightComponent = dot(trunkUnit, right)

        val trunkSagittal = signedPlaneAngle(
            trunkForwardComponent,
            trunkUpComponent
        )
        val trunkLateral = decoupledPlaneAngle(
            trunkRightComponent,
            trunkUpComponent,
            trunkForwardComponent
        )

        val shoulderBar = subtract(rightShoulder, leftShoulder)
        val hipBar = subtract(rightHip, leftHip)

        val shoulderBarHorizontal = unit(
            subtract(
                shoulderBar,
                multiply(up, dot(shoulderBar, up))
            )
        )
        val hipBarHorizontal = unit(
            subtract(
                hipBar,
                multiply(up, dot(hipBar, up))
            )
        )

        val trunkTwist = atan2(
            dot(cross(hipBarHorizontal, shoulderBarHorizontal), up),
            dot(hipBarHorizontal, shoulderBarHorizontal)
        ) * RAD_TO_DEG

        // Gram-Schmidt trunk-local coordinate frame.
        val trunkUp = trunkUnit
        val trunkRight = unit(
            subtract(
                right,
                multiply(trunkUp, dot(right, trunkUp))
            )
        )
        val trunkForward = unit(cross(trunkUp, trunkRight))

        val neckVector = unit(subtract(hd, c7))
        val neckUpComponent = dot(neckVector, trunkUp)
        val neckForwardComponent = dot(neckVector, trunkForward)
        val neckRightComponent = dot(neckVector, trunkRight)

        val neckFlex = signedPlaneAngle(
            neckForwardComponent,
            neckUpComponent
        )
        val neckLateral = abs(
            decoupledPlaneAngle(
                neckRightComponent,
                neckUpComponent,
                neckForwardComponent
            )
        )

        val hanging = multiply(trunkUp, -1.0)

        fun upperArm(
            shoulder: Vec3,
            elbow: Vec3
        ): Pair<Double, Double> {
            val armVector = unit(subtract(elbow, shoulder))
            val hangingComponent = dot(armVector, hanging)
            val forwardComponent = dot(armVector, trunkForward)
            val rightComponent = dot(armVector, trunkRight)

            val sagittal = signedPlaneAngle(
                forwardComponent,
                hangingComponent
            )
            val abduction = abs(
                decoupledPlaneAngle(
                    rightComponent,
                    hangingComponent,
                    forwardComponent
                )
            )
            return sagittal to abduction
        }

        val (upperArmRSagittal, upperArmRAbduction) =
            upperArm(rightShoulder, rightElbow)
        val (upperArmLSagittal, upperArmLAbduction) =
            upperArm(leftShoulder, leftElbow)

        val lowerArmR = 180.0 - angleAtJoint(
            rightShoulder,
            rightElbow,
            rightWrist
        )
        val lowerArmL = 180.0 - angleAtJoint(
            leftShoulder,
            leftElbow,
            leftWrist
        )

        val wristR = abs(
            180.0 - angleAtJoint(
                rightElbow,
                rightWrist,
                rightHand
            )
        )
        val wristL = abs(
            180.0 - angleAtJoint(
                leftElbow,
                leftWrist,
                leftHand
            )
        )

        val kneeR = 180.0 - angleAtJoint(
            rightHip,
            rightKnee,
            rightAnkle
        )
        val kneeL = 180.0 - angleAtJoint(
            leftHip,
            leftKnee,
            leftAnkle
        )

        return RebaFrameAngles(
            trunkSagittalDeg = trunkSagittal,
            trunkLateralDeg = trunkLateral,
            trunkTwistDeg = trunkTwist,
            neckFlexDeg = neckFlex,
            neckLateralDeg = neckLateral,
            upperArmRSagittalDeg = upperArmRSagittal,
            upperArmLSagittalDeg = upperArmLSagittal,
            upperArmRAbductionDeg = upperArmRAbduction,
            upperArmLAbductionDeg = upperArmLAbduction,
            lowerArmRDeg = lowerArmR,
            lowerArmLDeg = lowerArmL,
            wristRDeg = wristR,
            wristLDeg = wristL,
            kneeRDeg = kneeR,
            kneeLDeg = kneeL,
            kneeMaxDeg = maxOf(kneeR, kneeL)
        )
    }

    private fun unit(vector: Vec3): Vec3 {
        val norm = sqrt(dot(vector, vector))
        if (!norm.isFinite() || norm < EPSILON) {
            return Vec3(Double.NaN, Double.NaN, Double.NaN)
        }
        return multiply(vector, 1.0 / norm)
    }

    private fun angleAtJoint(
        proximal: Vec3,
        joint: Vec3,
        distal: Vec3
    ): Double = angleDegrees(
        subtract(proximal, joint),
        subtract(distal, joint)
    )

    private fun angleDegrees(first: Vec3, second: Vec3): Double {
        val cosine = dot(unit(first), unit(second))
        if (!cosine.isFinite()) return Double.NaN
        return acos(cosine.coerceIn(-1.0, 1.0)) * RAD_TO_DEG
    }

    private fun signedPlaneAngle(
        offAxis: Double,
        inPlane: Double
    ): Double = atan2(offAxis, inPlane) * RAD_TO_DEG

    private fun decoupledPlaneAngle(
        offPlane: Double,
        inPlaneA: Double,
        inPlaneB: Double
    ): Double = atan2(
        offPlane,
        sqrt(
            inPlaneA * inPlaneA +
                inPlaneB * inPlaneB +
                EPSILON
        )
    ) * RAD_TO_DEG

    private fun add(a: Vec3, b: Vec3) = Vec3(
        a.x + b.x,
        a.y + b.y,
        a.z + b.z
    )

    private fun subtract(a: Vec3, b: Vec3) = Vec3(
        a.x - b.x,
        a.y - b.y,
        a.z - b.z
    )

    private fun multiply(vector: Vec3, scalar: Double) = Vec3(
        vector.x * scalar,
        vector.y * scalar,
        vector.z * scalar
    )

    private fun dot(a: Vec3, b: Vec3): Double =
        a.x * b.x + a.y * b.y + a.z * b.z

    private fun cross(a: Vec3, b: Vec3) = Vec3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )
}