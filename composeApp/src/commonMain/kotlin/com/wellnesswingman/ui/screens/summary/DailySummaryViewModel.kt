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
import com.wellnesswingman.ui.common.CommentsState
import com.wellnesswingman.ui.common.UserCommentsManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var currentDate: LocalDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val commentsManager = UserCommentsManager(
        audioRecordingService = audioRecordingService,
        llmClientFactory = llmClientFactory,
        fileSystem = fileSystem,
        scope = screenModelScope,
        audioFilePrefix = "daycomment"
    )

    val commentsState: StateFlow<CommentsState> = commentsManager.commentsState

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
                    commentsManager.loadComments(summary.userComments)
                    if (summary.highlights.isNotBlank()) {
                        _uiState.value = DailySummaryUiState.Success(summary, date)
                    } else {
                        _uiState.value = DailySummaryUiState.NoSummary(date)
                    }
                } else {
                    commentsManager.loadComments(null)
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
                val existingComments = commentsManager.commentsState.value.text.takeIf { it.isNotBlank() }

                // If there's a comments-only placeholder row, remove it before generating
                // to avoid a UNIQUE constraint violation in the service's insertSummary call
                val existingSummary = dailySummaryRepository.getSummaryForDate(currentDate)
                if (existingSummary != null && existingSummary.highlights.isBlank()) {
                    dailySummaryRepository.deleteSummaryByDate(currentDate)
                }

                _uiState.value = DailySummaryUiState.Generating(currentDate)

                when (val result = dailySummaryService.generateSummary(currentDate, userComments = existingComments)) {
                    is DailySummaryResult.Success -> handleSummarySuccess(result, existingComments)
                    is DailySummaryResult.NoEntries -> _uiState.value = DailySummaryUiState.NoEntries(currentDate)
                    is DailySummaryResult.Error -> _uiState.value = DailySummaryUiState.Error(result.message)
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
                val existingComments = commentsManager.commentsState.value.text.takeIf { it.isNotBlank() }
                _uiState.value = DailySummaryUiState.Generating(currentDate)

                when (val result = dailySummaryService.regenerateSummary(currentDate, userComments = existingComments)) {
                    is DailySummaryResult.Success -> handleSummarySuccess(result, existingComments)
                    is DailySummaryResult.NoEntries -> _uiState.value = DailySummaryUiState.NoEntries(currentDate)
                    is DailySummaryResult.Error -> _uiState.value = DailySummaryUiState.Error(result.message)
                }
            } catch (e: Exception) {
                Napier.e("Failed to regenerate summary", e)
                _uiState.value = DailySummaryUiState.Error(e.message ?: "Unknown error")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private suspend fun handleSummarySuccess(result: DailySummaryResult.Success, existingComments: String?) {
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

    // --- Comments ---

    fun updateCommentsText(text: String) {
        commentsManager.updateText(text)
    }

    fun saveComments() {
        screenModelScope.launch {
            val text = commentsManager.commentsState.value.text
            try {
                val existing = dailySummaryRepository.getSummaryForDate(currentDate)
                if (existing != null) {
                    dailySummaryRepository.updateUserComments(currentDate, text.takeIf { it.isNotBlank() })
                } else if (text.isNotBlank()) {
                    dailySummaryRepository.upsertSummary(
                        DailySummary(summaryDate = currentDate, userComments = text)
                    )
                }
                commentsManager.markSaved()

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
        commentsManager.toggleRecording()
    }

    suspend fun checkMicPermission(): Boolean = commentsManager.checkMicPermission()
}

sealed class DailySummaryUiState {
    object Loading : DailySummaryUiState()
    data class Success(val summary: DailySummary, val date: LocalDate) : DailySummaryUiState()
    data class NoSummary(val date: LocalDate) : DailySummaryUiState()
    data class NoEntries(val date: LocalDate) : DailySummaryUiState()
    data class Generating(val date: LocalDate) : DailySummaryUiState()
    data class Error(val message: String) : DailySummaryUiState()
}
