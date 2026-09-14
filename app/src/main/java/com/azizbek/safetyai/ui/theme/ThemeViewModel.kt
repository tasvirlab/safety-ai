package com.azizbek.safetyai.ui.theme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.data.ThemePreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the app's dark-mode preference above [SafetyAITheme]. `null` from
 * [darkModePreference] means the user hasn't chosen yet — callers fall back
 * to `isSystemInDarkTheme()` for that case; this class stays screen-agnostic
 * and never reads the system theme itself.
 *
 * Built with a plain [viewModelFactory] rather than a DI framework — this is
 * the only screen-agnostic dependency the app has right now.
 */
class ThemeViewModel(private val repository: ThemePreferencesRepository) : ViewModel() {

    val darkModePreference: StateFlow<Boolean?> = repository.darkModePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkMode(enabled) }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer { ThemeViewModel(ThemePreferencesRepository(context.applicationContext)) }
        }
    }
}
