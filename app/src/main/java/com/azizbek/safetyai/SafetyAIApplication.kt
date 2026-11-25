package com.azizbek.safetyai

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafetyAIApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Best-effort sweep of any video file not owned by a saved assessment —
        // e.g. a recording left over from a flow abandoned mid-process-death.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val referenced = container.assessmentRepository.getAllVideoFilePaths().toSet()
            container.videoFileStore.deleteUnreferencedFiles(referenced)
            container.assessmentRepository.recoverAndSweep()
        }
    }
}
