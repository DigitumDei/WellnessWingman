package com.wellnesswingman.data.model.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured JSON schema for meal analysis results from the LLM.
 * This format ensures reliable parsing and enables versioning.
 */
@Serializable
data class MealAnalysisResult(
    /**
     * Schema version for evolution tracking and backward compatibility.
     * Current version: "1.0"
     */
    @SerialName("schemaVersion")
    val schemaVersion: String = "1.0",

    /**
     * List of detected food items in the meal.
     */
    @SerialName("foodItems")
    val foodItems: List<FoodItem> = emptyList(),

    /**
     * Estimated nutritional information for the entire meal.
     */
    @SerialName("nutrition")
    val nutrition: NutritionEstimate? = null,

    /**
     * Overall health assessment and recommendations.
     */
    @SerialName("healthInsights")
    val healthInsights: HealthInsights? = null,

    /**
     * Confidence level of the analysis (0.0 to 1.0).
     */
    @SerialName("confidence")
    @Serializable(with = LenientConfidenceSerializer::class)
    val confidence: Double = 0.0,

    /**
     * Any warnings or errors encountered during analysis.
     */
    @SerialName("warnings")
    val warnings: List<String> = emptyList()
)

@Serializable
data class FoodItem(
    /**
     * Name of the food item.
     */
    @SerialName("name")
    val name: String = "",

    /**
     * Estimated portion size (e.g., "1 cup", "150g", "medium").
     */
    @SerialName("portionSize")
    val portionSize: String? = null,

    /**
     * Estimated calories for this item.
     */
    @SerialName("calories")
    @Serializable(with = LenientNullableDoubleSerializer::class)
    val calories: Double? = null,

    /**
     * Confidence in the detection of this food item (0.0 to 1.0).
     */
    @SerialName("confidence")
    @Serializable(with = LenientConfidenceSerializer::class)
    val confidence: Double = 0.0,

    /**
     * Whether the item was matched to a stored exact nutritional profile.
     */
    @SerialName("nutritionSource")
    val nutritionSource: String? = null,

    /**
     * Name of the saved nutritional profile used for exact matching.
     */
    @SerialName("matchedProfileName")
    val matchedProfileName: String? = null
)

/**
 * Estimated nutrition.
 *
 * Every quantity is read leniently, because models sometimes answer with the unit attached
 * ("300 kcal", "24g"). Strict parsing would abort the entire analysis over one such field,
 * losing everything else it got right. See [LenientNullableDoubleSerializer].
 */
@Serializable
data class NutritionEstimate(
    /**
     * Total estimated calories for the meal.
     */
    @SerialName("totalCalories")
    @Serializable(with = LenientNullableDoubleSerializer::class)
    val totalCalories: Double? = null,

    /**
     * Protein in grams.
     */
    @SerialName("protein")
    @Serializable(with = LenientNullableDoubleSerializer::class)
    val protein: Double? = null,

    /**
     * Carbohydrates in grams.
     */
    @SerialName("carbohydrates")
    @Serializable(with = LenientNullableDoubleSerializer::class)
    val carbohydrates: Double? = null,

    /**
     * Fat in grams.
     */
    @SerialName("fat")
    @Serializable(with = LenientNullableDoubleSerializer::class)
    val fat: Double? = null,

    /**
     * Fiber in grams.
     */
    @SerialName("fiber")
    @Serializable(with = LenientNullableDoubleSerializer::class)
    val fiber: Double? = null,

    /**
     * Sugar in grams.
     */
    @SerialName("sugar")
    @Serializable(with = LenientNullableDoubleSerializer::class)
    val sugar: Double? = null,

    /**
     * Sodium in milligrams.
     */
    @SerialName("sodium")
    @Serializable(with = LenientNullableDoubleSerializer::class)
    val sodium: Double? = null,

    /**
     * Whether the nutrition values are exact from a saved profile or estimated by vision.
     */
    @SerialName("source")
    val source: String? = null,

    /**
     * Saved nutritional profile names used to supply exact nutrition values.
     */
    @SerialName("matchedProfiles")
    val matchedProfiles: List<String> = emptyList()
)

@Serializable
data class HealthInsights(
    /**
     * Overall health score (0-10, where 10 is healthiest).
     *
     * Read with the nullable-double serializer rather than the confidence one: this runs 0-10,
     * so clamping it to 0-1 would silently flatten every score above 1.
     */
    @SerialName("healthScore")
    @Serializable(with = LenientNullableDoubleSerializer::class)
    val healthScore: Double? = null,

    /**
     * Brief summary of the meal's health characteristics.
     */
    @SerialName("summary")
    val summary: String? = null,

    /**
     * Positive aspects of the meal.
     */
    @SerialName("positives")
    val positives: List<String> = emptyList(),

    /**
     * Areas for improvement.
     */
    @SerialName("improvements")
    val improvements: List<String> = emptyList(),

    /**
     * Specific recommendations for healthier alternatives or additions.
     */
    @SerialName("recommendations")
    val recommendations: List<String> = emptyList()
)
