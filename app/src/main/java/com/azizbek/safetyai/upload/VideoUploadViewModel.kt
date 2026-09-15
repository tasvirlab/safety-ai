package com.azizbek.safetyai.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.data.video.VideoFileStore
import com.azizbek.safetyai.domain.VideoMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VideoUploadUiState {
    data object NoSelection : VideoUploadUiState
    data object Preparing : VideoUploadUiState
    data class Ready(val filePath: String, val metadata: VideoMetadata) : VideoUploadUiState
    data class Error(val message: String) : VideoUploadUiState
}

class VideoUploadViewModel(private val videoFileStore: VideoFileStore) : ViewModel() {
    private val _uiState = MutableStateFlow<VideoUploadUiState>(VideoUploadUiState.NoSelection)
    val uiState: StateFlow<VideoUploadUiState> = _uiState.asStateFlow()

    /** Called when the picker returns a URI, or null on cancellation (a no-op, not an error). */
    fun onVideoPicked(uri: Uri?) {
        if (uri == null) return
        _uiState.value = VideoUploadUiState.Preparing
        viewModelScope.launch {
            if (!videoFileStore.isReadableVideo(uri)) {
                _uiState.value = VideoUploadUiState.Error("That file couldn't be read as a video. Choose another file.")
                return@launch
            }
            val result = runCatching {
                val file = videoFileStore.copyToPrivateStorage(uri)
                videoFileStore.readMetadata(file)
            }
            result.onSuccess { metadata ->
                _uiState.value = VideoUploadUiState.Ready(metadata.filePath, metadata)
            }.onFailure {
                _uiState.value = VideoUploadUiState.Error("This video couldn't be prepared. It may be missing or in an unsupported format.")
            }
        }
    }

    fun reset() {
        (_uiState.value as? VideoUploadUiState.Ready)?.let { videoFileStore.delete(it.filePath) }
        _uiState.value = VideoUploadUiState.NoSelection
    }

    companion object {
        fun factory(videoFileStore: VideoFileStore) = viewModelFactory {
            initializer { VideoUploadViewModel(videoFileStore) }
        }
    }
}
