package com.wellnesswingman.ui.screens.calendar.day

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

class DayDetailViewModel(
    private val trackedEntryRepository: TrackedEntryRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow<DayDetailUiState>(DayDetailUiState.Loading)
    val uiState: StateFlow<DayDetailUiState> = _uiState.asStateFlow()

    fun loadDay(date: LocalDate) {
        screenModelScope.launch {
            try {
                _uiState.value = DayDetailUiState.Loading

                val startInstant = date.atStartOfDayIn(TimeZone.currentSystemDefault())
                val endInstant = date.atTime(23, 59, 59).toInstant(TimeZone.currentSystemDefault())

                val entries = trackedEntryRepository.getEntriesBetween(startInstant, endInstant)

                _uiState.value = if (entries.isEmpty()) {
                    DayDetailUiState.Empty
                } else {
                    DayDetailUiState.Success(entries)
                }
            } catch (e: Exception) {
                Napier.e("Failed to load day $date", e)
                _uiState.value = DayDetailUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class DayDetailUiState {
    object Loading : DayDetailUiState()
    data class Success(val entries: List<TrackedEntry>) : DayDetailUiState()
    object Empty : DayDetailUiState()
    data class Error(val message: String) : DayDetailUiState()
}
