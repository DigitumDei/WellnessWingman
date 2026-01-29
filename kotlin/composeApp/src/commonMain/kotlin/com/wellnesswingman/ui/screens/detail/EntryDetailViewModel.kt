package com.wellnesswingman.ui.screens.detail

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.analysis.ExerciseAnalysisResult
import com.wellnesswingman.data.model.analysis.MealAnalysisResult
import com.wellnesswingman.data.model.analysis.SleepAnalysisResult
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class EntryDetailViewModel(
    private val entryId: Long,
    private val trackedEntryRepository: TrackedEntryRepository,
    private val entryAnalysisRepository: EntryAnalysisRepository
) : ScreenModel {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _uiState = MutableStateFlow<EntryDetailUiState>(EntryDetailUiState.Loading)
    val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

    init {
        loadEntry()
    }

    private fun loadEntry() {
        screenModelScope.launch {
            try {
                _uiState.value = EntryDetailUiState.Loading

                val entry = trackedEntryRepository.getEntryById(entryId)
                if (entry == null) {
                    _uiState.value = EntryDetailUiState.Error("Entry not found")
                    return@launch
                }

                val analysis = entryAnalysisRepository.getLatestAnalysisForEntry(entryId)

                _uiState.value = EntryDetailUiState.Success(
                    entry = entry,
                    analysis = analysis,
                    parsedAnalysis = parseAnalysis(entry.entryType, analysis)
                )
            } catch (e: Exception) {
                Napier.e("Failed to load entry $entryId", e)
                _uiState.value = EntryDetailUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun parseAnalysis(entryType: EntryType, analysis: EntryAnalysis?): ParsedAnalysis? {
        if (analysis == null) return null

        return try {
            when (entryType) {
                EntryType.MEAL -> {
                    val mealAnalysis = json.decodeFromString<MealAnalysisResult>(analysis.insightsJson)
                    ParsedAnalysis.Meal(mealAnalysis)
                }
                EntryType.EXERCISE -> {
                    val exerciseAnalysis = json.decodeFromString<ExerciseAnalysisResult>(analysis.insightsJson)
                    ParsedAnalysis.Exercise(exerciseAnalysis)
                }
                EntryType.SLEEP -> {
                    val sleepAnalysis = json.decodeFromString<SleepAnalysisResult>(analysis.insightsJson)
                    ParsedAnalysis.Sleep(sleepAnalysis)
                }
                else -> null
            }
        } catch (e: Exception) {
            Napier.w("Failed to parse analysis: ${e.message}")
            null
        }
    }

    fun deleteEntry() {
        screenModelScope.launch {
            try {
                trackedEntryRepository.deleteEntry(entryId)
                _uiState.value = EntryDetailUiState.Deleted
            } catch (e: Exception) {
                Napier.e("Failed to delete entry $entryId", e)
            }
        }
    }
}

sealed class EntryDetailUiState {
    object Loading : EntryDetailUiState()
    data class Success(
        val entry: TrackedEntry,
        val analysis: EntryAnalysis?,
        val parsedAnalysis: ParsedAnalysis?
    ) : EntryDetailUiState()
    data class Error(val message: String) : EntryDetailUiState()
    object Deleted : EntryDetailUiState()
}

sealed class ParsedAnalysis {
    data class Meal(val result: MealAnalysisResult) : ParsedAnalysis()
    data class Exercise(val result: ExerciseAnalysisResult) : ParsedAnalysis()
    data class Sleep(val result: SleepAnalysisResult) : ParsedAnalysis()
}
