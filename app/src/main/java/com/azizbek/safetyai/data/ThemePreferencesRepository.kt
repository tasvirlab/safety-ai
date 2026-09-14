package com.azizbek.safetyai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_preferences")

/**
 * Persists the user's dark-mode choice. `null` means "no choice made yet" —
 * callers fall back to the device's system theme in that case; this
 * repository only ever reports what the user explicitly picked.
 */
class ThemePreferencesRepository(private val context: Context) {

    private val darkModeKey = booleanPreferencesKey("dark_mode_enabled")

    val darkModePreference: Flow<Boolean?> =
        context.themeDataStore.data.map { preferences -> preferences[darkModeKey] }

    suspend fun setDarkMode(enabled: Boolean) {
        context.themeDataStore.edit { preferences -> preferences[darkModeKey] = enabled }
    }
}
