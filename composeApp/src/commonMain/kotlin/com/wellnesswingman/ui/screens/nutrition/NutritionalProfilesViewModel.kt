package com.wellnesswingman.ui.screens.nutrition

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.repository.NutritionalProfileRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NutritionalProfilesViewModel(
    private val repository: NutritionalProfileRepository
) : ScreenModel {

    val uiState: StateFlow<NutritionalProfilesUiState> = repository.getAllAsFlow()
        .map { profiles -> NutritionalProfilesUiState.Success(profiles) as NutritionalProfilesUiState }
        .onStart { emit(NutritionalProfilesUiState.Loading) }
        .catch { error ->
            Napier.e("Failed to load nutritional profiles", error)
            emit(NutritionalProfilesUiState.Error(error.message ?: "Unknown error"))
        }
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NutritionalProfilesUiState.Loading
        )

    fun deleteProfile(profileId: Long) {
        screenModelScope.launch {
            try {
                repository.delete(profileId)
            } catch (error: Exception) {
                Napier.e("Failed to delete nutritional profile $profileId", error)
                // We could emit a side effect for the error, but for simplicity let's rely on Flow's catch
            }
        }
    }
}

sealed class NutritionalProfilesUiState {
    object Loading : NutritionalProfilesUiState()
    data class Success(val profiles: List<NutritionalProfile>) : NutritionalProfilesUiState()
    data class Error(val message: String) : NutritionalProfilesUiState()
}
