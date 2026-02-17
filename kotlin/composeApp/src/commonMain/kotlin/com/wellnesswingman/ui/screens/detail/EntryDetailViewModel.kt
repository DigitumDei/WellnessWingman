package com.wellnesswingman.ui.screens.detail

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.analysis.ExerciseAnalysisResult
import com.wellnesswingman.data.model.analysis.MealAnalysisResult
import com.wellnesswingman.data.model.analysis.SleepAnalysisResult
import com.wellnesswingman.data.model.analysis.UnifiedAnalysisResult
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.analysis.BackgroundAnalysisService
import com.wellnesswingman.domain.events.StatusChangeNotifier
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
import kotlinx.serialization.json.Json

class EntryDetailViewModel(
    private val entryId: Long,
    private val trackedEntryRepository: TrackedEntryRepository,
    private val entryAnalysisRepository: EntryAnalysisRepository,
    private val fileSystem: FileSystem,
    private val backgroundAnalysisService: BackgroundAnalysisService,
    private val statusChangeNotifier: StatusChangeNotifier,
    private val audioRecordingService: AudioRecordingService,
    private val llmClientFactory: LlmClientFactory
) : ScreenModel {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var recordingJob: Job? = null

    private val _uiState = MutableStateFlow<EntryDetailUiState>(EntryDetailUiState.Loading)
    val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

    // Correction mode state
    private val _correctionState = MutableStateFlow(CorrectionState())
    val correctionState: StateFlow<CorrectionState> = _correctionState.asStateFlow()

    init {
        loadEntry()
        subscribeToStatusChanges()
    }

    private fun subscribeToStatusChanges() {
        screenModelScope.launch {
            statusChangeNotifier.statusChanges.collect { event ->
                if (event.entryId == entryId) {
                    Napier.i("Status changed to ${event.status} for entry $entryId, reloading")
                    loadEntry()
                    if (event.status == ProcessingStatus.COMPLETED) {
                        exitCorrectionMode()
                    }
                }
            }
        }
    }

    private fun loadEntry() {
        screenModelScope.launch {
            try {
                _uiState.value = EntryDetailUiState.Loading

                val entry = trackedEntryRepository.getEntryById(entryId)
                if (entry == null) {
                    _uiState.value = EntryDetailUiState.Error("Entry not found")
                    return@launch
                }

                val analysis = entryAnalysisRepository.getLatestAnalysisForEntry(entryId)

                // Load image if blob path exists
                val imageBytes = try {
                    val blobPath = entry.blobPath
                    if (blobPath != null && fileSystem.exists(blobPath)) {
                        fileSystem.readBytes(blobPath)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Napier.e("Failed to load image for entry $entryId", e)
                    null
                }

                _uiState.value = EntryDetailUiState.Success(
                    entry = entry,
                    analysis = analysis,
                    parsedAnalysis = parseAnalysis(entry.entryType, analysis),
                    imageBytes = imageBytes
                )
            } catch (e: Exception) {
                Napier.e("Failed to load entry $entryId", e)
                _uiState.value = EntryDetailUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun parseAnalysis(entryType: EntryType, analysis: EntryAnalysis?): ParsedAnalysis? {
        if (analysis == null) return null

        return try {
            // Try unified format first
            try {
                val unified = json.decodeFromString<UnifiedAnalysisResult>(analysis.insightsJson)
                unified.mealAnalysis?.let {
                    ParsedAnalysis.Meal(
                        result = it,
                        unifiedWarnings = unified.warnings,
                        unifiedConfidence = unified.confidence
                    )
                }
                    ?: unified.exerciseAnalysis?.let { ParsedAnalysis.Exercise(it) }
                    ?: unified.sleepAnalysis?.let { ParsedAnalysis.Sleep(it) }
            } catch (e: Exception) {
                // Fall back to type-specific parsing
                when (entryType) {
                    EntryType.MEAL -> {
                        val mealAnalysis = json.decodeFromString<MealAnalysisResult>(analysis.insightsJson)
                        ParsedAnalysis.Meal(result = mealAnalysis)
                    }
                    EntryType.EXERCISE -> {
                        val exerciseAnalysis = json.decodeFromString<ExerciseAnalysisResult>(analysis.insightsJson)
                        ParsedAnalysis.Exercise(exerciseAnalysis)
                    }
                    EntryType.SLEEP -> {
                        val sleepAnalysis = json.decodeFromString<SleepAnalysisResult>(analysis.insightsJson)
                        ParsedAnalysis.Sleep(sleepAnalysis)
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            Napier.e("Failed to parse analysis for entry $entryId: ${e.message}", e)
            Napier.e("Raw JSON (first 500 chars): ${analysis.insightsJson.take(500)}")
            null
        }
    }

    fun deleteEntry() {
        screenModelScope.launch {
            try {
                trackedEntryRepository.deleteEntry(entryId)
                _uiState.value = EntryDetailUiState.Deleted
            } catch (e: Exception) {
                Napier.e("Failed to delete entry $entryId", e)
            }
        }
    }

    fun retryAnalysis() {
        screenModelScope.launch {
            try {
                val entry = trackedEntryRepository.getEntryById(entryId)
                if (entry == null) {
                    Napier.e("Entry not found for retry: $entryId")
                    return@launch
                }

                Napier.i("Queueing analysis retry for entry $entryId")
                backgroundAnalysisService.queueEntry(entryId, entry.userNotes)
            } catch (e: Exception) {
                Napier.e("Failed to start analysis retry for entry $entryId", e)
            }
        }
    }

    // Correction mode methods

    /**
     * Enters correction mode and pre-populates with existing user notes.
     */
    fun enterCorrectionMode() {
        screenModelScope.launch {
            val currentState = _uiState.value
            if (currentState is EntryDetailUiState.Success) {
                _correctionState.update {
                    CorrectionState(
                        isActive = true,
                        correctionText = currentState.entry.userNotes ?: ""
                    )
                }
            }
        }
    }

    /**
     * Exits correction mode and clears state.
     */
    fun exitCorrectionMode() {
        _correctionState.update {
            CorrectionState(isActive = false)
        }
    }

    /**
     * Updates the correction text.
     */
    fun updateCorrectionText(text: String) {
        _correctionState.update { it.copy(correctionText = text) }
    }

    /**
     * Appends transcribed text to the correction.
     */
    fun appendTranscription(transcribedText: String) {
        _correctionState.update { state ->
            val newText = if (state.correctionText.isBlank()) {
                transcribedText
            } else {
                "${state.correctionText}\n$transcribedText"
            }
            state.copy(correctionText = newText, isTranscribing = false)
        }
    }

    /**
     * Toggles voice recording on/off.
     */
    fun toggleRecording() {
        screenModelScope.launch {
            if (_correctionState.value.isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    /**
     * Checks if microphone permission is granted (for UI layer to decide whether to request).
     */
    suspend fun checkMicPermission(): Boolean {
        return audioRecordingService.checkPermission()
    }

    private suspend fun startRecording() {
        try {
            if (!audioRecordingService.checkPermission()) {
                Napier.w("Microphone permission not granted")
                return
            }

            val audioDir = "${fileSystem.getAppDataDirectory()}/audio"
            fileSystem.createDirectory(audioDir)
            val audioPath = "$audioDir/correction_${Clock.System.now().toEpochMilliseconds()}.m4a"

            if (audioRecordingService.startRecording(audioPath)) {
                _correctionState.update { it.copy(isRecording = true, recordingDurationSeconds = 0, transcriptionError = null) }
                startDurationTimer()
            }
        } catch (e: Exception) {
            Napier.e("Failed to start recording", e)
        }
    }

    private suspend fun stopRecording() {
        try {
            recordingJob?.cancel()
            _correctionState.update { it.copy(isRecording = false, recordingDurationSeconds = 0) }

            val result = audioRecordingService.stopRecording()
            val audioPath = result.filePath
            if (result.isSuccess && audioPath != null) {
                _correctionState.update { it.copy(isTranscribing = true) }
                transcribeAudio(audioPath)
            }
        } catch (e: Exception) {
            Napier.e("Failed to stop recording", e)
        } finally {
            _correctionState.update { it.copy(isTranscribing = false) }
        }
    }

    private fun startDurationTimer() {
        recordingJob = screenModelScope.launch {
            var elapsed = 0
            while (_correctionState.value.isRecording) {
                delay(1000)
                elapsed++
                _correctionState.update { it.copy(recordingDurationSeconds = elapsed) }
            }
        }
    }

    private suspend fun transcribeAudio(audioPath: String) {
        try {
            val audioBytes = fileSystem.readBytes(audioPath)
            val llmClient = llmClientFactory.createForCurrentProvider()
            val transcription = llmClient.transcribeAudio(audioBytes)

            appendTranscription(transcription)

            fileSystem.delete(audioPath)
        } catch (e: Exception) {
            Napier.e("Failed to transcribe audio", e)
            _correctionState.update {
                it.copy(transcriptionError = "Transcription failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Submits the correction for re-analysis.
     */
    fun submitCorrection() {
        screenModelScope.launch {
            val correctionText = _correctionState.value.correctionText.trim()
            if (correctionText.isBlank()) {
                Napier.w("Cannot submit empty correction")
                return@launch
            }

            try {
                _correctionState.update { it.copy(isSubmitting = true) }

                // Save the correction text to user notes
                trackedEntryRepository.updateUserNotes(entryId, correctionText)

                // Queue correction analysis
                Napier.i("Queueing correction analysis for entry $entryId")
                backgroundAnalysisService.queueCorrection(entryId, correctionText)

                // The status change subscription will handle reloading when complete
            } catch (e: Exception) {
                Napier.e("Failed to submit correction for entry $entryId", e)
                _correctionState.update { it.copy(isSubmitting = false) }
            }
        }
    }
}

sealed class EntryDetailUiState {
    object Loading : EntryDetailUiState()
    data class Success(
        val entry: TrackedEntry,
        val analysis: EntryAnalysis?,
        val parsedAnalysis: ParsedAnalysis?,
        val imageBytes: ByteArray? = null
    ) : EntryDetailUiState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Success

            if (entry != other.entry) return false
            if (analysis != other.analysis) return false
            if (parsedAnalysis != other.parsedAnalysis) return false
            if (imageBytes != null) {
                if (other.imageBytes == null) return false
                if (!imageBytes.contentEquals(other.imageBytes)) return false
            } else if (other.imageBytes != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = entry.hashCode()
            result = 31 * result + (analysis?.hashCode() ?: 0)
            result = 31 * result + (parsedAnalysis?.hashCode() ?: 0)
            result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
            return result
        }
    }
    data class Error(val message: String) : EntryDetailUiState()
    object Deleted : EntryDetailUiState()
}

sealed class ParsedAnalysis {
    data class Meal(
        val result: MealAnalysisResult,
        val unifiedWarnings: List<String> = emptyList(),
        val unifiedConfidence: Double? = null
    ) : ParsedAnalysis()
    data class Exercise(val result: ExerciseAnalysisResult) : ParsedAnalysis()
    data class Sleep(val result: SleepAnalysisResult) : ParsedAnalysis()
}

/**
 * State for correction mode.
 */
data class CorrectionState(
    val isActive: Boolean = false,
    val correctionText: String = "",
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val isSubmitting: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val transcriptionError: String? = null
) {
    val canSubmit: Boolean
        get() = isActive && correctionText.isNotBlank() && !isSubmitting && !isRecording && !isTranscribing
}
