package com.wellnesswingman.ui.screens.calendar.day

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.DailySummaryResult
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.NutritionTotals
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.analysis.MealAnalysisResult
import com.wellnesswingman.data.model.analysis.UnifiedAnalysisResult
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.analysis.DailySummaryService
import com.wellnesswingman.domain.analysis.DailyTotalsCalculator
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.ui.screens.main.SummaryCardState
import com.wellnesswingman.ui.screens.photo.PhotoReviewViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.serialization.json.Json

class DayDetailViewModel(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val entryAnalysisRepository: EntryAnalysisRepository,
    private val dailySummaryRepository: DailySummaryRepository,
    private val dailySummaryService: DailySummaryService,
    private val dailyTotalsCalculator: DailyTotalsCalculator,
    private val fileSystem: FileSystem
) : ScreenModel {

    private val _uiState = MutableStateFlow<DayDetailUiState>(DayDetailUiState.Loading)
    val uiState: StateFlow<DayDetailUiState> = _uiState.asStateFlow()

    private val _summaryCardState = MutableStateFlow<SummaryCardState>(SummaryCardState.Hidden)
    val summaryCardState: StateFlow<SummaryCardState> = _summaryCardState.asStateFlow()

    private val _isGeneratingSummary = MutableStateFlow(false)
    val isGeneratingSummary: StateFlow<Boolean> = _isGeneratingSummary.asStateFlow()

    private var currentDate: LocalDate? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun loadDay(date: LocalDate) {
        // Avoid reloading if we're already displaying this date
        if (currentDate == date && _uiState.value is DayDetailUiState.Success) {
            return
        }
        currentDate = date

        screenModelScope.launch {
            try {
                _uiState.value = DayDetailUiState.Loading

                val startInstant = date.atStartOfDayIn(TimeZone.currentSystemDefault())
                val endInstant = date.atTime(23, 59, 59).toInstant(TimeZone.currentSystemDefault())

                val entries = trackedEntryRepository.getEntriesForDay(
                    startInstant.toEpochMilliseconds(),
                    endInstant.toEpochMilliseconds()
                )

                // Filter out daily summary entries from the list
                val filteredEntries = entries.filter { it.entryType != EntryType.DAILY_SUMMARY }

                if (filteredEntries.isEmpty()) {
                    _uiState.value = DayDetailUiState.Empty
                    _summaryCardState.value = SummaryCardState.Hidden
                } else {
                    val nutritionTotals = calculateNutritionTotals(filteredEntries)
                    val hasCompletedMeals = filteredEntries.any {
                        it.entryType == EntryType.MEAL && it.processingStatus == ProcessingStatus.COMPLETED
                    }

                    val thumbnails = loadThumbnails(filteredEntries)
                    _uiState.value = DayDetailUiState.Success(
                        entries = filteredEntries,
                        nutritionTotals = nutritionTotals,
                        hasCompletedMeals = hasCompletedMeals,
                        thumbnails = thumbnails
                    )

                    updateSummaryCardState(date, hasCompletedMeals)
                }
            } catch (e: Exception) {
                Napier.e("Failed to load day $date", e)
                _uiState.value = DayDetailUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun calculateNutritionTotals(entries: List<TrackedEntry>): NutritionTotals {
        try {
            val completedMeals = entries.filter {
                it.entryType == EntryType.MEAL && it.processingStatus == ProcessingStatus.COMPLETED
            }

            if (completedMeals.isEmpty()) {
                return NutritionTotals()
            }

            val mealAnalyses = mutableListOf<MealAnalysisResult>()

            for (entry in completedMeals) {
                try {
                    val analysis = entryAnalysisRepository.getLatestAnalysisForEntry(entry.entryId)
                    if (analysis != null) {
                        try {
                            // Try unified result first
                            val unifiedResult = json.decodeFromString<UnifiedAnalysisResult>(analysis.insightsJson)
                            unifiedResult.mealAnalysis?.let { mealAnalyses.add(it) }
                        } catch (e: Exception) {
                            try {
                                // Fall back to direct meal analysis
                                val mealResult = json.decodeFromString<MealAnalysisResult>(analysis.insightsJson)
                                mealAnalyses.add(mealResult)
                            } catch (e2: Exception) {
                                Napier.w("Failed to parse meal analysis for entry ${entry.entryId}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Napier.w("Failed to get analysis for entry ${entry.entryId}: ${e.message}")
                }
            }

            return dailyTotalsCalculator.calculate(mealAnalyses)
        } catch (e: Exception) {
            Napier.e("Failed to calculate nutrition totals", e)
            return NutritionTotals()
        }
    }

    private suspend fun updateSummaryCardState(date: LocalDate, hasCompletedMeals: Boolean) {
        if (!hasCompletedMeals) {
            _summaryCardState.value = SummaryCardState.Hidden
            return
        }

        val existingSummary = dailySummaryRepository.getSummaryForDate(date)
        _summaryCardState.value = if (existingSummary != null) {
            SummaryCardState.HasSummary(existingSummary.summaryId)
        } else {
            SummaryCardState.NoSummary
        }
    }

    private suspend fun loadThumbnails(entries: List<TrackedEntry>): Map<Long, ByteArray> {
        val thumbnails = mutableMapOf<Long, ByteArray>()
        for (entry in entries) {
            val blobPath = entry.blobPath ?: continue
            try {
                val previewPath = PhotoReviewViewModel.getPreviewPath(blobPath)
                if (fileSystem.exists(previewPath)) {
                    thumbnails[entry.entryId] = fileSystem.readBytes(previewPath)
                }
            } catch (e: Exception) {
                Napier.w("Failed to load thumbnail for entry ${entry.entryId}", e)
            }
        }
        return thumbnails
    }

    fun generateDailySummary() {
        val date = currentDate ?: return

        screenModelScope.launch {
            if (_isGeneratingSummary.value) return@launch

            try {
                _isGeneratingSummary.value = true
                _summaryCardState.value = SummaryCardState.Generating

                when (val result = dailySummaryService.generateSummary(date)) {
                    is DailySummaryResult.Success -> {
                        _summaryCardState.value = SummaryCardState.HasSummary(result.summary.summaryId)
                    }
                    is DailySummaryResult.NoEntries -> {
                        _summaryCardState.value = SummaryCardState.NoSummary
                    }
                    is DailySummaryResult.Error -> {
                        _summaryCardState.value = SummaryCardState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to generate daily summary for $date", e)
                _summaryCardState.value = SummaryCardState.Error(e.message ?: "Unknown error")
            } finally {
                _isGeneratingSummary.value = false
            }
        }
    }
}

sealed class DayDetailUiState {
    object Loading : DayDetailUiState()
    data class Success(
        val entries: List<TrackedEntry>,
        val nutritionTotals: NutritionTotals = NutritionTotals(),
        val hasCompletedMeals: Boolean = false,
        val thumbnails: Map<Long, ByteArray> = emptyMap()
    ) : DayDetailUiState()
    object Empty : DayDetailUiState()
    data class Error(val message: String) : DayDetailUiState()
}
