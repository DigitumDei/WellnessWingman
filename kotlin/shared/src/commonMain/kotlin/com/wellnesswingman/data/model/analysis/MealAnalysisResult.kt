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
    val calories: Double? = null,

    /**
     * Confidence in the detection of this food item (0.0 to 1.0).
     */
    @SerialName("confidence")
    val confidence: Double = 0.0
)

@Serializable
data class NutritionEstimate(
    /**
     * Total estimated calories for the meal.
     */
    @SerialName("totalCalories")
    val totalCalories: Double? = null,

    /**
     * Protein in grams.
     */
    @SerialName("protein")
    val protein: Double? = null,

    /**
     * Carbohydrates in grams.
     */
    @SerialName("carbohydrates")
    val carbohydrates: Double? = null,

    /**
     * Fat in grams.
     */
    @SerialName("fat")
    val fat: Double? = null,

    /**
     * Fiber in grams.
     */
    @SerialName("fiber")
    val fiber: Double? = null,

    /**
     * Sugar in grams.
     */
    @SerialName("sugar")
    val sugar: Double? = null,

    /**
     * Sodium in milligrams.
     */
    @SerialName("sodium")
    val sodium: Double? = null
)

@Serializable
data class HealthInsights(
    /**
     * Overall health score (0-10, where 10 is healthiest).
     */
    @SerialName("healthScore")
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
