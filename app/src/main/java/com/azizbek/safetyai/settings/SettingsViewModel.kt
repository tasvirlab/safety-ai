package com.azizbek.safetyai.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.data.ThemePreferencesRepository
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.data.video.VideoFileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val assessmentRepository: AssessmentRepository,
    private val videoFileStore: VideoFileStore
) : ViewModel() {

    val darkModePreference: StateFlow<Boolean?> = themePreferencesRepository.darkModePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val assessmentCount: StateFlow<Int> = assessmentRepository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes

    init {
        refreshStorage()
    }

    private fun refreshStorage() {
        viewModelScope.launch { _storageBytes.value = videoFileStore.totalManagedStorageBytes() }
    }

    /** null = follow system. */
    fun setAppearance(darkMode: Boolean?) {
        viewModelScope.launch {
            if (darkMode == null) themePreferencesRepository.clearDarkMode() else themePreferencesRepository.setDarkMode(darkMode)
        }
    }

    /** Deletes any video file in app-private storage not owned by a saved assessment (abandoned recordings/uploads). */
    fun clearTemporaryFiles() {
        viewModelScope.launch {
            val referenced = assessmentRepository.getAllVideoFilePaths().toSet()
            videoFileStore.deleteUnreferencedFiles(referenced)
            refreshStorage()
        }
    }

    fun deleteAllData(onDeleted: () -> Unit) {
        viewModelScope.launch {
            assessmentRepository.deleteAll()
            refreshStorage()
            onDeleted()
        }
    }

    companion object {
        fun factory(
            themePreferencesRepository: ThemePreferencesRepository,
            assessmentRepository: AssessmentRepository,
            videoFileStore: VideoFileStore
        ) = viewModelFactory {
            initializer { SettingsViewModel(themePreferencesRepository, assessmentRepository, videoFileStore) }
        }
    }
}
