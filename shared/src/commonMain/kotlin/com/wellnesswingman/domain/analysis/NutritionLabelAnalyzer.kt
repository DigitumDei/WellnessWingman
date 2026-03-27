package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.domain.llm.LlmClientFactory
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NutritionLabelAnalyzer(
    private val llmClientFactory: LlmClientFactory
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun analyzeLabelImage(
        imageBytes: ByteArray,
        sourceImagePath: String? = null
    ): NutritionLabelExtraction {
        val llmClient = llmClientFactory.createForCurrentProvider()
        val response = llmClient.analyzeImage(
            imageBytes = imageBytes,
            prompt = buildPrompt(),
            jsonSchema = null
        )

        val parsed = json.decodeFromString<NutritionLabelExtraction>(response.content)
        Napier.d("Nutrition label extracted with confidence ${parsed.confidence}")
        return parsed.copy(
            rawJson = response.content,
            sourceImagePath = sourceImagePath
        )
    }

    fun toProfile(
        extraction: NutritionLabelExtraction,
        primaryName: String,
        aliases: List<String>
    ): NutritionalProfile {
        val now = Clock.System.now()
        return NutritionalProfile(
            externalId = "nutrition-profile-${now.toEpochMilliseconds()}",
            primaryName = primaryName.trim(),
            aliases = aliases.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            servingSize = extraction.servingSize,
            calories = extraction.nutrition.totalCalories,
            protein = extraction.nutrition.protein,
            carbohydrates = extraction.nutrition.carbohydrates,
            fat = extraction.nutrition.fat,
            fiber = extraction.nutrition.fiber,
            sugar = extraction.nutrition.sugar,
            sodium = extraction.nutrition.sodium,
            saturatedFat = extraction.nutrition.saturatedFat,
            transFat = extraction.nutrition.transFat,
            cholesterol = extraction.nutrition.cholesterol,
            rawJson = extraction.rawJson ?: json.encodeToString(extraction),
            sourceImagePath = extraction.sourceImagePath,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun buildPrompt(): String = """
You are extracting nutrition facts from a photographed food packaging nutrition panel.
Return only valid JSON.

Read the visible serving size and nutrient values. Preserve units semantically:
- calories in kcal
- protein/carbohydrates/fat/fiber/sugar/saturatedFat/transFat in grams
- sodium/cholesterol in milligrams

If a field is not visible, use null.

Required JSON shape:
{
  "servingSize": "string or null",
  "nutrition": {
    "totalCalories": number or null,
    "protein": number or null,
    "carbohydrates": number or null,
    "fat": number or null,
    "fiber": number or null,
    "sugar": number or null,
    "sodium": number or null,
    "saturatedFat": number or null,
    "transFat": number or null,
    "cholesterol": number or null
  },
  "confidence": number,
  "warnings": ["string"]
}
""".trimIndent()
}

@Serializable
data class NutritionLabelExtraction(
    @SerialName("servingSize")
    val servingSize: String? = null,
    @SerialName("nutrition")
    val nutrition: ExtractedNutrition = ExtractedNutrition(),
    @SerialName("confidence")
    val confidence: Double = 0.0,
    @SerialName("warnings")
    val warnings: List<String> = emptyList(),
    val rawJson: String? = null,
    val sourceImagePath: String? = null
)

@Serializable
data class ExtractedNutrition(
    @SerialName("totalCalories")
    val totalCalories: Double? = null,
    @SerialName("protein")
    val protein: Double? = null,
    @SerialName("carbohydrates")
    val carbohydrates: Double? = null,
    @SerialName("fat")
    val fat: Double? = null,
    @SerialName("fiber")
    val fiber: Double? = null,
    @SerialName("sugar")
    val sugar: Double? = null,
    @SerialName("sodium")
    val sodium: Double? = null,
    @SerialName("saturatedFat")
    val saturatedFat: Double? = null,
    @SerialName("transFat")
    val transFat: Double? = null,
    @SerialName("cholesterol")
    val cholesterol: Double? = null
)
