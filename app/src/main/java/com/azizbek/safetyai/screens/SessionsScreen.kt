package com.azizbek.safetyai.screens

import androidx.compose.runtime.Composable
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.history.AssessmentsScreen

/** Route/composable name kept as "Sessions" (see [com.azizbek.safetyai.navigation.SafetyDestination]) — only the user-facing label changed. Real logic lives in [com.azizbek.safetyai.history]. */
@Composable
fun SessionsScreen(
    repository: AssessmentRepository,
    onOpenAssessment: (String) -> Unit,
    onStartAssessment: () -> Unit
) {
    AssessmentsScreen(
        repository = repository,
        onOpenAssessment = onOpenAssessment,
        onStartAssessment = onStartAssessment
    )
}
