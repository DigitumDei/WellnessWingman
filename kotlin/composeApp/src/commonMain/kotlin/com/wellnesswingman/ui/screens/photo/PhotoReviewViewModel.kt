package com.wellnesswingman.ui.screens.photo

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.analysis.AnalysisOrchestrator
import com.wellnesswingman.platform.CameraCaptureService
import com.wellnesswingman.platform.CaptureResult
import com.wellnesswingman.platform.PhotoResizer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class PhotoReviewViewModel(
    private val cameraService: CameraCaptureService,
    private val photoResizer: PhotoResizer,
    private val trackedEntryRepository: TrackedEntryRepository,
    private val analysisOrchestrator: AnalysisOrchestrator
) : ScreenModel {

    private val _uiState = MutableStateFlow<PhotoReviewUiState>(PhotoReviewUiState.Initial)
    val uiState: StateFlow<PhotoReviewUiState> = _uiState.asStateFlow()

    fun captureFromCamera() {
        screenModelScope.launch {
            try {
                _uiState.value = PhotoReviewUiState.Capturing

                when (val result = cameraService.capturePhoto()) {
                    is CaptureResult.Success -> {
                        val resizedBytes = photoResizer.resizeForUpload(result.bytes)
                        _uiState.value = PhotoReviewUiState.Review(
                            photoPath = result.photoPath,
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
                        val resizedBytes = photoResizer.resizeForUpload(result.bytes)
                        _uiState.value = PhotoReviewUiState.Review(
                            photoPath = result.photoPath,
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

    fun confirmPhoto(entryType: EntryType, userNotes: String = "") {
        screenModelScope.launch {
            try {
                val reviewState = _uiState.value as? PhotoReviewUiState.Review ?: return@launch
                _uiState.value = PhotoReviewUiState.Processing

                // Create entry
                val entry = TrackedEntry(
                    entryType = entryType,
                    capturedAt = Clock.System.now(),
                    userNotes = userNotes.ifBlank { null },
                    photoPath = reviewState.photoPath
                )
                val entryId = trackedEntryRepository.insertEntry(entry)

                // Start analysis
                analysisOrchestrator.analyzeEntry(entryId, reviewState.photoBytes)

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
}

sealed class PhotoReviewUiState {
    object Initial : PhotoReviewUiState()
    object Capturing : PhotoReviewUiState()
    object Picking : PhotoReviewUiState()
    data class Review(val photoPath: String, val photoBytes: ByteArray) : PhotoReviewUiState()
    object Processing : PhotoReviewUiState()
    data class Success(val entryId: Long) : PhotoReviewUiState()
    data class Error(val message: String) : PhotoReviewUiState()
    object Cancelled : PhotoReviewUiState()
}
