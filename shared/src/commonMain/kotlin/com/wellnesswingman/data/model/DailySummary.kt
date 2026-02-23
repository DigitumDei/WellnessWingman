package com.wellnesswingman.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
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
    val recommendations: String = "",
    /**
     * Timestamp when the summary was generated. Used for outdated detection.
     */
    val generatedAt: Instant? = null
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
    @SerialName("schemaVersion")
    val schemaVersion: String = "1.0",
    @SerialName("date")
    val date: String,
    @SerialName("summary")
    val summary: String,
    @SerialName("highlights")
    val highlights: List<String> = emptyList(),
    @SerialName("recommendations")
    val recommendations: List<String> = emptyList(),
    @SerialName("totals")
    val nutritionTotals: NutritionTotals? = null,
    @SerialName("balance")
    val balance: NutritionalBalance? = null,
    @SerialName("entriesIncluded")
    val entriesIncluded: List<DailySummaryEntryReference> = emptyList(),
    val mealCount: Int = 0,
    val exerciseCount: Int = 0,
    val sleepHours: Double? = null
)

/**
 * Aggregated nutrition totals for a day.
 */
@Serializable
data class NutritionTotals(
    @SerialName("calories")
    val calories: Double = 0.0,
    @SerialName("protein")
    val protein: Double = 0.0,
    @SerialName("carbohydrates")
    val carbs: Double = 0.0,
    @SerialName("fat")
    val fat: Double = 0.0,
    @SerialName("fiber")
    val fiber: Double = 0.0,
    @SerialName("sugar")
    val sugar: Double = 0.0,
    @SerialName("sodium")
    val sodium: Double = 0.0
)

/**
 * Nutritional balance metrics for daily summary.
 */
@Serializable
data class NutritionalBalance(
    /**
     * Overall balance description (e.g., "Balanced", "High in carbs")
     */
    @SerialName("overall")
    val overall: String? = null,

    /**
     * Macro balance ratio (e.g., "45C/30P/25F")
     */
    @SerialName("macroBalance")
    val macroBalance: String? = null,

    /**
     * Meal timing assessment (e.g., "Well-distributed across meals")
     */
    @SerialName("timing")
    val timing: String? = null,

    /**
     * Food variety score (e.g., "Good variety of food groups")
     */
    @SerialName("variety")
    val variety: String? = null
)

/**
 * Reference to an entry included in a daily summary.
 */
@Serializable
data class DailySummaryEntryReference(
    @SerialName("entryId")
    val entryId: Long,

    @SerialName("entryType")
    val entryType: String = "Unknown",

    @SerialName("capturedAt")
    val capturedAt: Instant,

    @SerialName("summary")
    val summary: String? = null
)
