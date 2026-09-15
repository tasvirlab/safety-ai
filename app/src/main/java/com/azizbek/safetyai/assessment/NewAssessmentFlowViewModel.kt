package com.azizbek.safetyai.assessment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.data.video.VideoFileStore
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.VideoMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** In-progress state of the New Assessment flow, carried across its screens. */
data class NewAssessmentFlowState(
    val draft: AssessmentDraft? = null,
    val videoFilePath: String? = null,
    val videoMetadata: VideoMetadata? = null,
    val isSaving: Boolean = false,
    val savedAssessmentId: String? = null
)

/**
 * Scoped to the "New Assessment" nested navigation graph (one instance shared
 * by every screen in the flow, cleared once the flow finishes or is
 * abandoned) — this is what lets the assessment details, captured/selected
 * video, and review screens all see the same in-progress data without
 * threading it through nav arguments.
 */
class NewAssessmentFlowViewModel(
    private val repository: AssessmentRepository,
    private val videoFileStore: VideoFileStore
) : ViewModel() {

    private val _state = MutableStateFlow(NewAssessmentFlowState())
    val state: StateFlow<NewAssessmentFlowState> = _state.asStateFlow()

    fun setDraft(draft: AssessmentDraft) {
        _state.update { it.copy(draft = draft) }
    }

    /** Replaces the held video, deleting whichever file was previously held (e.g. a discarded retake) so it never becomes an orphaned draft. */
    fun setVideo(filePath: String, metadata: VideoMetadata) {
        val previous = _state.value.videoFilePath
        if (previous != null && previous != filePath) videoFileStore.delete(previous)
        _state.update { it.copy(videoFilePath = filePath, videoMetadata = metadata) }
    }

    /** Deletes the currently-held video file (if any) and clears it from state — used by Retake/Choose another/discard. */
    fun discardVideo() {
        _state.value.videoFilePath?.let(videoFileStore::delete)
        _state.update { it.copy(videoFilePath = null, videoMetadata = null) }
    }

    /** Saves the assessment exactly once even if the caller taps Save repeatedly. */
    fun save(onSaved: (String) -> Unit) {
        val current = _state.value
        if (current.isSaving || current.savedAssessmentId != null) return
        val draft = current.draft ?: return
        val metadata = current.videoMetadata ?: return

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val assessment = Assessment(
                id = UUID.randomUUID().toString(),
                name = draft.name,
                projectOrSite = draft.projectOrSite,
                workerReference = draft.workerReference,
                taskType = draft.taskType,
                customTaskDescription = draft.customTaskDescription,
                loadKg = draft.loadKg,
                suddenForce = draft.suddenForce,
                coupling = draft.coupling,
                source = draft.source,
                video = metadata,
                status = AssessmentStatus.READY_FOR_ANALYSIS,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            repository.save(assessment)
            _state.update { it.copy(isSaving = false, savedAssessmentId = assessment.id) }
            onSaved(assessment.id)
        }
    }

    /** If the flow is abandoned (graph popped) without a successful save, its held video file must not linger. */
    override fun onCleared() {
        val current = _state.value
        if (current.savedAssessmentId == null) {
            current.videoFilePath?.let(videoFileStore::delete)
        }
    }

    companion object {
        fun factory(repository: AssessmentRepository, videoFileStore: VideoFileStore) = viewModelFactory {
            initializer { NewAssessmentFlowViewModel(repository, videoFileStore) }
        }
    }
}
