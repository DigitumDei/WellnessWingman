package com.wellnesswingman.ui.screens.nutrition

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.repository.NutritionalProfileRepository
import com.wellnesswingman.domain.analysis.ExtractedNutrition
import com.wellnesswingman.domain.analysis.NutritionLabelAnalyzing
import com.wellnesswingman.domain.analysis.NutritionLabelExtraction
import com.wellnesswingman.platform.CameraCaptureOperations
import com.wellnesswingman.platform.CaptureResult
import com.wellnesswingman.platform.FileSystemOperations
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class NutritionLabelScanViewModel(
    private val profileId: Long?,
    private val cameraService: CameraCaptureOperations,
    private val fileSystem: FileSystemOperations,
    private val analyzer: NutritionLabelAnalyzing,
    private val repository: NutritionalProfileRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow(NutritionLabelScanUiState(isLoading = profileId != null))
    val uiState: StateFlow<NutritionLabelScanUiState> = _uiState.asStateFlow()

    init {
        if (profileId != null) {
            loadExistingProfile(profileId)
        }
    }

    private fun loadExistingProfile(profileId: Long) {
        screenModelScope.launch {
            try {
                val profile = repository.getById(profileId)
                if (profile == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "This nutritional profile no longer exists."
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    profileId = profile.profileId,
                    existingExternalId = profile.externalId,
                    createdAt = profile.createdAt,
                    primaryName = profile.primaryName,
                    aliases = profile.aliases.joinToString(", "),
                    servingSize = profile.servingSize.orEmpty(),
                    nutrition = ExtractedNutrition(
                        totalCalories = profile.calories,
                        protein = profile.protein,
                        carbohydrates = profile.carbohydrates,
                        fat = profile.fat,
                        fiber = profile.fiber,
                        sugar = profile.sugar,
                        sodium = profile.sodium,
                        saturatedFat = profile.saturatedFat,
                        transFat = profile.transFat,
                        cholesterol = profile.cholesterol
                    ),
                    sourceImagePath = profile.sourceImagePath,
                    photoBytes = profile.sourceImagePath
                        ?.takeIf(fileSystem::exists)
                        ?.let { fileSystem.readBytes(it) }
                )
            } catch (error: Exception) {
                Napier.e("Failed to load nutritional profile $profileId", error)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Unknown error"
                )
            }
        }
    }

    fun captureFromCamera() = handleCapture { cameraService.capturePhoto() }

    fun pickFromGallery() = handleCapture { cameraService.pickFromGallery() ?: CaptureResult.Cancelled }

    private fun handleCapture(block: suspend () -> CaptureResult) {
        screenModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isCapturing = true, error = null)
                when (val result = block()) {
                    is CaptureResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isCapturing = false,
                            sourceImagePath = result.photoPath,
                            photoBytes = result.bytes,
                            servingSize = "",
                            nutrition = ExtractedNutrition(),
                            extractionWarnings = emptyList(),
                            rawJson = null
                        )
                    }
                    is CaptureResult.Error -> {
                        _uiState.value = _uiState.value.copy(isCapturing = false, error = result.message)
                    }
                    is CaptureResult.Cancelled -> {
                        _uiState.value = _uiState.value.copy(isCapturing = false)
                    }
                }
            } catch (error: Exception) {
                Napier.e("Capture failed for nutrition label scan", error)
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    error = error.message ?: "Unknown error"
                )
            }
        }
    }

    fun analyzeImage() {
        screenModelScope.launch {
            val state = _uiState.value
            val imageBytes = state.photoBytes ?: return@launch
            if (!analyzer.hasConfiguredApiKey()) {
                _uiState.value = state.copy(
                    error = "Missing API Key. Go to Settings to add your OpenAI or Gemini API key to extract nutrition facts."
                )
                return@launch
            }
            try {
                _uiState.value = state.copy(
                    isAnalyzing = true,
                    error = null,
                    servingSize = "",
                    nutrition = ExtractedNutrition(),
                    extractionWarnings = emptyList(),
                    rawJson = null
                )
                val extraction = analyzer.analyzeLabelImage(imageBytes, state.sourceImagePath)
                applyExtraction(extraction)
            } catch (error: Exception) {
                Napier.e("Nutrition label analysis failed", error)
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = error.message ?: "Unknown error"
                )
            }
        }
    }

    private fun applyExtraction(extraction: NutritionLabelExtraction) {
        _uiState.value = _uiState.value.copy(
            isAnalyzing = false,
            extractionWarnings = extraction.warnings,
            servingSize = extraction.servingSize.orEmpty(),
            nutrition = extraction.nutrition,
            rawJson = extraction.rawJson
        )
    }

    fun updatePrimaryName(value: String) {
        _uiState.value = _uiState.value.copy(primaryName = value)
    }

    fun updateAliases(value: String) {
        _uiState.value = _uiState.value.copy(aliases = value)
    }

    fun updateServingSize(value: String) {
        _uiState.value = _uiState.value.copy(servingSize = value)
    }

    fun updateNutrition(update: (ExtractedNutrition) -> ExtractedNutrition) {
        _uiState.value = _uiState.value.copy(nutrition = update(_uiState.value.nutrition))
    }

    fun save(onSaved: () -> Unit) {
        screenModelScope.launch {
            val state = _uiState.value
            val primaryName = state.primaryName.trim()
            if (primaryName.isBlank()) {
                _uiState.value = state.copy(error = "Primary name is required")
                return@launch
            }

            try {
                _uiState.value = state.copy(isSaving = true, error = null)
                val now = Clock.System.now()
                val profile = NutritionalProfile(
                    profileId = state.profileId ?: 0,
                    externalId = state.existingExternalId ?: "nutrition-profile-${now.toEpochMilliseconds()}",
                    primaryName = primaryName,
                    aliases = parseAliases(state.aliases),
                    servingSize = state.servingSize.trim().ifBlank { null },
                    calories = state.nutrition.totalCalories,
                    protein = state.nutrition.protein,
                    carbohydrates = state.nutrition.carbohydrates,
                    fat = state.nutrition.fat,
                    fiber = state.nutrition.fiber,
                    sugar = state.nutrition.sugar,
                    sodium = state.nutrition.sodium,
                    saturatedFat = state.nutrition.saturatedFat,
                    transFat = state.nutrition.transFat,
                    cholesterol = state.nutrition.cholesterol,
                    rawJson = state.rawJson,
                    sourceImagePath = state.sourceImagePath,
                    createdAt = state.createdAt ?: now,
                    updatedAt = now
                )

                if (profile.profileId == 0L) {
                    repository.insert(profile)
                } else {
                    repository.update(profile)
                }

                _uiState.value = _uiState.value.copy(isSaving = false, saveCompleted = true)
                onSaved()
            } catch (error: Exception) {
                Napier.e("Failed to save nutritional profile", error)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = error.message ?: "Unknown error"
                )
            }
        }
    }

    private fun parseAliases(raw: String): List<String> {
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
}

data class NutritionLabelScanUiState(
    val isLoading: Boolean = false,
    val isCapturing: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isSaving: Boolean = false,
    val saveCompleted: Boolean = false,
    val profileId: Long? = null,
    val existingExternalId: String? = null,
    val createdAt: Instant? = null,
    val sourceImagePath: String? = null,
    val photoBytes: ByteArray? = null,
    val primaryName: String = "",
    val aliases: String = "",
    val servingSize: String = "",
    val nutrition: ExtractedNutrition = ExtractedNutrition(),
    val extractionWarnings: List<String> = emptyList(),
    val rawJson: String? = null,
    val error: String? = null
)
