package com.wellnesswingman.ui.screens.nutrition

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.repository.NutritionalProfileRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NutritionalProfilesViewModel(
    private val repository: NutritionalProfileRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow<NutritionalProfilesUiState>(NutritionalProfilesUiState.Loading)
    val uiState: StateFlow<NutritionalProfilesUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    fun loadProfiles() {
        screenModelScope.launch {
            try {
                _uiState.value = NutritionalProfilesUiState.Loading
                _uiState.value = NutritionalProfilesUiState.Success(repository.getAll())
            } catch (error: Exception) {
                Napier.e("Failed to load nutritional profiles", error)
                _uiState.value = NutritionalProfilesUiState.Error(error.message ?: "Unknown error")
            }
        }
    }

    fun deleteProfile(profileId: Long) {
        screenModelScope.launch {
            try {
                repository.delete(profileId)
                loadProfiles()
            } catch (error: Exception) {
                Napier.e("Failed to delete nutritional profile $profileId", error)
                _uiState.value = NutritionalProfilesUiState.Error(error.message ?: "Unknown error")
            }
        }
    }
}

sealed class NutritionalProfilesUiState {
    object Loading : NutritionalProfilesUiState()
    data class Success(val profiles: List<NutritionalProfile>) : NutritionalProfilesUiState()
    data class Error(val message: String) : NutritionalProfilesUiState()
}
