package com.wellnesswingman.ui.screens.summary

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.DailySummaryResult
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.domain.analysis.DailySummaryService
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
    private val dailySummaryService: DailySummaryService
) : ScreenModel {

    private val _uiState = MutableStateFlow<DailySummaryUiState>(DailySummaryUiState.Loading)
    val uiState: StateFlow<DailySummaryUiState> = _uiState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var currentDate: LocalDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

    init {
        loadSummary(currentDate)
    }

    fun loadSummary(date: LocalDate) {
        currentDate = date
        screenModelScope.launch {
            try {
                _uiState.value = DailySummaryUiState.Loading

                val summary = dailySummaryRepository.getSummaryForDate(date)

                if (summary != null) {
                    _uiState.value = DailySummaryUiState.Success(summary, date)
                } else {
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
                _uiState.value = DailySummaryUiState.Generating(currentDate)

                when (val result = dailySummaryService.generateSummary(currentDate)) {
                    is DailySummaryResult.Success -> {
                        _uiState.value = DailySummaryUiState.Success(result.summary, currentDate)
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
                _uiState.value = DailySummaryUiState.Generating(currentDate)

                when (val result = dailySummaryService.regenerateSummary(currentDate)) {
                    is DailySummaryResult.Success -> {
                        _uiState.value = DailySummaryUiState.Success(result.summary, currentDate)
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
}

sealed class DailySummaryUiState {
    object Loading : DailySummaryUiState()
    data class Success(val summary: DailySummary, val date: LocalDate) : DailySummaryUiState()
    data class NoSummary(val date: LocalDate) : DailySummaryUiState()
    data class NoEntries(val date: LocalDate) : DailySummaryUiState()
    data class Generating(val date: LocalDate) : DailySummaryUiState()
    data class Error(val message: String) : DailySummaryUiState()
}
