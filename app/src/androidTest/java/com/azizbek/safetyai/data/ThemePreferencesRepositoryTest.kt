package com.azizbek.safetyai.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreferencesRepositoryTest {

    private val repository = ThemePreferencesRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun setDarkModePersistsTheChoice() = runBlocking {
        repository.setDarkMode(true)
        assertEquals(true, repository.darkModePreference.first())

        repository.setDarkMode(false)
        assertEquals(false, repository.darkModePreference.first())
    }

    @Test
    fun clearDarkModeReturnsToFollowSystem() = runBlocking {
        repository.setDarkMode(true)
        repository.clearDarkMode()
        assertNull(repository.darkModePreference.first())
    }
}
