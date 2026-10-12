package com.wellnesswingman.domain.report

import com.wellnesswingman.data.model.NutritionTotals
import com.wellnesswingman.data.model.NutritionalBalance
import com.wellnesswingman.data.model.analysis.MentionedFood
import com.wellnesswingman.data.model.analysis.CheckInFactor
import com.wellnesswingman.data.model.analysis.FoodItem
import com.wellnesswingman.data.model.analysis.NutritionEstimate
import com.wellnesswingman.data.model.analysis.HealthInsights
import com.wellnesswingman.data.model.analysis.ExerciseMetrics
import com.wellnesswingman.data.model.analysis.SleepAnalysisResult
import com.wellnesswingman.data.model.polar.PolarDailyActivity
import com.wellnesswingman.data.model.polar.PolarSleepResult
import com.wellnesswingman.data.model.polar.PolarTrainingSession
import com.wellnesswingman.data.model.polar.PolarNightlyRecharge
import com.wellnesswingman.domain.common.DateRange
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The report preset, which only initializes the editable [HealthReportSection] choices.
 */
enum class HealthReportPreset {
    FOOD_DIARY,
    COMPLETE_HEALTH_RECORD,
    CUSTOM;

    /**
     * The sections a preset initially selects. CUSTOM has none by design: the
     * user chooses explicitly. Presets never lock the user out of changing a
     * toggle afterwards.
     */
    fun defaultSections(): Set<HealthReportSection> = when (this) {
        FOOD_DIARY -> setOf(
            HealthReportSection.MEALS_AND_NUTRITION,
            HealthReportSection.CHECK_INS,
            HealthReportSection.DAILY_AND_WEEKLY_SUMMARIES
        )
        COMPLETE_HEALTH_RECORD -> HealthReportSection.entries.toSet()
        CUSTOM -> emptySet()
    }
}

/**
 * A user-selectable report section. Selection gates both data gathering and
 * rendering: an unselected section is never queried and never appears.
 */
enum class HealthReportSection {
    PROFILE_AND_GOALS,
    MEALS_AND_NUTRITION,
    EXERCISE_AND_ACTIVITY,
    SLEEP_AND_RECOVERY,
    CHECK_INS,
    WEIGHT_HISTORY,
    POLAR_METRICS,
    DAILY_AND_WEEKLY_SUMMARIES
}

/**
 * The immutable user intent for one report. [timeZone] is captured when the
 * preview is created and is what every repository adapter and the builder use
 * to interpret the inclusive day range.
 */
data class HealthReportRequest(
    val range: DateRange,
    val timeZone: TimeZone,
    val preset: HealthReportPreset,
    val selectedSections: Set<HealthReportSection>
) {
    init {
        require(selectedSections.isNotEmpty()) {
            "At least one report section must be selected"
        }
    }

    /** Sections to query and render; never empty by construction. */
    val sections: Set<HealthReportSection>
        get() = selectedSections
}

/**
 * Provenance of a value shown in the report. Everything rendered must carry
 * one, so a clinician can tell a user's own words from an estimate.
 */
enum class ReportProvenance {
    USER_ENTERED,
    AI_ESTIMATED,
    EXACT_PROFILE,
    POLAR_MEASURED
}

/**
 * Immutable, presentation-ready snapshot of the selected local data.
 *
 * This is the single object both the preview and the final document are
 * derived from: the preview is built once, shown, and the same instance feeds
 * the builder after authorization. Only typed, redacted fields exist here —
 * raw JSON, internal identifiers, paths, credentials, and provider metadata
 * never survive the gatherer.
 */
data class HealthReportData(
    val start: LocalDate,
    val end: LocalDate,
    val timeZoneId: String,
    val profile: ReportProfile? = null,
    val meals: List<ReportMealEntry> = emptyList(),
    val exercise: List<ReportExerciseEntry> = emptyList(),
    val sleep: List<ReportSleepEntry> = emptyList(),
    val checkIns: List<ReportCheckIn> = emptyList(),
    val weightRecords: List<ReportWeightRecord> = emptyList(),
    val dailySummaries: List<ReportDailySummary> = emptyList(),
    val weeklySummaries: List<ReportWeeklySummary> = emptyList(),
    val polarDays: List<ReportPolarDay> = emptyList()
) {
    val isEmpty: Boolean
        get() = profile == null &&
            meals.isEmpty() && exercise.isEmpty() && sleep.isEmpty() &&
            checkIns.isEmpty() && weightRecords.isEmpty() &&
            dailySummaries.isEmpty() && weeklySummaries.isEmpty() && polarDays.isEmpty()
}

data class ReportProfile(
    val height: Double? = null,
    val heightUnit: String? = null,
    val sex: String? = null,
    val dateOfBirth: String? = null,
    val activityLevel: String? = null,
    val currentWeight: Double? = null,
    val weightUnit: String? = null,
    val goalsAndPreferences: String? = null
) {
    val hasContent: Boolean
        get() = height != null || sex != null || dateOfBirth != null ||
            activityLevel != null || currentWeight != null ||
            !goalsAndPreferences.isNullOrBlank()
}

data class ReportMealEntry(
    val date: LocalDate,
    val localTime: LocalDateTime,
    val userNotes: String? = null,
    val foodItems: List<ReportFoodItem> = emptyList(),
    val nutrition: ReportNutrition? = null,
    val healthInsights: ReportHealthInsights? = null
)

data class ReportFoodItem(
    val name: String,
    val portionSize: String? = null,
    val calories: Double? = null,
    val provenance: ReportProvenance
)

data class ReportNutrition(
    val totalCalories: Double? = null,
    val protein: Double? = null,
    val carbohydrates: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val sodium: Double? = null,
    val provenance: ReportProvenance = ReportProvenance.AI_ESTIMATED
) {
    /** True when at least one measurable value exists; avoids rendering "Nutrition: ()". */
    val hasValues: Boolean
        get() = listOf(totalCalories, protein, carbohydrates, fat, fiber, sugar, sodium)
            .any { it != null }
}

data class ReportHealthInsights(
    val healthScore: Double? = null,
    val summary: String? = null,
    val positives: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)

data class ReportExerciseEntry(
    val date: LocalDate,
    val localTime: LocalDateTime,
    val userNotes: String? = null,
    val activityType: String? = null,
    val metrics: ReportExerciseMetrics? = null,
    val insights: ReportExerciseInsights? = null
)

data class ReportExerciseMetrics(
    val distance: Double? = null,
    val distanceUnit: String? = null,
    val durationMinutes: Double? = null,
    val averagePace: String? = null,
    val calories: Double? = null,
    val averageHeartRate: Double? = null,
    val maxHeartRate: Double? = null,
    val steps: Double? = null
)

data class ReportExerciseInsights(
    val summary: String? = null,
    val positives: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)

data class ReportSleepEntry(
    val date: LocalDate,
    val localTime: LocalDateTime,
    val userNotes: String? = null,
    val analysis: SleepAnalysisResult? = null
)

data class ReportCheckIn(
    val date: LocalDate,
    val slot: String,
    val responseText: String,
    val inputSource: String,
    val mentionedFood: List<MentionedFood> = emptyList(),
    val goodFactors: List<CheckInFactor> = emptyList(),
    val badFactors: List<CheckInFactor> = emptyList()
)

data class ReportWeightRecord(
    val date: LocalDate,
    val recordedAt: LocalDateTime,
    val value: Double,
    val unit: String,
    val provenance: ReportProvenance
)

data class ReportDailySummary(
    val date: LocalDate,
    val highlights: String = "",
    val recommendations: String = "",
    val totals: ReportNutritionTotals? = null,
    val balance: NutritionalBalance? = null,
    val userComments: String? = null
)

data class ReportNutritionTotals(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodium: Double = 0.0,
    val mentionedCalories: Double = 0.0,
    val mentionedItemCount: Int = 0
)

data class ReportWeeklySummary(
    val weekStartDate: LocalDate,
    val highlights: String = "",
    val recommendations: String = "",
    val mealCount: Int = 0,
    val exerciseCount: Int = 0,
    val sleepCount: Int = 0,
    val otherCount: Int = 0,
    val totalEntries: Int = 0,
    val userComments: String? = null
)

/**
 * One calendar day of Polar-measured data. Only populated families appear:
 * an empty optional means "not present", never "measured zero".
 */
data class ReportPolarDay(
    val date: LocalDate,
    val activity: PolarDailyActivity? = null,
    val sleep: PolarSleepResult? = null,
    val trainingSessions: List<PolarTrainingSession> = emptyList(),
    val nightlyRecharge: PolarNightlyRecharge? = null
)

/**
 * Record counts derived purely from a [HealthReportData] instance.
 */
data class HealthReportPreview(
    val profileIncluded: Boolean,
    val mealCount: Int,
    val exerciseCount: Int,
    val sleepCount: Int,
    val checkInCount: Int,
    val weightCount: Int,
    val dailySummaryCount: Int,
    val weeklySummaryCount: Int,
    val polarDayCount: Int
) {
    val totalEntries: Int
        get() = mealCount + exerciseCount + sleepCount +
            checkInCount + weightCount + polarDayCount
}

/**
 * Builds the preview counts from the immutable snapshot only, so the preview
 * and the final document always agree about what will be disclosed.
 */
fun HealthReportData.toPreview(): HealthReportPreview = HealthReportPreview(
    profileIncluded = profile != null,
    mealCount = meals.size,
    exerciseCount = exercise.size,
    sleepCount = sleep.size,
    checkInCount = checkIns.size,
    weightCount = weightRecords.size,
    dailySummaryCount = dailySummaries.size,
    weeklySummaryCount = weeklySummaries.size,
    polarDayCount = polarDays.size
)

// --- Conversions of stored typed payloads into report types ---

internal fun FoodItem.toReportFoodItem(): ReportFoodItem = ReportFoodItem(
    name = name,
    portionSize = portionSize,
    calories = calories,
    provenance = if (
        nutritionSource.equals("exact", ignoreCase = true) ||
        matchedProfileName != null
    ) {
        ReportProvenance.EXACT_PROFILE
    } else {
        ReportProvenance.AI_ESTIMATED
    }
)

internal fun NutritionEstimate?.toReportNutrition(): ReportNutrition? {
    if (this == null) return null
    return ReportNutrition(
        totalCalories = totalCalories,
        protein = protein,
        carbohydrates = carbohydrates,
        fat = fat,
        fiber = fiber,
        sugar = sugar,
        sodium = sodium,
        provenance = if (source.equals("exact", ignoreCase = true) || matchedProfiles.isNotEmpty()) {
            ReportProvenance.EXACT_PROFILE
        } else {
            ReportProvenance.AI_ESTIMATED
        }
    )
}

internal fun HealthInsights?.toReportHealthInsights(): ReportHealthInsights? {
    if (this == null) return null
    return ReportHealthInsights(
        healthScore = healthScore,
        summary = summary,
        positives = positives,
        improvements = improvements,
        recommendations = recommendations
    )
}

internal fun ExerciseMetrics?.toReportExerciseMetrics(): ReportExerciseMetrics? {
    if (this == null) return null
    return ReportExerciseMetrics(
        distance = distance,
        distanceUnit = distanceUnit,
        durationMinutes = durationMinutes,
        averagePace = averagePace,
        calories = calories,
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate,
        steps = steps
    )
}

internal fun NutritionTotals?.toReportNutritionTotals(): ReportNutritionTotals? {
    if (this == null) return null
    return ReportNutritionTotals(
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        sugar = sugar,
        sodium = sodium,
        mentionedCalories = mentionedCalories,
        mentionedItemCount = mentionedItemCount
    )
}

internal fun com.wellnesswingman.data.model.analysis.ExerciseInsights?.toReportExerciseInsights(): ReportExerciseInsights? {
    if (this == null) return null
    return ReportExerciseInsights(
        summary = summary,
        positives = positives,
        recommendations = recommendations
    )
}

internal fun Instant.toLocalDateTimeIn(zone: TimeZone): LocalDateTime = toLocalDateTime(zone)
