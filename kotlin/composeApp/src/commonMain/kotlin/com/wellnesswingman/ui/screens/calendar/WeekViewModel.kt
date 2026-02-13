package com.wellnesswingman.ui.screens.calendar

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeeklySummary
import com.wellnesswingman.data.model.WeeklySummaryResult
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.analysis.WeeklySummaryService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.*

class WeekViewModel(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val weeklySummaryService: WeeklySummaryService
) : ScreenModel {

    private val _uiState = MutableStateFlow<WeekUiState>(WeekUiState.Loading)
    val uiState: StateFlow<WeekUiState> = _uiState.asStateFlow()

    private val _currentWeekStart = MutableStateFlow(getWeekStart(Clock.System.now()))
    val currentWeekStart: StateFlow<LocalDate> = _currentWeekStart.asStateFlow()

    private val _weeklySummaryState = MutableStateFlow<WeeklySummaryState>(WeeklySummaryState.Hidden)
    val weeklySummaryState: StateFlow<WeeklySummaryState> = _weeklySummaryState.asStateFlow()

    private val _entryCounts = MutableStateFlow(EntryCounts())
    val entryCounts: StateFlow<EntryCounts> = _entryCounts.asStateFlow()

    init {
        loadWeek(_currentWeekStart.value)
    }

    fun loadWeek(weekStart: LocalDate) {
        screenModelScope.launch {
            try {
                _uiState.value = WeekUiState.Loading
                _currentWeekStart.value = weekStart
                _weeklySummaryState.value = WeeklySummaryState.Loading

                val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
                val startInstant = weekStart.atStartOfDayIn(TimeZone.currentSystemDefault())
                val endInstant = weekEnd.atTime(23, 59, 59).toInstant(TimeZone.currentSystemDefault())

                val entries = trackedEntryRepository.getEntriesForDay(
                    startInstant.toEpochMilliseconds(),
                    endInstant.toEpochMilliseconds()
                )

                val entriesByDate = entries.groupBy { entry ->
                    entry.capturedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
                }

                // Calculate entry counts from completed entries
                val completedEntries = entries.filter {
                    it.processingStatus == ProcessingStatus.COMPLETED &&
                    it.entryType != EntryType.DAILY_SUMMARY
                }

                val counts = EntryCounts(
                    mealCount = completedEntries.count { it.entryType == EntryType.MEAL },
                    exerciseCount = completedEntries.count { it.entryType == EntryType.EXERCISE },
                    sleepCount = completedEntries.count { it.entryType == EntryType.SLEEP },
                    otherCount = completedEntries.count {
                        it.entryType != EntryType.MEAL &&
                        it.entryType != EntryType.EXERCISE &&
                        it.entryType != EntryType.SLEEP &&
                        it.entryType != EntryType.DAILY_SUMMARY
                    },
                    totalEntries = completedEntries.size
                )
                _entryCounts.value = counts

                _uiState.value = WeekUiState.Success(
                    weekStart = weekStart,
                    entriesByDate = entriesByDate
                )

                // Check for existing summary
                checkForExistingSummary(weekStart)

            } catch (e: Exception) {
                Napier.e("Failed to load week starting $weekStart", e)
                _uiState.value = WeekUiState.Error(e.message ?: "Unknown error")
                _weeklySummaryState.value = WeeklySummaryState.Hidden
            }
        }
    }

    private suspend fun checkForExistingSummary(weekStart: LocalDate) {
        try {
            val existingSummary = weeklySummaryService.getSummaryForWeek(weekStart)
            if (existingSummary != null) {
                val highlightsList = existingSummary.highlights.lines().filter { it.isNotBlank() }
                val recommendationsList = existingSummary.recommendations.lines().filter { it.isNotBlank() }
                _weeklySummaryState.value = WeeklySummaryState.HasSummary(
                    summary = existingSummary,
                    highlightsList = highlightsList,
                    recommendationsList = recommendationsList
                )
            } else {
                _weeklySummaryState.value = WeeklySummaryState.NoSummary
            }
        } catch (e: Exception) {
            Napier.e("Failed to check for existing summary", e)
            _weeklySummaryState.value = WeeklySummaryState.NoSummary
        }
    }

    fun generateWeeklySummary() {
        val weekStart = _currentWeekStart.value
        screenModelScope.launch {
            _weeklySummaryState.value = WeeklySummaryState.Generating

            when (val result = weeklySummaryService.generateSummary(weekStart)) {
                is WeeklySummaryResult.Success -> {
                    _weeklySummaryState.value = WeeklySummaryState.HasSummary(
                        summary = result.summary,
                        highlightsList = result.highlightsList,
                        recommendationsList = result.recommendationsList
                    )
                }
                is WeeklySummaryResult.NoEntries -> {
                    _weeklySummaryState.value = WeeklySummaryState.Error("No completed entries for this week")
                }
                is WeeklySummaryResult.Error -> {
                    _weeklySummaryState.value = WeeklySummaryState.Error(result.message)
                }
            }
        }
    }

    fun regenerateWeeklySummary() {
        val weekStart = _currentWeekStart.value
        screenModelScope.launch {
            _weeklySummaryState.value = WeeklySummaryState.Generating

            when (val result = weeklySummaryService.regenerateSummary(weekStart)) {
                is WeeklySummaryResult.Success -> {
                    _weeklySummaryState.value = WeeklySummaryState.HasSummary(
                        summary = result.summary,
                        highlightsList = result.highlightsList,
                        recommendationsList = result.recommendationsList
                    )
                }
                is WeeklySummaryResult.NoEntries -> {
                    _weeklySummaryState.value = WeeklySummaryState.Error("No completed entries for this week")
                }
                is WeeklySummaryResult.Error -> {
                    _weeklySummaryState.value = WeeklySummaryState.Error(result.message)
                }
            }
        }
    }

    fun previousWeek() {
        val current = _currentWeekStart.value
        val previous = current.minus(7, DateTimeUnit.DAY)
        loadWeek(previous)
    }

    fun nextWeek() {
        val current = _currentWeekStart.value
        val next = current.plus(7, DateTimeUnit.DAY)
        loadWeek(next)
    }

    fun today() {
        val weekStart = getWeekStart(Clock.System.now())
        loadWeek(weekStart)
    }

    private fun getWeekStart(instant: Instant): LocalDate {
        val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val dayOfWeek = date.dayOfWeek.value % 7 // 0 = Sunday
        return date.minus(dayOfWeek, DateTimeUnit.DAY)
    }
}

sealed class WeekUiState {
    object Loading : WeekUiState()
    data class Success(
        val weekStart: LocalDate,
        val entriesByDate: Map<LocalDate, List<TrackedEntry>>
    ) : WeekUiState()
    data class Error(val message: String) : WeekUiState()
}

sealed class WeeklySummaryState {
    object Hidden : WeeklySummaryState()
    object Loading : WeeklySummaryState()
    object NoSummary : WeeklySummaryState()
    object Generating : WeeklySummaryState()
    data class HasSummary(
        val summary: WeeklySummary,
        val highlightsList: List<String>,
        val recommendationsList: List<String>
    ) : WeeklySummaryState()
    data class Error(val message: String) : WeeklySummaryState()
}

data class EntryCounts(
    val mealCount: Int = 0,
    val exerciseCount: Int = 0,
    val sleepCount: Int = 0,
    val otherCount: Int = 0,
    val totalEntries: Int = 0
)
