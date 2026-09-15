package com.azizbek.safetyai

import android.content.Context
import com.azizbek.safetyai.analysis.SafetyAnalysisEngine
import com.azizbek.safetyai.analysis.UnavailableSafetyAnalysisEngine
import com.azizbek.safetyai.data.ThemePreferencesRepository
import com.azizbek.safetyai.data.local.AppDatabase
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.data.repository.NotificationRepository
import com.azizbek.safetyai.data.repository.RoomAssessmentRepository
import com.azizbek.safetyai.data.video.VideoFileStore

/**
 * Hand-written application-level dependency container — no DI framework for
 * a single-module app this size. Screens reach it via
 * `(LocalContext.current.applicationContext as SafetyAIApplication).container`
 * inside each screen's `viewModelFactory`.
 */
class AppContainer(context: Context) {
    private val database by lazy { AppDatabase.getInstance(context) }
    val videoFileStore by lazy { VideoFileStore(context) }
    val assessmentRepository: AssessmentRepository by lazy {
        RoomAssessmentRepository(database.assessmentDao(), videoFileStore)
    }
    val notificationRepository by lazy { NotificationRepository(database.notificationDao()) }
    val themePreferencesRepository by lazy { ThemePreferencesRepository(context) }
    val analysisEngine: SafetyAnalysisEngine by lazy { UnavailableSafetyAnalysisEngine() }
}
