package com.wellnesswingman.ui.screens.calendar

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.repository.TrackedEntryRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.*

class YearViewModel(
    private val trackedEntryRepository: TrackedEntryRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow<YearUiState>(YearUiState.Loading)
    val uiState: StateFlow<YearUiState> = _uiState.asStateFlow()

    private val _currentYear = MutableStateFlow(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
    )
    val currentYear: StateFlow<Int> = _currentYear.asStateFlow()

    init {
        loadYear(_currentYear.value)
    }

    fun loadYear(year: Int) {
        screenModelScope.launch {
            try {
                _uiState.value = YearUiState.Loading
                _currentYear.value = year

                val startInstant = LocalDate(year, Month.JANUARY, 1)
                    .atStartOfDayIn(TimeZone.currentSystemDefault())
                val endInstant = LocalDate(year, Month.DECEMBER, 31)
                    .atTime(23, 59, 59)
                    .toInstant(TimeZone.currentSystemDefault())

                val entries = trackedEntryRepository.getEntriesForDay(
                    startInstant.toEpochMilliseconds(),
                    endInstant.toEpochMilliseconds()
                )

                // Count entries by month
                val entriesByMonth = entries
                    .groupBy { entry ->
                        entry.capturedAt.toLocalDateTime(TimeZone.currentSystemDefault()).month
                    }
                    .mapValues { it.value.size }

                _uiState.value = YearUiState.Success(
                    year = year,
                    entriesByMonth = entriesByMonth
                )
            } catch (e: Exception) {
                Napier.e("Failed to load year $year", e)
                _uiState.value = YearUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun previousYear() {
        loadYear(_currentYear.value - 1)
    }

    fun nextYear() {
        loadYear(_currentYear.value + 1)
    }
}

sealed class YearUiState {
    object Loading : YearUiState()
    data class Success(
        val year: Int,
        val entriesByMonth: Map<Month, Int>
    ) : YearUiState()
    data class Error(val message: String) : YearUiState()
}
