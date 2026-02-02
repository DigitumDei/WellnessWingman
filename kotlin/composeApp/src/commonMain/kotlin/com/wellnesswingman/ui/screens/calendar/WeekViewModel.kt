package com.wellnesswingman.ui.screens.calendar

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.TrackedEntryRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.*

class WeekViewModel(
    private val trackedEntryRepository: TrackedEntryRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow<WeekUiState>(WeekUiState.Loading)
    val uiState: StateFlow<WeekUiState> = _uiState.asStateFlow()

    private val _currentWeekStart = MutableStateFlow(getWeekStart(Clock.System.now()))
    val currentWeekStart: StateFlow<LocalDate> = _currentWeekStart.asStateFlow()

    init {
        loadWeek(_currentWeekStart.value)
    }

    fun loadWeek(weekStart: LocalDate) {
        screenModelScope.launch {
            try {
                _uiState.value = WeekUiState.Loading
                _currentWeekStart.value = weekStart

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

                _uiState.value = WeekUiState.Success(
                    weekStart = weekStart,
                    entriesByDate = entriesByDate
                )
            } catch (e: Exception) {
                Napier.e("Failed to load week starting $weekStart", e)
                _uiState.value = WeekUiState.Error(e.message ?: "Unknown error")
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
