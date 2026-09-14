package com.azizbek.safetyai

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azizbek.safetyai.navigation.SafetyAppScaffold
import com.azizbek.safetyai.ui.theme.SafetyAITheme
import com.azizbek.safetyai.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafetyApp()
        }
    }
}

@Composable
private fun SafetyApp() {
    val context = LocalContext.current
    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModel.factory(context))
    val storedDarkMode by themeViewModel.darkModePreference.collectAsStateWithLifecycle()
    // No stored choice yet (null) → fall back to the device's system theme.
    val isDarkMode = storedDarkMode ?: isSystemInDarkTheme()

    ApplySystemBarContrast(darkTheme = isDarkMode)

    SafetyAITheme(darkTheme = isDarkMode) {
        SafetyAppScaffold(
            isDarkMode = isDarkMode,
            onToggleDarkMode = themeViewModel::setDarkMode
        )
    }
}

/** Keeps status/navigation-bar icon contrast correct as the theme changes. */
@Composable
private fun ApplySystemBarContrast(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        SideEffect {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
