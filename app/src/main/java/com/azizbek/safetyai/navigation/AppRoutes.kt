package com.azizbek.safetyai.navigation

/** Standalone (non-bottom-nav) full-screen routes outside the New Assessment flow. */
const val ASSESSMENT_DETAIL_ROUTE = "assessmentDetail"
const val ASSESSMENT_DETAIL_ROUTE_PATTERN = "$ASSESSMENT_DETAIL_ROUTE/{assessmentId}"
fun assessmentDetailRoute(id: String) = "$ASSESSMENT_DETAIL_ROUTE/$id"

const val EDIT_ASSESSMENT_ROUTE = "editAssessment"
const val EDIT_ASSESSMENT_ROUTE_PATTERN = "$EDIT_ASSESSMENT_ROUTE/{assessmentId}"
fun editAssessmentRoute(id: String) = "$EDIT_ASSESSMENT_ROUTE/$id"

const val REPLACE_VIDEO_ROUTE = "replaceVideo"
const val REPLACE_VIDEO_ROUTE_PATTERN = "$REPLACE_VIDEO_ROUTE/{assessmentId}"
fun replaceVideoRoute(id: String) = "$REPLACE_VIDEO_ROUTE/$id"

const val NOTIFICATIONS_ROUTE = "notifications"
const val SETTINGS_ROUTE = "settings"

/** Every route (or prefix) that hides the bottom nav / drawer chrome. */
val CHROMELESS_ROUTE_PREFIXES = setOf(
    ASSESSMENT_DETAIL_ROUTE,
    EDIT_ASSESSMENT_ROUTE,
    REPLACE_VIDEO_ROUTE,
    NOTIFICATIONS_ROUTE,
    SETTINGS_ROUTE
)
