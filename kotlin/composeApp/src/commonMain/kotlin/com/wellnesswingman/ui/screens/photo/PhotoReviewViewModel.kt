package com.wellnesswingman.ui.screens.photo

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.analysis.AnalysisOrchestrator
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.CameraCaptureService
import com.wellnesswingman.platform.CaptureResult
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.platform.PhotoResizer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class PhotoReviewViewModel(
    private val cameraService: CameraCaptureService,
    private val photoResizer: PhotoResizer,
    private val trackedEntryRepository: TrackedEntryRepository,
    private val analysisOrchestrator: AnalysisOrchestrator,
    private val audioRecordingService: AudioRecordingService,
    private val fileSystem: FileSystem,
    private val llmClientFactory: LlmClientFactory
) : ScreenModel {

    private val _uiState = MutableStateFlow<PhotoReviewUiState>(PhotoReviewUiState.Initial)
    val uiState: StateFlow<PhotoReviewUiState> = _uiState.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _newTranscription = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val newTranscription: SharedFlow<String> = _newTranscription.asSharedFlow()

    private val _transcriptionError = MutableStateFlow<String?>(null)
    val transcriptionError: StateFlow<String?> = _transcriptionError.asStateFlow()

    private var recordingJob: Job? = null
    private var currentAudioPath: String? = null

    fun captureFromCamera() {
        screenModelScope.launch {
            try {
                _uiState.value = PhotoReviewUiState.Capturing

                when (val result = cameraService.capturePhoto()) {
                    is CaptureResult.Success -> {
                        val resizedBytes = photoResizer.resize(result.bytes)
                        _uiState.value = PhotoReviewUiState.Review(
                            blobPath = result.photoPath,
                            photoBytes = resizedBytes
                        )
                    }
                    is CaptureResult.Error -> {
                        _uiState.value = PhotoReviewUiState.Error(result.message)
                    }
                    is CaptureResult.Cancelled -> {
                        _uiState.value = PhotoReviewUiState.Initial
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to capture photo", e)
                _uiState.value = PhotoReviewUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun pickFromGallery() {
        screenModelScope.launch {
            try {
                _uiState.value = PhotoReviewUiState.Picking

                when (val result = cameraService.pickFromGallery()) {
                    is CaptureResult.Success -> {
                        val resizedBytes = photoResizer.resize(result.bytes)
                        _uiState.value = PhotoReviewUiState.Review(
                            blobPath = result.photoPath,
                            photoBytes = resizedBytes
                        )
                    }
                    is CaptureResult.Error -> {
                        _uiState.value = PhotoReviewUiState.Error(result.message)
                    }
                    is CaptureResult.Cancelled, null -> {
                        _uiState.value = PhotoReviewUiState.Initial
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to pick photo", e)
                _uiState.value = PhotoReviewUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun toggleRecording() {
        screenModelScope.launch {
            if (_isRecording.value) {
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
            val audioPath = "$audioDir/recording_${Clock.System.now().toEpochMilliseconds()}.m4a"

            if (audioRecordingService.startRecording(audioPath)) {
                currentAudioPath = audioPath
                _isRecording.value = true
                _transcriptionError.value = null
                startDurationTimer()
            }
        } catch (e: Exception) {
            Napier.e("Failed to start recording", e)
        }
    }

    private suspend fun stopRecording() {
        try {
            recordingJob?.cancel()
            _isRecording.value = false
            _recordingDuration.value = 0L

            val result = audioRecordingService.stopRecording()
            val audioPath = result.filePath
            if (result.isSuccess && audioPath != null) {
                _isTranscribing.value = true
                transcribeAudio(audioPath)
            }
        } catch (e: Exception) {
            Napier.e("Failed to stop recording", e)
        } finally {
            _isTranscribing.value = false
        }
    }

    private fun startDurationTimer() {
        recordingJob = screenModelScope.launch {
            var elapsed = 0L
            while (_isRecording.value) {
                delay(100)
                elapsed += 100
                _recordingDuration.value = elapsed
            }
        }
    }

    private suspend fun transcribeAudio(audioPath: String) {
        try {
            _transcriptionError.value = null
            val audioBytes = fileSystem.readBytes(audioPath)
            val llmClient = llmClientFactory.createForCurrentProvider()
            val transcription = llmClient.transcribeAudio(audioBytes)

            // Emit transcription so UI can append to notes field
            _newTranscription.tryEmit(transcription)

            // Delete audio file after successful transcription
            fileSystem.delete(audioPath)
        } catch (e: Exception) {
            Napier.e("Failed to transcribe audio", e)
            _transcriptionError.value = "Transcription failed: ${e.message ?: "Unknown error"}"
            // Keep audio file if transcription fails
        }
    }

    fun clearTranscriptionError() {
        _transcriptionError.value = null
    }

    fun confirmPhoto(userNotes: String = "") {
        screenModelScope.launch {
            try {
                val reviewState = _uiState.value as? PhotoReviewUiState.Review ?: return@launch
                _uiState.value = PhotoReviewUiState.Processing

                // Generate preview thumbnail
                generatePreview(reviewState.photoBytes, reviewState.blobPath)

                // Create entry with user notes (already includes any transcribed text)
                val entry = TrackedEntry(
                    entryType = EntryType.UNKNOWN,
                    capturedAt = Clock.System.now(),
                    userNotes = userNotes.ifBlank { null },
                    blobPath = reviewState.blobPath
                )
                val entryId = trackedEntryRepository.insertEntry(entry)

                // Fetch the created entry with its ID
                val createdEntry = trackedEntryRepository.getEntryById(entryId) ?: throw Exception("Failed to retrieve created entry")

                // Start analysis in background - don't wait for it to complete
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        analysisOrchestrator.processEntry(createdEntry, userNotes)
                        Napier.i("Background analysis completed for entry $entryId")
                    } catch (e: Exception) {
                        Napier.e("Background analysis failed for entry $entryId", e)
                    }
                }

                _uiState.value = PhotoReviewUiState.Success(entryId)
            } catch (e: Exception) {
                Napier.e("Failed to create entry", e)
                _uiState.value = PhotoReviewUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun retry() {
        _uiState.value = PhotoReviewUiState.Initial
    }

    fun cancel() {
        _uiState.value = PhotoReviewUiState.Cancelled
    }

    /**
     * Creates an entry directly from photo bytes (for platform-specific camera implementations).
     */
    fun createEntryFromPhoto(photoBytes: ByteArray, userNotes: String = "") {
        screenModelScope.launch {
            try {
                _uiState.value = PhotoReviewUiState.Processing

                // Resize photo
                val resizedBytes = photoResizer.resize(photoBytes)

                // Save photo to disk
                val photosDir = "${fileSystem.getAppDataDirectory()}/photos"
                fileSystem.createDirectory(photosDir)
                val photoPath = "$photosDir/photo_${Clock.System.now().toEpochMilliseconds()}.jpg"
                fileSystem.writeBytes(photoPath, resizedBytes)

                // Generate preview thumbnail
                generatePreview(resizedBytes, photoPath)

                // Create entry with saved photo path (notes already include any transcribed text)
                val entry = TrackedEntry(
                    entryType = EntryType.UNKNOWN,
                    capturedAt = Clock.System.now(),
                    userNotes = userNotes.ifBlank { null },
                    blobPath = photoPath
                )
                val entryId = trackedEntryRepository.insertEntry(entry)

                // Fetch the created entry with its ID
                val createdEntry = trackedEntryRepository.getEntryById(entryId) ?: throw Exception("Failed to retrieve created entry")

                // Start analysis in background - don't wait for it to complete
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        analysisOrchestrator.processEntry(createdEntry, userNotes)
                        Napier.i("Background analysis completed for entry $entryId")
                    } catch (e: Exception) {
                        Napier.e("Background analysis failed for entry $entryId", e)
                    }
                }

                _uiState.value = PhotoReviewUiState.Success(entryId)
            } catch (e: Exception) {
                Napier.e("Failed to create entry from photo", e)
                _uiState.value = PhotoReviewUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun generatePreview(photoBytes: ByteArray, blobPath: String) {
        try {
            val previewPath = getPreviewPath(blobPath)
            val previewBytes = photoResizer.resize(
                photoBytes = photoBytes,
                maxWidth = 400,
                maxHeight = 400,
                quality = 70
            )
            fileSystem.writeBytes(previewPath, previewBytes)
            Napier.d("Generated preview at $previewPath (${previewBytes.size} bytes)")
        } catch (e: Exception) {
            Napier.w("Failed to generate preview for $blobPath", e)
        }
    }

    companion object {
        fun getPreviewPath(blobPath: String): String {
            val lastDot = blobPath.lastIndexOf('.')
            return if (lastDot > 0) {
                "${blobPath.substring(0, lastDot)}_preview${blobPath.substring(lastDot)}"
            } else {
                "${blobPath}_preview"
            }
        }
    }
}

sealed class PhotoReviewUiState {
    object Initial : PhotoReviewUiState()
    object Capturing : PhotoReviewUiState()
    object Picking : PhotoReviewUiState()
    data class Review(val blobPath: String, val photoBytes: ByteArray) : PhotoReviewUiState()
    object Processing : PhotoReviewUiState()
    data class Success(val entryId: Long) : PhotoReviewUiState()
    data class Error(val message: String) : PhotoReviewUiState()
    object Cancelled : PhotoReviewUiState()
}
