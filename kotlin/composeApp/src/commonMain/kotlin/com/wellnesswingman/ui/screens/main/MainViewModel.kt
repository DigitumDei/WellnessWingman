package com.wellnesswingman.ui.screens.main

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.util.DateTimeUtil
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

class MainViewModel(
    private val trackedEntryRepository: TrackedEntryRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadEntries()
    }

    fun loadEntries() {
        screenModelScope.launch {
            try {
                _uiState.value = MainUiState.Loading

                val entries = trackedEntryRepository.getAllEntries()

                if (entries.isEmpty()) {
                    _uiState.value = MainUiState.Empty
                } else {
                    _uiState.value = MainUiState.Success(entries)
                }
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
                val entries = trackedEntryRepository.getAllEntries()
                _uiState.value = if (entries.isEmpty()) {
                    MainUiState.Empty
                } else {
                    MainUiState.Success(entries)
                }
            } catch (e: Exception) {
                Napier.e("Failed to refresh entries", e)
            } finally {
                _isRefreshing.value = false
            }
        }
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
    data class Success(val entries: List<TrackedEntry>) : MainUiState()
    data class Error(val message: String) : MainUiState()
}
