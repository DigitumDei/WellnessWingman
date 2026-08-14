package com.wellnesswingman.ui.common

import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.platform.OnDeviceTranscriptionService
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
    private val audioFilePrefix: String = "comment",
    private val maxTextLength: Int? = null,
    private val onDeviceTranscriptionService: OnDeviceTranscriptionService? = null
) {
    private val _commentsState = MutableStateFlow(CommentsState())
    val commentsState: StateFlow<CommentsState> = _commentsState.asStateFlow()

    private var recordingJob: Job? = null

    /**
     * Loads existing comments into state, marking them as saved.
     */
    fun loadComments(savedText: String?) {
        val text = limitCommentText(savedText ?: "", maxTextLength)
        _commentsState.value = CommentsState(text = text, savedText = text)
    }

    /**
     * Updates the comment text (unsaved).
     */
    fun updateText(text: String) {
        _commentsState.update { it.copy(text = limitCommentText(text, maxTextLength)) }
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
    suspend fun checkMicPermission(): Boolean = onDeviceTranscriptionService?.checkPermission()
        ?: audioRecordingService.checkPermission()

    /**
     * Toggles audio recording on/off.
     */
    fun toggleRecording() {
        val wasRecording = _commentsState.value.isRecording
        scope.launch {
            // Natural end-of-speech can update state between the tap and this coroutine running.
            // In that case the user's original action is already complete, so do nothing.
            if (_commentsState.value.isRecording != wasRecording) return@launch

            if (onDeviceTranscriptionService != null) {
                if (wasRecording) {
                    stopOnDeviceRecording()
                } else if (hasTextCapacity()) {
                    startOnDeviceRecording()
                } else {
                    _commentsState.update { it.copy(transcriptionError = textCapacityReachedError()) }
                }
            } else if (wasRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    /**
     * Releases platform recording resources when the owning screen model is disposed.
     */
    fun dispose() {
        recordingJob?.cancel()
        if (!_commentsState.value.isRecording) return

        if (onDeviceTranscriptionService != null) {
            onDeviceTranscriptionService.cancel()
        } else {
            audioRecordingService.cancelRecording()
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

    private suspend fun startOnDeviceRecording() {
        val service = onDeviceTranscriptionService ?: return
        try {
            if (!service.checkPermission()) {
                _commentsState.update {
                    it.copy(transcriptionError = "Microphone permission not granted")
                }
                return
            }

            service.startListening { result ->
                scope.launch { handleOnDeviceCompletion(result) }
            }
            _commentsState.update {
                it.copy(
                    isRecording = true,
                    recordingDurationSeconds = 0,
                    transcriptionError = null
                )
            }
            startDurationTimer()
        } catch (e: Exception) {
            service.cancel()
            Napier.e("Failed to start on-device transcription", e)
            _commentsState.update {
                it.copy(
                    isRecording = false,
                    transcriptionError = "Voice input unavailable: ${e.message ?: "Unknown error"}"
                )
            }
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

    private suspend fun stopOnDeviceRecording() {
        val service = onDeviceTranscriptionService ?: return
        recordingJob?.cancel()
        _commentsState.update {
            it.copy(isRecording = false, recordingDurationSeconds = 0, isTranscribing = true)
        }

        try {
            val transcription = service.stopListening()
                ?: throw IllegalStateException("No speech was recognized")
            applyTranscription(transcription)
        } catch (e: Exception) {
            Napier.e("Failed to transcribe on-device audio", e)
            _commentsState.update {
                it.copy(
                    isTranscribing = false,
                    transcriptionError = "Transcription failed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    private suspend fun handleOnDeviceCompletion(result: Result<String?>) {
        recordingJob?.cancel()
        _commentsState.update {
            it.copy(isRecording = false, recordingDurationSeconds = 0, isTranscribing = true)
        }

        try {
            val transcription = result.getOrThrow()
                ?: throw IllegalStateException("No speech was recognized")
            applyTranscription(transcription)
        } catch (e: Exception) {
            Napier.e("Failed to transcribe on-device audio", e)
            _commentsState.update {
                it.copy(
                    isTranscribing = false,
                    transcriptionError = "Transcription failed: ${e.message ?: "Unknown error"}"
                )
            }
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

            applyTranscription(transcription)
        } catch (e: Exception) {
            Napier.e("Failed to transcribe audio", e)
            _commentsState.update {
                it.copy(isTranscribing = false, transcriptionError = "Transcription failed: ${e.message ?: "Unknown error"}")
            }
        } finally {
            try {
                fileSystem.delete(audioPath)
            } catch (e: Exception) {
                Napier.w("Failed to delete temporary audio file", e)
            }
        }
    }

    private fun applyTranscription(transcription: String) {
        _commentsState.update { state ->
            val result = appendTranscriptionText(state.text, transcription, maxTextLength)
            state.copy(
                text = result.text,
                isTranscribing = false,
                transcriptionError = if (result.wasTruncated) {
                    textCapacityTruncatedError()
                } else {
                    null
                }
            )
        }
    }

    private fun hasTextCapacity(): Boolean =
        maxTextLength == null || _commentsState.value.text.length < maxTextLength

    private fun textCapacityReachedError(): String =
        maxTextLength?.let { "Voice input is unavailable because the text limit is $it characters" }
            ?: "Voice input is unavailable because the text limit has been reached"

    private fun textCapacityTruncatedError(): String =
        maxTextLength?.let { "Voice input was truncated at the $it-character limit" }
            ?: "Voice input was truncated at the text limit"
}

internal fun limitCommentText(text: String, maxTextLength: Int?): String =
    maxTextLength?.let(text::take) ?: text

internal data class TranscriptionTextResult(
    val text: String,
    val wasTruncated: Boolean
)

internal fun appendTranscriptionText(
    existingText: String,
    transcription: String,
    maxTextLength: Int?
): TranscriptionTextResult {
    val cleanedTranscription = transcription.trim()
    if (cleanedTranscription.isBlank()) {
        throw IllegalStateException("No speech was recognized")
    }

    val newText = if (existingText.isBlank()) cleanedTranscription
    else "$existingText\n$cleanedTranscription"
    val limitedText = limitCommentText(newText, maxTextLength)
    val resultText = if (limitedText.length < newText.length && limitedText.endsWith('\n')) {
        limitedText.dropLast(1)
    } else {
        limitedText
    }
    return TranscriptionTextResult(
        text = resultText,
        wasTruncated = resultText.length < newText.length
    )
}
