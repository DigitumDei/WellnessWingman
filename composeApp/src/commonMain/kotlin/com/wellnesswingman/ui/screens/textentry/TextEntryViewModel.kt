package com.wellnesswingman.ui.screens.textentry

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.domain.capture.TextEntryProcessor
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.ui.common.CommentsState
import com.wellnesswingman.ui.common.UserCommentsManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TextEntryUiState(
    val isSaving: Boolean = false,
    val saveError: String? = null,
    /**
     * Set when the entry saved but no API key is configured, so analysis could not be queued.
     * The entry is real either way; only its analysis is missing.
     */
    val apiKeyMissing: Boolean = false,
    /** The saved entry, once it exists. */
    val savedEntryId: Long? = null
)

/**
 * Captures an entry the user describes rather than photographs.
 *
 * For the case where the thing happened but the photo did not — a meal eaten out, a run whose
 * watch died. Text and voice share the same [UserCommentsManager] used by photo notes and
 * check-ins, so speech lands in the text field where it can be corrected before saving rather
 * than being committed blind.
 */
class TextEntryViewModel(
    private val textEntryProcessor: TextEntryProcessor,
    audioRecordingService: AudioRecordingService,
    llmClientFactory: LlmClientFactory,
    fileSystem: FileSystem
) : ScreenModel {

    private val _uiState = MutableStateFlow(TextEntryUiState())
    val uiState: StateFlow<TextEntryUiState> = _uiState.asStateFlow()

    private val commentsManager = UserCommentsManager(
        audioRecordingService = audioRecordingService,
        llmClientFactory = llmClientFactory,
        fileSystem = fileSystem,
        scope = screenModelScope,
        audioFilePrefix = "textentry"
    )

    val commentsState: StateFlow<CommentsState> = commentsManager.commentsState

    init {
        commentsManager.loadComments(null)
    }

    fun updateText(text: String) {
        commentsManager.updateText(text)
    }

    fun toggleRecording() {
        commentsManager.toggleRecording()
    }

    suspend fun checkMicPermission(): Boolean = commentsManager.checkMicPermission()

    /**
     * Saves the description as an entry and queues its analysis.
     *
     * @param onSaved invoked with the new entry id once it exists, so the caller can navigate to
     *   it. Not invoked when the API key is missing — that case is surfaced first, since landing
     *   on a detail screen that will never fill in is confusing.
     */
    fun save(onSaved: (Long) -> Unit) {
        val text = commentsState.value.text.trim()
        if (text.isEmpty() || _uiState.value.isSaving) return

        _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)

        screenModelScope.launch {
            try {
                val result = textEntryProcessor.process(text)

                commentsManager.markSaved()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    savedEntryId = result.entryId,
                    apiKeyMissing = result.apiKeyMissing
                )

                if (!result.apiKeyMissing) onSaved(result.entryId)
            } catch (e: Exception) {
                Napier.e("Failed to save a text entry", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveError = e.message ?: "Could not save your entry"
                )
            }
        }
    }

    /** Dismisses the missing-key warning and continues to the entry that was saved anyway. */
    fun acknowledgeApiKeyMissing(onContinue: (Long) -> Unit) {
        val entryId = _uiState.value.savedEntryId ?: return
        _uiState.value = _uiState.value.copy(apiKeyMissing = false)
        onContinue(entryId)
    }
}
