package com.azizbek.safetyai.assessment

// Display strings for the domain enums, kept out of the enums themselves so
// the domain layer stays free of copy decisions. Plain Kotlin — no Compose.

fun AssessmentSource.sourceLabel(): String = when (this) {
    AssessmentSource.CAMERA -> "Camera recording"
    AssessmentSource.VIDEO_UPLOAD -> "Video upload"
}

fun AssessmentSource.continueLabel(): String = when (this) {
    AssessmentSource.CAMERA -> "Continue to camera"
    AssessmentSource.VIDEO_UPLOAD -> "Continue to video"
}

fun TaskType.label(): String = when (this) {
    TaskType.LIFTING -> "Lifting"
    TaskType.CARRYING -> "Carrying"
    TaskType.MATERIAL_HANDLING -> "Material handling"
    TaskType.PUSHING_PULLING -> "Pushing or pulling"
    TaskType.OVERHEAD_WORK -> "Overhead work"
    TaskType.BENDING_TWISTING -> "Bending or twisting"
    TaskType.KNEELING_SQUATTING -> "Kneeling or squatting"
    TaskType.OTHER -> "Other"
}

fun CouplingQuality.label(): String = when (this) {
    CouplingQuality.GOOD -> "Good"
    CouplingQuality.FAIR -> "Fair"
    CouplingQuality.POOR -> "Poor"
    CouplingQuality.UNACCEPTABLE -> "Unacceptable"
    CouplingQuality.NOT_APPLICABLE -> "Not applicable"
}

fun CouplingQuality.description(): String = when (this) {
    CouplingQuality.GOOD -> "Secure, comfortable grip with suitable handles"
    CouplingQuality.FAIR -> "Acceptable grip but not ideal"
    CouplingQuality.POOR -> "Difficult or insecure grip"
    CouplingQuality.UNACCEPTABLE -> "Unsafe, awkward or no usable grip"
    CouplingQuality.NOT_APPLICABLE -> "No load to grip — coupling doesn't apply"
}
