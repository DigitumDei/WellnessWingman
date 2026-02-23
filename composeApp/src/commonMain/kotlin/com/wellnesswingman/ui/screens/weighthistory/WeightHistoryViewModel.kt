package com.wellnesswingman.ui.screens.weighthistory

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.WeightRecord
import com.wellnesswingman.data.model.WeightSource
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class WeightHistoryViewModel(
    private val weightHistoryRepository: WeightHistoryRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow(WeightHistoryUiState())
    val uiState: StateFlow<WeightHistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        screenModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val records = weightHistoryRepository.getAllWeightRecords()
                _uiState.update { it.copy(records = records, isLoading = false) }
            } catch (e: Exception) {
                Napier.e("Failed to load weight history", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun logWeight(value: Double, unit: String) {
        screenModelScope.launch {
            try {
                weightHistoryRepository.addWeightRecord(
                    WeightRecord(
                        recordedAt = Clock.System.now(),
                        weightValue = value,
                        weightUnit = unit,
                        source = WeightSource.MANUAL.value
                    )
                )
                appSettingsRepository.setCurrentWeight(value)
                appSettingsRepository.setWeightUnit(unit)
                dismissLogDialog()
                loadHistory()
            } catch (e: Exception) {
                Napier.e("Failed to log weight", e)
            }
        }
    }

    fun deleteWeightRecord(recordId: Long) {
        screenModelScope.launch {
            try {
                weightHistoryRepository.deleteWeightRecord(recordId)
                loadHistory()
            } catch (e: Exception) {
                Napier.e("Failed to delete weight record $recordId", e)
            }
        }
    }

    fun showLogDialog() {
        _uiState.update { it.copy(showLogDialog = true) }
    }

    fun dismissLogDialog() {
        _uiState.update { it.copy(showLogDialog = false, logWeightValue = "") }
    }

    fun updateLogWeightValue(value: String) {
        _uiState.update { it.copy(logWeightValue = value) }
    }

    fun updateLogWeightUnit(unit: String) {
        _uiState.update { it.copy(logWeightUnit = unit) }
    }
}

data class WeightHistoryUiState(
    val records: List<WeightRecord> = emptyList(),
    val isLoading: Boolean = false,
    val showLogDialog: Boolean = false,
    val logWeightValue: String = "",
    val logWeightUnit: String = "kg"
)
