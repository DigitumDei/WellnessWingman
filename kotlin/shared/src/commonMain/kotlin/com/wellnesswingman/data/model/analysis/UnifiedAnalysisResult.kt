package com.wellnesswingman.data.model.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Unified analysis result containing all entry type analyses.
 * The LLM returns this structure with only one analysis populated based on detected type.
 */
@Serializable
data class UnifiedAnalysisResult(
    @SerialName("schemaVersion")
    val schemaVersion: String = "1.0",

    /**
     * Detected entry type: "Meal", "Exercise", "Sleep", or "Other"
     */
    @SerialName("entryType")
    val entryType: String? = null,

    /**
     * Overall confidence in the analysis (0.0 to 1.0)
     */
    @SerialName("confidence")
    val confidence: Double = 0.0,

    /**
     * Meal analysis data (null if not a meal entry)
     */
    @SerialName("mealAnalysis")
    val mealAnalysis: MealAnalysisResult? = null,

    /**
     * Exercise analysis data (null if not an exercise entry)
     */
    @SerialName("exerciseAnalysis")
    val exerciseAnalysis: ExerciseAnalysisResult? = null,

    /**
     * Sleep analysis data (null if not a sleep entry)
     */
    @SerialName("sleepAnalysis")
    val sleepAnalysis: SleepAnalysisResult? = null,

    /**
     * Other analysis data (null if not an other entry)
     */
    @SerialName("otherAnalysis")
    val otherAnalysis: OtherAnalysisResult? = null,

    /**
     * Global warnings about the analysis
     */
    @SerialName("warnings")
    val warnings: List<String> = emptyList()
)

/**
 * Other/miscellaneous analysis result.
 */
@Serializable
data class OtherAnalysisResult(
    @SerialName("summary")
    val summary: String? = null,

    @SerialName("tags")
    val tags: List<String> = emptyList(),

    @SerialName("recommendations")
    val recommendations: List<String> = emptyList()
)
