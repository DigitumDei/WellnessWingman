package com.wellnesswingman.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Represents a daily summary of health entries.
 */
@Serializable
data class DailySummary(
    val summaryId: Long = 0,
    val externalId: String? = null,
    val summaryDate: LocalDate,
    val highlights: String = "",
    val recommendations: String = ""
)

/**
 * Result of generating a daily summary.
 */
sealed class DailySummaryResult {
    data class Success(
        val summary: DailySummary,
        val payload: DailySummaryPayload
    ) : DailySummaryResult()

    object NoEntries : DailySummaryResult()

    data class Error(val message: String) : DailySummaryResult()
}

/**
 * Payload structure for daily summary responses.
 */
@Serializable
data class DailySummaryPayload(
    val schemaVersion: String = "1.0",
    val date: String,
    val summary: String,
    val highlights: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val nutritionTotals: NutritionTotals? = null,
    val mealCount: Int = 0,
    val exerciseCount: Int = 0,
    val sleepHours: Double? = null
)

/**
 * Aggregated nutrition totals for a day.
 */
@Serializable
data class NutritionTotals(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodium: Double = 0.0
)
