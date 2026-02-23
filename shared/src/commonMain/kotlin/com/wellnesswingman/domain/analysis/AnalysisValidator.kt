package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.analysis.MealAnalysisResult
import com.wellnesswingman.data.model.analysis.UnifiedAnalysisResult
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json

/**
 * Result of analysis validation.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Validates analysis results against expected schemas and provides quality feedback.
 */
class AnalysisValidator {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Validates a meal analysis JSON string.
     */
    fun validateMealAnalysis(jsonString: String, schemaVersion: String = "1.0"): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val analysis = try {
            json.decodeFromString<MealAnalysisResult>(jsonString)
        } catch (e: Exception) {
            errors.add("Failed to parse meal analysis JSON: ${e.message}")
            return ValidationResult(isValid = false, errors = errors)
        }

        // Check schema version
        if (analysis.schemaVersion != schemaVersion) {
            warnings.add("Schema version mismatch: expected $schemaVersion, got ${analysis.schemaVersion}")
        }

        // Check required fields
        if (analysis.foodItems.isEmpty()) {
            warnings.add("No food items detected in meal analysis")
        }

        // Check nutrition data
        val nutrition = analysis.nutrition
        if (nutrition == null) {
            warnings.add("No nutrition data provided")
        } else {
            if (nutrition.totalCalories == null || nutrition.totalCalories <= 0) {
                warnings.add("Total calories missing or invalid")
            }
            if (nutrition.protein == null) {
                warnings.add("Protein value missing")
            }
            if (nutrition.carbohydrates == null) {
                warnings.add("Carbohydrates value missing")
            }
            if (nutrition.fat == null) {
                warnings.add("Fat value missing")
            }
        }

        // Check confidence
        if (analysis.confidence < 0.5) {
            warnings.add("Low confidence analysis (${analysis.confidence})")
        }

        // Check health insights
        if (analysis.healthInsights == null) {
            warnings.add("No health insights provided")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Validates a unified analysis result.
     */
    fun validateUnifiedAnalysis(jsonString: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val unified = try {
            json.decodeFromString<UnifiedAnalysisResult>(jsonString)
        } catch (e: Exception) {
            errors.add("Failed to parse unified analysis JSON: ${e.message}")
            return ValidationResult(isValid = false, errors = errors)
        }

        // Check entry type is provided
        if (unified.entryType.isNullOrBlank()) {
            errors.add("Entry type not detected")
            return ValidationResult(isValid = false, errors = errors, warnings = warnings)
        }

        // Validate based on entry type
        when (unified.entryType.lowercase()) {
            "meal" -> {
                if (unified.mealAnalysis == null) {
                    errors.add("Meal entry detected but mealAnalysis is null")
                }
            }
            "exercise" -> {
                if (unified.exerciseAnalysis == null) {
                    errors.add("Exercise entry detected but exerciseAnalysis is null")
                }
            }
            "sleep" -> {
                if (unified.sleepAnalysis == null) {
                    errors.add("Sleep entry detected but sleepAnalysis is null")
                }
            }
            "other" -> {
                if (unified.otherAnalysis == null) {
                    errors.add("Other entry detected but otherAnalysis is null")
                }
            }
            else -> {
                warnings.add("Unknown entry type: ${unified.entryType}")
            }
        }

        // Check confidence
        if (unified.confidence < 0.5) {
            warnings.add("Low overall confidence (${unified.confidence})")
        }

        // Check for warnings from the LLM
        if (unified.warnings.isNotEmpty()) {
            warnings.addAll(unified.warnings.map { "LLM warning: $it" })
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Logs validation results.
     */
    fun logValidation(entryId: Long, result: ValidationResult) {
        if (!result.isValid) {
            Napier.w("Analysis validation failed for entry $entryId: ${result.errors.joinToString("; ")}")
        } else if (result.warnings.isNotEmpty()) {
            Napier.i("Analysis validation warnings for entry $entryId: ${result.warnings.joinToString("; ")}")
        }
    }
}
