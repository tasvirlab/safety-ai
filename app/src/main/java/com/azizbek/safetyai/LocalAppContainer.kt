package com.azizbek.safetyai

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Fetches the app-wide [AppContainer] from the current [android.content.Context]'s Application. */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current.applicationContext as SafetyAIApplication
    return context.container
}
