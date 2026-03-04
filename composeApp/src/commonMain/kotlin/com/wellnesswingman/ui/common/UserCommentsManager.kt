package com.wellnesswingman.ui.common

import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.FileSystem
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Shared state for user comments and voice recording across summary screens.
 */
data class CommentsState(
    val text: String = "",
    val savedText: String = "",
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val transcriptionError: String? = null
) {
    val hasUnsavedChanges: Boolean get() = text != savedText
}

/**
 * Reusable manager for user comments (text + voice notes) on summary screens.
 * Handles text editing, audio recording, and transcription.
 *
 * The actual persistence (e.g., saving to a repository) is delegated to the caller.
 * After a successful save, the caller should invoke [markSaved] to update the UI state.
 */
class UserCommentsManager(
    private val audioRecordingService: AudioRecordingService,
    private val llmClientFactory: LlmClientFactory,
    private val fileSystem: FileSystem,
    private val scope: CoroutineScope,
    private val audioFilePrefix: String = "comment"
) {
    private val _commentsState = MutableStateFlow(CommentsState())
    val commentsState: StateFlow<CommentsState> = _commentsState.asStateFlow()

    private var recordingJob: Job? = null

    /**
     * Loads existing comments into state, marking them as saved.
     */
    fun loadComments(savedText: String?) {
        val text = savedText ?: ""
        _commentsState.value = CommentsState(text = text, savedText = text)
    }

    /**
     * Updates the comment text (unsaved).
     */
    fun updateText(text: String) {
        _commentsState.update { it.copy(text = text) }
    }

    /**
     * Marks the current text as saved (call after successful persistence).
     */
    fun markSaved() {
        _commentsState.update { it.copy(savedText = it.text) }
    }

    /**
     * Checks microphone permission.
     */
    suspend fun checkMicPermission(): Boolean = audioRecordingService.checkPermission()

    /**
     * Toggles audio recording on/off.
     */
    fun toggleRecording() {
        scope.launch {
            if (_commentsState.value.isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    private suspend fun startRecording() {
        try {
            if (!audioRecordingService.checkPermission()) {
                Napier.w("Microphone permission not granted")
                return
            }

            val audioDir = "${fileSystem.getAppDataDirectory()}/audio"
            fileSystem.createDirectory(audioDir)
            val audioPath = "$audioDir/${audioFilePrefix}_${Clock.System.now().toEpochMilliseconds()}.m4a"

            if (audioRecordingService.startRecording(audioPath)) {
                _commentsState.update { it.copy(isRecording = true, recordingDurationSeconds = 0, transcriptionError = null) }
                startDurationTimer()
            }
        } catch (e: Exception) {
            Napier.e("Failed to start recording", e)
        }
    }

    private suspend fun stopRecording() {
        try {
            recordingJob?.cancel()
            _commentsState.update { it.copy(isRecording = false, recordingDurationSeconds = 0) }

            val result = audioRecordingService.stopRecording()
            val audioPath = result.filePath
            if (result.isSuccess && audioPath != null) {
                _commentsState.update { it.copy(isTranscribing = true) }
                transcribeAudio(audioPath)
            }
        } catch (e: Exception) {
            Napier.e("Failed to stop recording", e)
        }
    }

    private fun startDurationTimer() {
        recordingJob = scope.launch {
            var elapsed = 0
            while (_commentsState.value.isRecording) {
                delay(1000)
                elapsed++
                _commentsState.update { it.copy(recordingDurationSeconds = elapsed) }
            }
        }
    }

    private suspend fun transcribeAudio(audioPath: String) {
        try {
            val audioBytes = fileSystem.readBytes(audioPath)
            val llmClient = llmClientFactory.createForCurrentProvider()
            val transcription = llmClient.transcribeAudio(audioBytes)

            _commentsState.update { state ->
                val newText = if (state.text.isBlank()) transcription
                else "${state.text}\n$transcription"
                state.copy(text = newText, isTranscribing = false)
            }

            fileSystem.delete(audioPath)
        } catch (e: Exception) {
            Napier.e("Failed to transcribe audio", e)
            _commentsState.update {
                it.copy(isTranscribing = false, transcriptionError = "Transcription failed: ${e.message ?: "Unknown error"}")
            }
        }
    }
}
