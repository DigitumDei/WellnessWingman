package com.wellnesswingman.domain.report

import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.DailyCheckIn
import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeeklySummary
import com.wellnesswingman.data.model.WeightRecord
import com.wellnesswingman.data.model.WeightSource
import com.wellnesswingman.data.model.analysis.UnifiedAnalysisResult
import com.wellnesswingman.data.model.polar.StoredPolarActivity
import com.wellnesswingman.data.model.polar.StoredPolarNightlyRecharge
import com.wellnesswingman.data.model.polar.StoredPolarSleepResult
import com.wellnesswingman.data.model.polar.StoredPolarTrainingSession
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.CheckInAnalysisRepository
import com.wellnesswingman.data.repository.DailyCheckInRepository
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.PolarSyncRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeeklySummaryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json

/**
 * Gathers one immutable [HealthReportData] snapshot for a
 * [HealthReportRequest] from local repositories.
 *
 * Exactly one query pass per selected section (entries plus the PR 1 batch
 * analysis lookup, daily/weekly summaries, weight, check-ins plus their
 * analyses, the four Polar families, profile settings) — no per-row queries.
 * The preview is derived from the returned snapshot, and the same snapshot is
 * what the builder consumes later; there is no second read.
 *
 * Every repository range convention is normalized through [DateRange]: local
 * date repositories get the inclusive bounds, timestamp repositories get the
 * half-open instant bounds in the captured time zone, and Polar gets
 * (start, endExclusive) local dates.
 *
 * Typed payloads (analysis JSON) are parsed defensively: a malformed or
 * failed analysis never throws the gatherer and never leaks raw JSON — the
 * canonical user-entered data (notes, times) is preserved and the derived
 * content is omitted.
 */
class HealthReportGatherer(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val entryAnalysisRepository: EntryAnalysisRepository,
    private val dailySummaryRepository: DailySummaryRepository,
    private val weeklySummaryRepository: WeeklySummaryRepository,
    private val weightHistoryRepository: WeightHistoryRepository,
    private val dailyCheckInRepository: DailyCheckInRepository,
    private val checkInAnalysisRepository: CheckInAnalysisRepository,
    private val polarSyncRepository: PolarSyncRepository,
    private val appSettingsRepository: AppSettingsRepository
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun gather(request: HealthReportRequest): HealthReportData {
        val zone = request.timeZone
        val range = request.range
        val sections = request.sections
        val (rangeStart, rangeEndExclusive) = range.toInstantBounds(zone)

        val profile = if (HealthReportSection.PROFILE_AND_GOALS in sections) {
            gatherProfile()
        } else {
            null
        }

        var meals: List<ReportMealEntry> = emptyList()
        var exercise: List<ReportExerciseEntry> = emptyList()
        var sleep: List<ReportSleepEntry> = emptyList()

        if (sections.any {
                it == HealthReportSection.MEALS_AND_NUTRITION ||
                    it == HealthReportSection.EXERCISE_AND_ACTIVITY ||
                    it == HealthReportSection.SLEEP_AND_RECOVERY
            }
        ) {
            val entries = trackedEntryRepository.getEntriesInRange(rangeStart, rangeEndExclusive)
            val analysesById = entryAnalysisRepository
                .getLatestAnalysesForEntries(rangeStart, rangeEndExclusive)
                .associateBy { it.entryId }

            val wantsMeals = HealthReportSection.MEALS_AND_NUTRITION in sections
            val wantsExercise = HealthReportSection.EXERCISE_AND_ACTIVITY in sections
            val wantsSleep = HealthReportSection.SLEEP_AND_RECOVERY in sections

            meals = entries
                .filter { wantsMeals && it.entryType == EntryType.MEAL }
                .map { it.toReportMealEntry(zone, analysesById[it.entryId]) }
            exercise = entries
                .filter { wantsExercise && it.entryType == EntryType.EXERCISE }
                .map { it.toReportExerciseEntry(zone, analysesById[it.entryId]) }
            sleep = entries
                .filter { wantsSleep && it.entryType == EntryType.SLEEP }
                .map { it.toReportSleepEntry(zone, analysesById[it.entryId]) }
        }

        val checkIns = if (HealthReportSection.CHECK_INS in sections) {
            gatherCheckIns(range, zone)
        } else {
            emptyList()
        }

        val weightRecords = if (HealthReportSection.WEIGHT_HISTORY in sections) {
            weightHistoryRepository
                .getWeightHistory(rangeStart, rangeEndExclusive)
                .map { it.toReportWeightRecord(zone) }
                .sortedBy { it.recordedAt }
        } else {
            emptyList()
        }

        val dailySummaries = if (HealthReportSection.DAILY_AND_WEEKLY_SUMMARIES in sections) {
            dailySummaryRepository
                .getSummariesForDateRange(range.start, range.end)
                .map { it.toReportDailySummary() }
                .sortedBy { it.date }
        } else {
            emptyList()
        }

        val weeklySummaries = if (HealthReportSection.DAILY_AND_WEEKLY_SUMMARIES in sections) {
            weeklySummaryRepository
                .getSummariesForDateRange(range.start, range.end)
                .map { it.toReportWeeklySummary() }
                .sortedBy { it.weekStartDate }
        } else {
            emptyList()
        }

        val polarDays = if (HealthReportSection.POLAR_METRICS in sections) {
            gatherPolar(range)
        } else {
            emptyList()
        }

        return HealthReportData(
            start = range.start,
            end = range.end,
            timeZoneId = zone.id,
            profile = profile,
            meals = meals,
            exercise = exercise,
            sleep = sleep,
            checkIns = checkIns,
            weightRecords = weightRecords,
            dailySummaries = dailySummaries,
            weeklySummaries = weeklySummaries,
            polarDays = polarDays
        )
    }

    /**
     * Returns the profile only when it has at least one value to show, so a
     * selected-but-empty profile never produces an empty report section.
     */
    private fun gatherProfile(): ReportProfile? {
        val unit = appSettingsRepository.getHeightUnit()
        val weightUnit = appSettingsRepository.getWeightUnit()
        val profile = ReportProfile(
            height = appSettingsRepository.getHeight(),
            heightUnit = unit,
            sex = appSettingsRepository.getSex(),
            dateOfBirth = appSettingsRepository.getDateOfBirth(),
            activityLevel = appSettingsRepository.getActivityLevel(),
            currentWeight = appSettingsRepository.getCurrentWeight(),
            weightUnit = weightUnit,
            goalsAndPreferences = appSettingsRepository.getGoalsAndPreferences()?.takeIf { it.isNotBlank() }
        )
        return profile.takeIf { it.hasContent }
    }

    private suspend fun gatherCheckIns(
        range: com.wellnesswingman.domain.common.DateRange,
        zone: TimeZone
    ): List<ReportCheckIn> {
        val checkIns = dailyCheckInRepository.getCheckInsForDateRange(range.start, range.end)
        val analyses = checkInAnalysisRepository.getAnalysesForDateRange(range.start, range.end)
            .associateBy { it.checkInDate to it.slot }

        return checkIns
            .map { checkIn -> checkIn.toReportCheckIn(analyses[checkIn.checkInDate to checkIn.slot]) }
            .sortedWith(compareBy<ReportCheckIn> { it.date }.thenBy { it.slot })
    }

    private suspend fun gatherPolar(
        range: com.wellnesswingman.domain.common.DateRange
    ): List<ReportPolarDay> {
        val activities = polarSyncRepository.getActivities(range.start, range.endExclusive)
        val sleeps = polarSyncRepository.getSleepResults(range.start, range.endExclusive)
        val trainings = polarSyncRepository.getTrainingSessions(range.start, range.endExclusive)
        val recharges = polarSyncRepository.getNightlyRecharge(range.start, range.endExclusive)

        val days = sortedMapOf<LocalDate, MutableList<Any?>>()

        activities.forEach { dayBy(it.localDate, days).add(it) }
        sleeps.forEach { dayBy(it.localDate, days).add(it) }
        trainings.forEach { dayBy(it.localDate, days).add(it) }
        recharges.forEach { dayBy(it.localDate, days).add(it) }

        return days.map { (date, items) ->
            ReportPolarDay(
                date = date,
                activity = items.filterIsInstance<StoredPolarActivity>().firstOrNull()?.data,
                sleep = items.filterIsInstance<StoredPolarSleepResult>().firstOrNull()?.data,
                trainingSessions = items.filterIsInstance<StoredPolarTrainingSession>()
                    .map { it.data }
                    .sortedBy { it.startTime },
                nightlyRecharge = items.filterIsInstance<StoredPolarNightlyRecharge>().firstOrNull()?.data
            )
        }
    }

    private fun dayBy(date: LocalDate, days: MutableMap<LocalDate, MutableList<Any?>>): MutableList<Any?> =
        days.getOrPut(date) { mutableListOf() }

    // --- Entry mapping ---

    private fun TrackedEntry.toReportMealEntry(
        zone: TimeZone,
        analysis: EntryAnalysis?
    ): ReportMealEntry {
        val local = capturedAt.toLocalDateTimeIn(zone)
        val meal = analysis?.unified()?.mealAnalysis
        return ReportMealEntry(
            date = local.date,
            localTime = local,
            userNotes = userNotes,
            foodItems = meal?.foodItems.orEmpty().map { it.toReportFoodItem() },
            nutrition = meal?.nutrition.toReportNutrition(),
            healthInsights = meal?.healthInsights.toReportHealthInsights()
        )
    }

    private fun TrackedEntry.toReportExerciseEntry(
        zone: TimeZone,
        analysis: EntryAnalysis?
    ): ReportExerciseEntry {
        val local = capturedAt.toLocalDateTimeIn(zone)
        val exerciseAnalysis = analysis?.unified()?.exerciseAnalysis
        return ReportExerciseEntry(
            date = local.date,
            localTime = local,
            userNotes = userNotes,
            activityType = exerciseAnalysis?.activityType,
            metrics = exerciseAnalysis?.metrics.toReportExerciseMetrics(),
            insights = exerciseAnalysis?.insights.toReportExerciseInsights()
        )
    }

    private fun TrackedEntry.toReportSleepEntry(
        zone: TimeZone,
        analysis: EntryAnalysis?
    ): ReportSleepEntry {
        val local = capturedAt.toLocalDateTimeIn(zone)
        return ReportSleepEntry(
            date = local.date,
            localTime = local,
            userNotes = userNotes,
            analysis = analysis?.unified()?.sleepAnalysis
        )
    }

    private fun DailyCheckIn.toReportCheckIn(analysis: CheckInAnalysis?): ReportCheckIn {
        val facets = analysis?.completedFacets
        return ReportCheckIn(
            date = checkInDate,
            slot = slot.toStorageString(),
            responseText = responseText,
            inputSource = inputSource.toStorageString(),
            mentionedFood = facets?.mentionedFood.orEmpty(),
            goodFactors = facets?.goodFactors.orEmpty(),
            badFactors = facets?.badFactors.orEmpty()
        )
    }

    private fun WeightRecord.toReportWeightRecord(zone: TimeZone): ReportWeightRecord {
        val local = recordedAt.toLocalDateTimeIn(zone)
        return ReportWeightRecord(
            date = local.date,
            recordedAt = local,
            value = weightValue,
            unit = weightUnit,
            provenance = if (WeightSource.fromString(source) == WeightSource.MANUAL) {
                ReportProvenance.USER_ENTERED
            } else {
                ReportProvenance.AI_ESTIMATED
            }
        )
    }

    private fun DailySummary.toReportDailySummary(): ReportDailySummary = ReportDailySummary(
        date = summaryDate,
        highlights = highlights,
        recommendations = recommendations,
        totals = payload?.nutritionTotals?.toReportNutritionTotals(),
        balance = payload?.balance,
        userComments = userComments
    )

    private val DailySummary.payload: com.wellnesswingman.data.model.DailySummaryPayload?
        get() = payloadJson?.let { raw ->
            runCatching { json.decodeFromString<com.wellnesswingman.data.model.DailySummaryPayload>(raw) }
                .getOrNull()
        }

    private fun WeeklySummary.toReportWeeklySummary(): ReportWeeklySummary = ReportWeeklySummary(
        weekStartDate = weekStartDate,
        highlights = highlights,
        recommendations = recommendations,
        mealCount = mealCount,
        exerciseCount = exerciseCount,
        sleepCount = sleepCount,
        otherCount = otherCount,
        totalEntries = totalEntries,
        userComments = userComments
    )

    private fun EntryAnalysis.unified(): UnifiedAnalysisResult? =
        runCatching { json.decodeFromString<UnifiedAnalysisResult>(insightsJson) }.getOrNull()
}
