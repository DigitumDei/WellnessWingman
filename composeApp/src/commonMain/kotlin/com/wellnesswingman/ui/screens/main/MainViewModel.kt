package com.wellnesswingman.ui.screens.main

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
import com.wellnesswingman.domain.capture.PendingCaptureStore
import com.wellnesswingman.domain.polar.PolarDayContext
import com.wellnesswingman.domain.polar.PolarInsightService
import com.wellnesswingman.domain.polar.PolarSyncOrchestrator
import com.wellnesswingman.domain.polar.PolarSyncTrigger
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.ui.screens.photo.PhotoReviewViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

class MainViewModel(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val entryAnalysisRepository: EntryAnalysisRepository,
    private val dailySummaryRepository: DailySummaryRepository,
    private val dailySummaryService: DailySummaryService,
    private val dailyTotalsCalculator: DailyTotalsCalculator,
    private val polarInsightService: PolarInsightService,
    private val fileSystem: FileSystem,
    private val pendingCaptureStore: PendingCaptureStore,
    private val polarSyncOrchestrator: PolarSyncOrchestrator
) : ScreenModel {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _summaryCardState = MutableStateFlow<SummaryCardState>(SummaryCardState.Hidden)
    val summaryCardState: StateFlow<SummaryCardState> = _summaryCardState.asStateFlow()

    private val _isGeneratingSummary = MutableStateFlow(false)
    val isGeneratingSummary: StateFlow<Boolean> = _isGeneratingSummary.asStateFlow()

    private val _hasPendingCapture = MutableStateFlow(false)
    val hasPendingCapture: StateFlow<Boolean> = _hasPendingCapture.asStateFlow()

    private var lastHandledPolarSyncCompletion: Instant? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    init {
        observeEntries()
        observePolarSyncStatus()
        checkPendingCapture()
        refreshPolarDataIfNeeded()
    }

    private fun refreshPolarDataIfNeeded() {
        screenModelScope.launch {
            runCatching {
                polarSyncOrchestrator.syncIfStale(PolarSyncTrigger.APP_ENTRY)
            }.onFailure { error ->
                Napier.w("Automatic Polar sync failed on app entry: ${error.message}")
            }
        }
    }

    fun checkPendingCapture() {
        screenModelScope.launch {
            try {
                val pending = pendingCaptureStore.get()
                if (pending != null && fileSystem.exists(pending.photoFilePath)) {
                    _hasPendingCapture.value = true
                    Napier.i("Found pending capture to recover: ${pending.photoFilePath}")
                }
            } catch (e: Exception) {
                Napier.e("Error checking pending capture", e)
            }
        }
    }

    fun consumePendingCapture() {
        _hasPendingCapture.value = false
    }

    private fun observeEntries() {
        screenModelScope.launch {
            try {
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                trackedEntryRepository.observeEntriesForDay(today).collect { entries ->
                    try {
                        renderTodayState(today, entries)
                    } catch (e: Exception) {
                        Napier.e("Error processing entries", e)
                        _uiState.value = MainUiState.Error(e.message ?: "Unknown error")
                    }
                }
            } catch (e: Exception) {
                Napier.e("Error observing entries", e)
                _uiState.value = MainUiState.Error(e.message ?: "Unknown error")
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

    private fun observePolarSyncStatus() {
        screenModelScope.launch {
            polarSyncOrchestrator.status.collectLatest { status ->
                val completedAt = status.lastResult?.completedAt ?: return@collectLatest
                if (status.isRunning || lastHandledPolarSyncCompletion == completedAt) {
                    return@collectLatest
                }

                lastHandledPolarSyncCompletion = completedAt
                try {
                    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val entries = trackedEntryRepository.getEntriesForDay(today)
                    renderTodayState(today, entries)
                } catch (error: Exception) {
                    Napier.w("Failed to refresh main screen after Polar sync: ${error.message}")
                }
            }
        }
    }

    private suspend fun renderTodayState(today: LocalDate, entries: List<TrackedEntry>) {
        val filteredEntries = entries.filter { it.entryType != EntryType.DAILY_SUMMARY }
        val polarContext = polarInsightService.getDayContext(today)

        if (shouldShowEmptyMainState(filteredEntries.size, polarContext.hasData)) {
            _uiState.value = MainUiState.Empty
            _summaryCardState.value = SummaryCardState.Hidden
            return
        }

        val nutritionTotals = calculateNutritionTotals(filteredEntries)
        val hasCompletedMeals = filteredEntries.any {
            it.entryType == EntryType.MEAL && it.processingStatus == ProcessingStatus.COMPLETED
        }
        val thumbnails = loadThumbnails(filteredEntries)
        _uiState.value = MainUiState.Success(
            entries = filteredEntries,
            nutritionTotals = nutritionTotals,
            hasCompletedMeals = hasCompletedMeals,
            polarContext = polarContext,
            thumbnails = thumbnails
        )
        updateSummaryCardState(today, hasMainSummaryInputs(hasCompletedMeals, polarContext.hasData))
    }

    private suspend fun updateSummaryCardState(date: LocalDate, hasSummaryInputs: Boolean) {
        if (!hasSummaryInputs) {
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

    fun loadEntries() {
        screenModelScope.launch {
            try {
                _uiState.value = MainUiState.Loading

                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val entries = trackedEntryRepository.getEntriesForDay(today)
                renderTodayState(today, entries)
            } catch (e: Exception) {
                Napier.e("Failed to load entries", e)
                _uiState.value = MainUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun refresh() {
        screenModelScope.launch {
            _isRefreshing.value = true
            try {
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val entries = trackedEntryRepository.getEntriesForDay(today)
                renderTodayState(today, entries)
            } catch (e: Exception) {
                Napier.e("Failed to refresh entries", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun generateDailySummary() {
        screenModelScope.launch {
            if (_isGeneratingSummary.value) return@launch

            try {
                _isGeneratingSummary.value = true
                _summaryCardState.value = SummaryCardState.Generating

                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                when (val result = dailySummaryService.generateSummary(today)) {
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
                Napier.e("Failed to generate daily summary", e)
                _summaryCardState.value = SummaryCardState.Error(e.message ?: "Unknown error")
            } finally {
                _isGeneratingSummary.value = false
            }
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

    fun deleteEntry(entryId: Long) {
        screenModelScope.launch {
            try {
                trackedEntryRepository.deleteEntry(entryId)
                loadEntries()
            } catch (e: Exception) {
                Napier.e("Failed to delete entry $entryId", e)
            }
        }
    }
}

sealed class MainUiState {
    object Loading : MainUiState()
    object Empty : MainUiState()
    data class Success(
        val entries: List<TrackedEntry>,
        val nutritionTotals: NutritionTotals = NutritionTotals(),
        val hasCompletedMeals: Boolean = false,
        val polarContext: PolarDayContext,
        val thumbnails: Map<Long, ByteArray> = emptyMap()
    ) : MainUiState()
    data class Error(val message: String) : MainUiState()
}

internal fun shouldShowEmptyMainState(entryCount: Int, polarHasData: Boolean): Boolean =
    entryCount == 0 && !polarHasData

internal fun hasMainSummaryInputs(hasCompletedMeals: Boolean, polarHasData: Boolean): Boolean =
    hasCompletedMeals || polarHasData

sealed class SummaryCardState {
    object Hidden : SummaryCardState()
    object NoSummary : SummaryCardState()
    object Generating : SummaryCardState()
    data class HasSummary(val summaryId: Long) : SummaryCardState()
    data class Error(val message: String) : SummaryCardState()
}
