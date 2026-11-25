package com.azizbek.safetyai.domain

/** The fields that change the scientific analysis. Everything else on [Assessment] is descriptive metadata. */
fun Assessment.analysisInputs(): List<Any?> = listOf(loadKg, suddenForce, coupling, taskType, video.filePath)
