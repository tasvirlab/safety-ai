package com.azizbek.safetyai.assessment

/**
 * Route for the full-screen New Assessment flow — deliberately outside
 * [com.azizbek.safetyai.navigation.SafetyDestination]'s bottom-nav/drawer
 * destinations, since this flow shows neither.
 */
const val ASSESSMENT_DETAILS_ROUTE = "assessmentDetails"

/** Pattern registered with NavHost: `assessmentDetails/{source}` — step 1 of 5, Job details. */
const val ASSESSMENT_DETAILS_ROUTE_PATTERN = "$ASSESSMENT_DETAILS_ROUTE/{$ASSESSMENT_SOURCE_ARG}"

/** Builds a concrete route carrying the source as its typed name — never a display string. */
fun assessmentDetailsRoute(source: AssessmentSource): String = "$ASSESSMENT_DETAILS_ROUTE/${source.name}"

/** Step 2 of 5, Load & coupling — carries `{source}` too, so it can look up step 1's back-stack entry and share its [AssessmentFormViewModel]. */
const val LOAD_COUPLING_ROUTE = "loadCoupling"
const val LOAD_COUPLING_ROUTE_PATTERN = "$LOAD_COUPLING_ROUTE/{$ASSESSMENT_SOURCE_ARG}"
fun loadCouplingRoute(source: AssessmentSource): String = "$LOAD_COUPLING_ROUTE/${source.name}"

/**
 * Route of the nested graph containing the whole New Assessment flow (job
 * details → load/coupling → camera setup → capture/upload → review).
 * [NewAssessmentFlowViewModel] is scoped to this route so every screen in the
 * flow shares one instance.
 */
const val NEW_ASSESSMENT_FLOW_ROUTE = "newAssessmentFlow"

const val CAMERA_GUIDANCE_ROUTE = "cameraGuidance"
const val CAMERA_RECORDING_ROUTE = "cameraRecording"
const val VIDEO_UPLOAD_ROUTE = "videoUpload"
const val REVIEW_ASSESSMENT_ROUTE = "reviewAssessment"

/** Every route inside the New Assessment flow — used to hide bottom nav/drawer chrome for all of them. */
val NEW_ASSESSMENT_FLOW_ROUTES = setOf(
    ASSESSMENT_DETAILS_ROUTE,
    LOAD_COUPLING_ROUTE,
    CAMERA_GUIDANCE_ROUTE,
    CAMERA_RECORDING_ROUTE,
    VIDEO_UPLOAD_ROUTE,
    REVIEW_ASSESSMENT_ROUTE
)
