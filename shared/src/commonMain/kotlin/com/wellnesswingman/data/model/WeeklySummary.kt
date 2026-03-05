package com.wellnesswingman.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a weekly summary of health entries.
 */
@Serializable
data class WeeklySummary(
    val summaryId: Long = 0,
    val weekStartDate: LocalDate,
    val highlights: String = "",
    val recommendations: String = "",
    val mealCount: Int = 0,
    val exerciseCount: Int = 0,
    val sleepCount: Int = 0,
    val otherCount: Int = 0,
    val totalEntries: Int = 0,
    /**
     * Timestamp when the summary was generated. Used for outdated detection.
     */
    val generatedAt: Instant? = null,
    val userComments: String? = null,
    val payloadJson: String? = null
)

/**
 * Result of generating a weekly summary.
 */
sealed class WeeklySummaryResult {
    data class Success(
        val summary: WeeklySummary,
        val highlightsList: List<String>,
        val recommendationsList: List<String>
    ) : WeeklySummaryResult()

    object NoEntries : WeeklySummaryResult()

    data class Error(val message: String) : WeeklySummaryResult()
}

/**
 * Weight change data for a week.
 */
@Serializable
data class WeightChangeSummary(
    @SerialName("start")
    val start: Double? = null,
    @SerialName("end")
    val end: Double? = null,
    @SerialName("unit")
    val unit: String = "kg"
)

/**
 * Payload structure for weekly summary LLM responses.
 */
@Serializable
data class WeeklySummaryPayload(
    @SerialName("schemaVersion")
    val schemaVersion: String = "1.1",
    @SerialName("weekStartDate")
    val weekStartDate: String,
    @SerialName("highlights")
    val highlights: List<String> = emptyList(),
    @SerialName("recommendations")
    val recommendations: List<String> = emptyList(),
    @SerialName("mealCount")
    val mealCount: Int = 0,
    @SerialName("exerciseCount")
    val exerciseCount: Int = 0,
    @SerialName("sleepCount")
    val sleepCount: Int = 0,
    @SerialName("otherCount")
    val otherCount: Int = 0,
    @SerialName("totalEntries")
    val totalEntries: Int = 0,
    @SerialName("nutritionAverages")
    val nutritionAverages: NutritionTotals? = null,
    @SerialName("nutritionTrend")
    val nutritionTrend: String? = null,
    @SerialName("weightChange")
    val weightChange: WeightChangeSummary? = null,
    @SerialName("balanceSummary")
    val balanceSummary: String? = null
)
