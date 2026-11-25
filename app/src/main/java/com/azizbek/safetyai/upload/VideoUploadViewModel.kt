package com.azizbek.safetyai.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.data.video.VideoFileStore
import com.azizbek.safetyai.domain.VideoMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

sealed interface VideoUploadUiState {
    data object NoSelection : VideoUploadUiState
    data object Preparing : VideoUploadUiState
    data class Ready(val filePath: String, val metadata: VideoMetadata) : VideoUploadUiState
    data class Error(val message: String) : VideoUploadUiState
}

class VideoUploadViewModel(private val videoFileStore: VideoFileStore) : ViewModel() {
    private val _uiState = MutableStateFlow<VideoUploadUiState>(VideoUploadUiState.NoSelection)
    val uiState: StateFlow<VideoUploadUiState> = _uiState.asStateFlow()

    private var importJob: Job? = null

    /** Called when the picker returns a URI, or null on cancellation (a no-op, not an error). */
    fun onVideoPicked(uri: Uri?) {
        if (uri == null) return
        importJob?.cancel()
        (_uiState.value as? VideoUploadUiState.Ready)?.let { videoFileStore.delete(it.filePath) }
        _uiState.value = VideoUploadUiState.Preparing
        importJob = viewModelScope.launch {
            if (!videoFileStore.isReadableVideo(uri)) {
                _uiState.value = VideoUploadUiState.Error("That file couldn't be read as a video. Choose another file.")
                return@launch
            }
            var file: File? = null
            try {
                file = videoFileStore.copyToPrivateStorage(uri)
                val metadata = videoFileStore.readMetadata(file)
                _uiState.value = VideoUploadUiState.Ready(metadata.filePath, metadata)
            } catch (cancelled: CancellationException) {
                file?.let { videoFileStore.delete(it.absolutePath) }
                throw cancelled
            } catch (error: Exception) {
                file?.let { videoFileStore.delete(it.absolutePath) }
                _uiState.value = VideoUploadUiState.Error(
                    if (error is IOException && error.isOutOfSpace()) "Not enough storage space to import this video."
                    else "This video couldn't be prepared. It may be missing or in an unsupported format."
                )
            }
        }
    }

    private fun IOException.isOutOfSpace() = message?.contains("ENOSPC", ignoreCase = true) == true ||
        message?.contains("No space left", ignoreCase = true) == true

    fun reset() {
        importJob?.cancel()
        (_uiState.value as? VideoUploadUiState.Ready)?.let { videoFileStore.delete(it.filePath) }
        _uiState.value = VideoUploadUiState.NoSelection
    }

    companion object {
        fun factory(videoFileStore: VideoFileStore) = viewModelFactory {
            initializer { VideoUploadViewModel(videoFileStore) }
        }
    }
}
