package com.wellnesswingman.ui.screens.summary

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.DailySummaryResult
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.domain.analysis.DailySummaryService
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.FileSystem
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class DailySummaryViewModel(
    private val dailySummaryRepository: DailySummaryRepository,
    private val dailySummaryService: DailySummaryService,
    private val audioRecordingService: AudioRecordingService,
    private val llmClientFactory: LlmClientFactory,
    private val fileSystem: FileSystem
) : ScreenModel {

    private val _uiState = MutableStateFlow<DailySummaryUiState>(DailySummaryUiState.Loading)
    val uiState: StateFlow<DailySummaryUiState> = _uiState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _commentsState = MutableStateFlow(CommentsState())
    val commentsState: StateFlow<CommentsState> = _commentsState.asStateFlow()

    private var currentDate: LocalDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

    private var recordingJob: Job? = null

    init {
        loadSummary(currentDate)
    }

    fun loadSummary(date: LocalDate) {
        val state = _uiState.value
        if (currentDate == date && (state is DailySummaryUiState.Success ||
                    state is DailySummaryUiState.NoSummary ||
                    state is DailySummaryUiState.NoEntries)) {
            return
        }
        currentDate = date
        screenModelScope.launch {
            try {
                _uiState.value = DailySummaryUiState.Loading

                val summary = dailySummaryRepository.getSummaryForDate(date)

                if (summary != null) {
                    val existingComments = summary.userComments ?: ""
                    _commentsState.value = CommentsState(text = existingComments, savedText = existingComments)
                    if (summary.highlights.isNotBlank()) {
                        _uiState.value = DailySummaryUiState.Success(summary, date)
                    } else {
                        _uiState.value = DailySummaryUiState.NoSummary(date)
                    }
                } else {
                    _commentsState.value = CommentsState()
                    _uiState.value = DailySummaryUiState.NoSummary(date)
                }
            } catch (e: Exception) {
                Napier.e("Failed to load summary for $date", e)
                _uiState.value = DailySummaryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun generateSummary() {
        screenModelScope.launch {
            try {
                _isGenerating.value = true
                val existingComments = _commentsState.value.text.takeIf { it.isNotBlank() }

                // If there's a comments-only placeholder row, remove it before generating
                // to avoid a UNIQUE constraint violation in the service's insertSummary call
                val existingSummary = dailySummaryRepository.getSummaryForDate(currentDate)
                if (existingSummary != null && existingSummary.highlights.isBlank()) {
                    dailySummaryRepository.deleteSummaryByDate(currentDate)
                }

                _uiState.value = DailySummaryUiState.Generating(currentDate)

                when (val result = dailySummaryService.generateSummary(currentDate)) {
                    is DailySummaryResult.Success -> {
                        if (existingComments != null) {
                            dailySummaryRepository.updateUserComments(currentDate, existingComments)
                            _uiState.value = DailySummaryUiState.Success(
                                result.summary.copy(userComments = existingComments),
                                currentDate
                            )
                        } else {
                            _uiState.value = DailySummaryUiState.Success(result.summary, currentDate)
                        }
                    }
                    is DailySummaryResult.NoEntries -> {
                        _uiState.value = DailySummaryUiState.NoEntries(currentDate)
                    }
                    is DailySummaryResult.Error -> {
                        _uiState.value = DailySummaryUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to generate summary", e)
                _uiState.value = DailySummaryUiState.Error(e.message ?: "Unknown error")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun regenerateSummary() {
        screenModelScope.launch {
            try {
                _isGenerating.value = true
                val existingComments = _commentsState.value.text.takeIf { it.isNotBlank() }
                _uiState.value = DailySummaryUiState.Generating(currentDate)

                when (val result = dailySummaryService.regenerateSummary(currentDate)) {
                    is DailySummaryResult.Success -> {
                        if (existingComments != null) {
                            dailySummaryRepository.updateUserComments(currentDate, existingComments)
                            _uiState.value = DailySummaryUiState.Success(
                                result.summary.copy(userComments = existingComments),
                                currentDate
                            )
                        } else {
                            _uiState.value = DailySummaryUiState.Success(result.summary, currentDate)
                        }
                    }
                    is DailySummaryResult.NoEntries -> {
                        _uiState.value = DailySummaryUiState.NoEntries(currentDate)
                    }
                    is DailySummaryResult.Error -> {
                        _uiState.value = DailySummaryUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to regenerate summary", e)
                _uiState.value = DailySummaryUiState.Error(e.message ?: "Unknown error")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // --- Comments ---

    fun updateCommentsText(text: String) {
        _commentsState.update { it.copy(text = text) }
    }

    fun saveComments() {
        screenModelScope.launch {
            val text = _commentsState.value.text
            try {
                val existing = dailySummaryRepository.getSummaryForDate(currentDate)
                if (existing != null) {
                    dailySummaryRepository.updateUserComments(currentDate, text.takeIf { it.isNotBlank() })
                } else if (text.isNotBlank()) {
                    dailySummaryRepository.upsertSummary(
                        DailySummary(summaryDate = currentDate, userComments = text)
                    )
                }
                _commentsState.update { it.copy(savedText = text) }

                // Update the in-memory success state if present
                val state = _uiState.value
                if (state is DailySummaryUiState.Success) {
                    _uiState.value = state.copy(summary = state.summary.copy(userComments = text.takeIf { it.isNotBlank() }))
                }
            } catch (e: Exception) {
                Napier.e("Failed to save comments", e)
            }
        }
    }

    // --- Voice recording ---

    fun toggleRecording() {
        screenModelScope.launch {
            if (_commentsState.value.isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    suspend fun checkMicPermission(): Boolean = audioRecordingService.checkPermission()

    private suspend fun startRecording() {
        try {
            if (!audioRecordingService.checkPermission()) {
                Napier.w("Microphone permission not granted")
                return
            }

            val audioDir = "${fileSystem.getAppDataDirectory()}/audio"
            fileSystem.createDirectory(audioDir)
            val audioPath = "$audioDir/daycomment_${Clock.System.now().toEpochMilliseconds()}.m4a"

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
        } finally {
            _commentsState.update { it.copy(isTranscribing = false) }
        }
    }

    private fun startDurationTimer() {
        recordingJob = screenModelScope.launch {
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
                it.copy(transcriptionError = "Transcription failed: ${e.message ?: "Unknown error"}")
            }
        }
    }
}

sealed class DailySummaryUiState {
    object Loading : DailySummaryUiState()
    data class Success(val summary: DailySummary, val date: LocalDate) : DailySummaryUiState()
    data class NoSummary(val date: LocalDate) : DailySummaryUiState()
    data class NoEntries(val date: LocalDate) : DailySummaryUiState()
    data class Generating(val date: LocalDate) : DailySummaryUiState()
    data class Error(val message: String) : DailySummaryUiState()
}

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
