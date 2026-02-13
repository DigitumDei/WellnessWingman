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

class CalendarViewModel(
    private val trackedEntryRepository: TrackedEntryRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _currentMonth = MutableStateFlow(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
    val currentMonth: StateFlow<LocalDate> = _currentMonth.asStateFlow()

    init {
        loadMonth(_currentMonth.value)
    }

    fun loadMonth(date: LocalDate) {
        screenModelScope.launch {
            try {
                _uiState.value = CalendarUiState.Loading
                _currentMonth.value = date

                // Get start and end of month
                val firstOfMonth = LocalDate(date.year, date.month, 1)
                val lastOfMonth = LocalDate(
                    date.year,
                    date.month,
                    date.month.length(isLeapYear(date.year))
                )

                val startInstant = firstOfMonth.atStartOfDayIn(TimeZone.currentSystemDefault())
                val endInstant = lastOfMonth.atTime(23, 59, 59)
                    .toInstant(TimeZone.currentSystemDefault())

                // Get entries for the month
                val entries = trackedEntryRepository.getEntriesForDay(
                    startInstant.toEpochMilliseconds(),
                    endInstant.toEpochMilliseconds()
                )

                // Group by date
                val entriesByDate = entries.groupBy { entry ->
                    entry.capturedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
                }

                _uiState.value = CalendarUiState.Success(
                    month = date,
                    entriesByDate = entriesByDate
                )
            } catch (e: Exception) {
                Napier.e("Failed to load calendar for $date", e)
                _uiState.value = CalendarUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun previousMonth() {
        val current = _currentMonth.value
        val previous = LocalDate(current.year, current.month, 1).minus(1, DateTimeUnit.MONTH)
        loadMonth(previous)
    }

    fun nextMonth() {
        val current = _currentMonth.value
        val next = LocalDate(current.year, current.month, 1).plus(1, DateTimeUnit.MONTH)
        loadMonth(next)
    }

    fun today() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        loadMonth(today)
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
}

sealed class CalendarUiState {
    object Loading : CalendarUiState()
    data class Success(
        val month: LocalDate,
        val entriesByDate: Map<LocalDate, List<TrackedEntry>>
    ) : CalendarUiState()
    data class Error(val message: String) : CalendarUiState()
}
