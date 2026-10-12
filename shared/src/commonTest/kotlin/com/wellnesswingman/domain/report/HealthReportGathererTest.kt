package com.wellnesswingman.domain.report

import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.CheckInInputSource
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn
import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeightRecord
import com.wellnesswingman.data.model.WeeklySummary
import com.wellnesswingman.data.model.analysis.CheckInAnalysisStatus
import com.wellnesswingman.data.model.analysis.CheckInFacets
import com.wellnesswingman.data.model.analysis.MentionedFood
import com.wellnesswingman.data.model.analysis.NutritionEstimate
import com.wellnesswingman.data.model.polar.PolarDailyActivity
import com.wellnesswingman.data.model.polar.PolarNightlyRecharge
import com.wellnesswingman.data.model.polar.PolarSleepResult
import com.wellnesswingman.data.model.polar.PolarTrainingSession
import com.wellnesswingman.data.model.polar.StoredPolarActivity
import com.wellnesswingman.data.model.polar.StoredPolarNightlyRecharge
import com.wellnesswingman.data.model.polar.StoredPolarSleepResult
import com.wellnesswingman.data.model.polar.StoredPolarTrainingSession
import com.wellnesswingman.domain.common.DateRange
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealthReportGathererTest {

    private val zone = TimeZone.UTC
    private val range = DateRange.of(LocalDate(2024, 1, 10), LocalDate(2024, 1, 12))

    private fun request(
        preset: HealthReportPreset = HealthReportPreset.CUSTOM,
        sections: Set<HealthReportSection> = HealthReportSection.entries.toSet(),
        range: DateRange = this.range,
        zone: TimeZone = this.zone
    ) = HealthReportRequest(range = range, timeZone = zone, preset = preset, selectedSections = sections)

    private fun tracked(
        id: Long,
        type: EntryType,
        at: Instant,
        notes: String? = null
    ) = TrackedEntry(
        entryId = id,
        entryType = type,
        capturedAt = at,
        userNotes = notes,
        processingStatus = ProcessingStatus.COMPLETED
    )

    private fun analysis(entryId: Long, json: String, at: Instant = Instant.parse("2024-01-11T01:00:00Z")) =
        EntryAnalysis(analysisId = entryId, entryId = entryId, capturedAt = at, insightsJson = json)

    private fun mealJson(
        exact: Boolean = false,
        foodName: String = "Rice bowl"
    ): String {
        val foodFields = listOfNotNull(
            "\"name\": \"$foodName\"",
            "\"portionSize\": \"1 bowl\"",
            "\"calories\": 450.0",
            if (exact) "\"nutritionSource\":\"exact\",\"matchedProfileName\":\"Rice\"" else null
        ).joinToString(", ")
        val nutritionFields = listOfNotNull(
            "\"totalCalories\": 450.0",
            "\"protein\": 20.0",
            "\"carbohydrates\": 60.0",
            "\"fat\": 12.0",
            if (exact) "\"source\":\"exact\"" else null
        ).joinToString(", ")
        return """
        {
          "schemaVersion": "1.0",
          "mealAnalysis": {
            "foodItems": [
              { $foodFields }
            ],
            "nutrition": { $nutritionFields },
            "healthInsights": { "healthScore": 7.0, "summary": "Balanced meal" }
          },
          "confidence": 0.8
        }
        """.trimIndent()
    }

    private fun gathererWith(
        trackedRepo: FakeTrackedEntryRepository,
        analysisRepo: FakeEntryAnalysisRepository,
        checkInRepo: FakeDailyCheckInRepository = FakeDailyCheckInRepository(),
        checkInAnalysisRepo: FakeCheckInAnalysisRepository = FakeCheckInAnalysisRepository(),
        dailySummaries: FakeDailySummaryRepository = FakeDailySummaryRepository(),
        weeklySummaries: FakeWeeklySummaryRepository = FakeWeeklySummaryRepository(),
        weights: FakeWeightHistoryRepository = FakeWeightHistoryRepository(),
        polar: FakePolarSyncRepository = FakePolarSyncRepository(),
        settings: FakeAppSettingsRepository = FakeAppSettingsRepository()
    ) = HealthReportGatherer(
        trackedEntryRepository = trackedRepo,
        entryAnalysisRepository = analysisRepo,
        dailySummaryRepository = dailySummaries,
        weeklySummaryRepository = weeklySummaries,
        weightHistoryRepository = weights,
        dailyCheckInRepository = checkInRepo,
        checkInAnalysisRepository = checkInAnalysisRepo,
        polarSyncRepository = polar,
        appSettingsRepository = settings
    )

    @Test
    fun `only selected sections are queried`() = runTest {
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(tracked(1, EntryType.MEAL, Instant.parse("2024-01-11T08:00:00Z")))
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply { parentEntries = trackedRepo.entries }
        val checkIns = FakeDailyCheckInRepository()
        val daily = FakeDailySummaryRepository()
        val weekly = FakeWeeklySummaryRepository()
        val weights = FakeWeightHistoryRepository()
        val polar = FakePolarSyncRepository()

        val data = gathererWith(trackedRepo, analysisRepo, checkIns, dailySummaries = daily, weeklySummaries = weekly, weights = weights, polar = polar)
            .gather(request(sections = setOf(HealthReportSection.WEIGHT_HISTORY)))

        assertEquals(0, trackedRepo.rangeCalls)
        assertEquals(0, analysisRepo.batchCalls)
        assertEquals(0, checkIns.rangeCalls)
        assertEquals(0, daily.rangeCalls)
        assertEquals(0, weekly.rangeCalls)
        assertEquals(0, polar.activityCalls)
        assertEquals(1, weights.rangeCalls)
        assertTrue(data.weightRecords.isEmpty())
        assertTrue(data.meals.isEmpty())
        assertNull(data.profile)
    }

    @Test
    fun `all selected sections are each queried exactly once`() = runTest {
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(tracked(1, EntryType.MEAL, Instant.parse("2024-01-11T08:00:00Z")))
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply { parentEntries = trackedRepo.entries }
        val checkIns = FakeDailyCheckInRepository()
        val checkInAnalyses = FakeCheckInAnalysisRepository()
        val daily = FakeDailySummaryRepository()
        val weekly = FakeWeeklySummaryRepository()
        val weights = FakeWeightHistoryRepository()
        val polar = FakePolarSyncRepository()

        gathererWith(trackedRepo, analysisRepo, checkIns, checkInAnalyses, daily, weekly, weights, polar)
            .gather(request())

        assertEquals(1, trackedRepo.rangeCalls)
        assertEquals(1, analysisRepo.batchCalls)
        assertEquals(1, checkIns.rangeCalls)
        assertEquals(1, checkInAnalyses.rangeCalls)
        assertEquals(1, daily.rangeCalls)
        assertEquals(1, weekly.rangeCalls)
        assertEquals(1, weights.rangeCalls)
        assertEquals(1, polar.activityCalls)
    }

    @Test
    fun `entry range is half-open on capturedAt instants`() = runTest {
        val start = Instant.parse("2024-01-10T00:00:00Z")
        val endExclusive = Instant.parse("2024-01-13T00:00:00Z")
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(
                tracked(1, EntryType.MEAL, start),
                tracked(2, EntryType.MEAL, Instant.parse("2024-01-11T12:00:00Z")),
                tracked(3, EntryType.MEAL, Instant.parse("2024-01-12T23:59:00Z")),
                tracked(4, EntryType.MEAL, endExclusive),
                tracked(5, EntryType.MEAL, Instant.parse("2024-01-09T23:59:00Z"))
            )
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply { parentEntries = trackedRepo.entries }

        val data = gathererWith(trackedRepo, analysisRepo)
            .gather(request(sections = setOf(HealthReportSection.MEALS_AND_NUTRITION)))

        assertEquals(3, data.meals.size)
        assertEquals(
            listOf(LocalDate(2024, 1, 10), LocalDate(2024, 1, 11), LocalDate(2024, 1, 12)),
            data.meals.map { it.date }
        )
    }

    @Test
    fun `check-ins and summaries use inclusive local dates while polar is end-exclusive`() = runTest {
        val checkIns = FakeDailyCheckInRepository().apply {
            checkIns = listOf(
                checkIn(LocalDate(2024, 1, 10)),
                checkIn(LocalDate(2024, 1, 12)),
                checkIn(LocalDate(2024, 1, 13))
            )
        }
        val daily = FakeDailySummaryRepository().apply {
            summaries = listOf(
                DailySummary(summaryId = 1, summaryDate = LocalDate(2024, 1, 10)),
                DailySummary(summaryId = 2, summaryDate = LocalDate(2024, 1, 12))
            )
        }
        val polar = FakePolarSyncRepository().apply {
            activities = listOf(
                storedActivity(LocalDate(2024, 1, 12)),
                storedActivity(LocalDate(2024, 1, 13))
            )
        }

        val data = gathererWith(FakeTrackedEntryRepository(), FakeEntryAnalysisRepository(), checkIns, dailySummaries = daily, polar = polar)
            .gather(request(sections = setOf(HealthReportSection.CHECK_INS, HealthReportSection.DAILY_AND_WEEKLY_SUMMARIES, HealthReportSection.POLAR_METRICS)))

        assertEquals(2, data.checkIns.size)
        assertEquals(2, data.dailySummaries.size)
        // 2024-01-13 is outside [start, endExclusive=2024-01-13)
        assertEquals(1, data.polarDays.size)
        assertEquals(LocalDate(2024, 1, 12), data.polarDays.single().date)
    }

    @Test
    fun `captured time zone decides the local day`() = runTest {
        val tokyo = TimeZone.of("Asia/Tokyo")
        // 2023-12-31T20:00Z = 2024-01-01 05:00 JST
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(tracked(1, EntryType.MEAL, Instant.parse("2023-12-31T20:00:00Z")))
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply { parentEntries = trackedRepo.entries }
        val tokyoRange = DateRange.of(LocalDate(2024, 1, 1), LocalDate(2024, 1, 1))

        val data = gathererWith(trackedRepo, analysisRepo)
            .gather(request(sections = setOf(HealthReportSection.MEALS_AND_NUTRITION), range = tokyoRange, zone = tokyo))

        assertEquals(1, data.meals.size)
        assertEquals(LocalDate(2024, 1, 1), data.meals.single().date)
        // And in UTC it would fall outside 2024-01-01.
        val utcData = gathererWith(trackedRepo, analysisRepo)
            .gather(request(sections = setOf(HealthReportSection.MEALS_AND_NUTRITION), range = tokyoRange, zone = TimeZone.UTC))
        assertTrue(utcData.meals.isEmpty())
    }

    @Test
    fun `meal analysis is parsed into typed report fields with provenance`() = runTest {
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(tracked(1, EntryType.MEAL, Instant.parse("2024-01-11T08:00:00Z"), notes = "Logged lunch"))
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply {
            parentEntries = trackedRepo.entries
            analyses = mapOf(1L to listOf(analysis(1, mealJson(exact = true))))
        }

        val data = gathererWith(trackedRepo, analysisRepo)
            .gather(request(sections = setOf(HealthReportSection.MEALS_AND_NUTRITION)))

        val meal = data.meals.single()
        assertEquals("Logged lunch", meal.userNotes)
        assertEquals(1, meal.foodItems.size)
        assertEquals("Rice bowl", meal.foodItems.single().name)
        assertEquals(ReportProvenance.EXACT_PROFILE, meal.foodItems.single().provenance)
        assertEquals(450.0, meal.nutrition?.totalCalories)
        assertEquals(ReportProvenance.EXACT_PROFILE, meal.nutrition?.provenance)
        assertEquals("Balanced meal", meal.healthInsights?.summary)
    }

    @Test
    fun `AI-estimated meal nutrition is labelled AI-estimated`() = runTest {
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(tracked(1, EntryType.MEAL, Instant.parse("2024-01-11T08:00:00Z")))
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply {
            parentEntries = trackedRepo.entries
            analyses = mapOf(1L to listOf(analysis(1, mealJson(exact = false))))
        }

        val data = gathererWith(trackedRepo, analysisRepo)
            .gather(request(sections = setOf(HealthReportSection.MEALS_AND_NUTRITION)))

        assertEquals(ReportProvenance.AI_ESTIMATED, data.meals.single().foodItems.single().provenance)
        assertEquals(ReportProvenance.AI_ESTIMATED, data.meals.single().nutrition?.provenance)
    }

    @Test
    fun `malformed analysis payload preserves user-entered data and omits derived content`() = runTest {
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(tracked(1, EntryType.MEAL, Instant.parse("2024-01-11T08:00:00Z"), notes = "My note survives"))
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply {
            parentEntries = trackedRepo.entries
            analyses = mapOf(1L to listOf(analysis(1, "{ not json")))
        }

        val data = gathererWith(trackedRepo, analysisRepo)
            .gather(request(sections = setOf(HealthReportSection.MEALS_AND_NUTRITION)))

        val meal = data.meals.single()
        assertEquals("My note survives", meal.userNotes)
        assertTrue(meal.foodItems.isEmpty())
        assertNull(meal.nutrition)
        assertNull(meal.healthInsights)
    }

    @Test
    fun `check-in food and factors come only from completed analyses`() = runTest {
        val checkIns = FakeDailyCheckInRepository().apply {
            checkIns = listOf(checkIn(LocalDate(2024, 1, 11)))
        }
        val checkInAnalyses = FakeCheckInAnalysisRepository().apply {
            analyses = listOf(
                CheckInAnalysis(
                    analysisId = 1,
                    checkInDate = LocalDate(2024, 1, 11),
                    slot = CheckInSlot.MORNING,
                    status = CheckInAnalysisStatus.COMPLETED,
                    analyzedAt = Instant.parse("2024-01-11T02:00:00Z"),
                    facets = CheckInFacets(
                        mentionedFood = listOf(
                            MentionedFood(
                                name = "Banana",
                                portionSize = "one",
                                nutrition = NutritionEstimate(totalCalories = 105.0)
                            )
                        ),
                        factors = emptyList()
                    )
                )
            )
        }

        val data = gathererWith(FakeTrackedEntryRepository(), FakeEntryAnalysisRepository(), checkIns, checkInAnalyses)
            .gather(request(sections = setOf(HealthReportSection.CHECK_INS)))

        val report = data.checkIns.single()
        assertEquals("Rough night", report.responseText)
        assertEquals(1, report.mentionedFood.size)
        assertEquals("Banana", report.mentionedFood.single().name)
    }

    @Test
    fun `pending check-in analysis contributes no derived content`() = runTest {
        val checkIns = FakeDailyCheckInRepository().apply {
            checkIns = listOf(checkIn(LocalDate(2024, 1, 11)))
        }
        val checkInAnalyses = FakeCheckInAnalysisRepository().apply {
            analyses = listOf(
                CheckInAnalysis(
                    analysisId = 1,
                    checkInDate = LocalDate(2024, 1, 11),
                    slot = CheckInSlot.MORNING,
                    status = CheckInAnalysisStatus.PENDING,
                    analyzedAt = Instant.parse("2024-01-11T02:00:00Z"),
                    facets = null
                )
            )
        }

        val data = gathererWith(FakeTrackedEntryRepository(), FakeEntryAnalysisRepository(), checkIns, checkInAnalyses)
            .gather(request(sections = setOf(HealthReportSection.CHECK_INS)))

        val report = data.checkIns.single()
        assertEquals("Rough night", report.responseText)
        assertTrue(report.mentionedFood.isEmpty())
        assertTrue(report.goodFactors.isEmpty())
    }

    @Test
    fun `weight provenance reflects manual versus LLM-detected source`() = runTest {
        val weights = FakeWeightHistoryRepository().apply {
            records = listOf(
                WeightRecord(
                    weightRecordId = 1,
                    recordedAt = Instant.parse("2024-01-11T07:00:00Z"),
                    weightValue = 80.0,
                    weightUnit = "kg",
                    source = "Manual"
                ),
                WeightRecord(
                    weightRecordId = 2,
                    recordedAt = Instant.parse("2024-01-11T08:00:00Z"),
                    weightValue = 79.5,
                    weightUnit = "kg",
                    source = "LlmDetected"
                )
            )
        }

        val data = gathererWith(FakeTrackedEntryRepository(), FakeEntryAnalysisRepository(), weights = weights)
            .gather(request(sections = setOf(HealthReportSection.WEIGHT_HISTORY)))

        assertEquals(2, data.weightRecords.size)
        assertEquals(ReportProvenance.USER_ENTERED, data.weightRecords[0].provenance)
        assertEquals(ReportProvenance.AI_ESTIMATED, data.weightRecords[1].provenance)
    }

    @Test
    fun `polar families for the same day merge into one day record`() = runTest {
        val date = LocalDate(2024, 1, 11)
        val polar = FakePolarSyncRepository().apply {
            activities = listOf(storedActivity(date))
            sleeps = listOf(
                StoredPolarSleepResult(
                    recordId = 1, externalId = "s1", source = "Polar", localDate = date,
                    startedAt = null, endedAt = null, syncedAt = Instant.parse("2024-01-11T06:00:00Z"),
                    data = PolarSleepResult(
                        date = date.toString(), sleepStart = "00:00", sleepEnd = "07:00",
                        durationSeconds = 25_200, deepSleepSeconds = 6_000, remSleepSeconds = 6_000,
                        lightSleepSeconds = 12_000, awakeSeconds = 1_200, efficiencyPercent = 95.0,
                        continuityIndex = 90.0, interruptionCount = 1, longInterruptionCount = 0,
                        sleepScore = 85.0, remScore = 80.0, deepSleepScore = 85.0, scoreRate = 4
                    )
                )
            )
            trainings = listOf(
                StoredPolarTrainingSession(
                    recordId = 1, externalId = "t1", source = "Polar", localDate = date,
                    startedAt = "10:00", syncedAt = Instant.parse("2024-01-11T12:00:00Z"),
                    data = PolarTrainingSession(
                        id = "t1", startTime = "10:00", durationSeconds = 1_800, sportId = "RUNNING",
                        calories = 250, distanceMeters = 4_500.0, averageHeartRate = 140, maxHeartRate = 165,
                        trainingBenefit = "Basic"
                    )
                ),
                StoredPolarTrainingSession(
                    recordId = 2, externalId = "t2", source = "Polar", localDate = date,
                    startedAt = "08:00", syncedAt = Instant.parse("2024-01-11T09:00:00Z"),
                    data = PolarTrainingSession(
                        id = "t2", startTime = "08:00", durationSeconds = 900, sportId = "WALKING",
                        calories = 90, distanceMeters = 1_500.0, averageHeartRate = 100, maxHeartRate = 120,
                        trainingBenefit = "Basic"
                    )
                )
            )
            recharges = listOf(
                StoredPolarNightlyRecharge(
                    recordId = 1, externalId = "r1", source = "Polar", localDate = date,
                    syncedAt = Instant.parse("2024-01-11T06:00:00Z"),
                    data = PolarNightlyRecharge(
                        date = date.toString(), ansStatus = 62.0, ansRate = 3, recoveryIndicator = 62,
                        recoveryIndicatorSubLevel = 2, hrvRmssd = 55, hrvMeanRri = 950,
                        baselineRmssd = 50, baselineRmssdSd = 5, baselineRri = 940, baselineRriSd = 30
                    )
                )
            )
        }

        val data = gathererWith(FakeTrackedEntryRepository(), FakeEntryAnalysisRepository(), polar = polar)
            .gather(request(sections = setOf(HealthReportSection.POLAR_METRICS)))

        val day = data.polarDays.single()
        assertNotNull(day.activity)
        assertNotNull(day.sleep)
        assertNotNull(day.nightlyRecharge)
        assertEquals(2, day.trainingSessions.size)
        // Training sessions sorted by start time.
        assertEquals(listOf("WALKING", "RUNNING"), day.trainingSessions.map { it.sportId })
    }

    @Test
    fun `empty range yields an empty snapshot with zero preview counts`() = runTest {
        val data = gathererWith(FakeTrackedEntryRepository(), FakeEntryAnalysisRepository())
            .gather(request(sections = setOf(HealthReportSection.MEALS_AND_NUTRITION, HealthReportSection.WEIGHT_HISTORY)))

        assertTrue(data.isEmpty)
        val preview = data.toPreview()
        assertEquals(0, preview.mealCount)
        assertEquals(0, preview.checkInCount)
        assertEquals(0, preview.totalEntries)
        assertEquals(false, preview.profileIncluded)
    }

    @Test
    fun `preview counts derive from the snapshot itself`() = runTest {
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(
                tracked(1, EntryType.MEAL, Instant.parse("2024-01-11T08:00:00Z")),
                tracked(2, EntryType.EXERCISE, Instant.parse("2024-01-11T09:00:00Z"))
            )
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply { parentEntries = trackedRepo.entries }
        val checkIns = FakeDailyCheckInRepository().apply { checkIns = listOf(checkIn(LocalDate(2024, 1, 11))) }
        val settings = FakeAppSettingsRepository().apply { setHeight(180.0) }

        val data = gathererWith(trackedRepo, analysisRepo, checkIns, settings = settings)
            .gather(request())

        val preview = data.toPreview()
        assertEquals(true, preview.profileIncluded)
        assertEquals(1, preview.mealCount)
        assertEquals(1, preview.exerciseCount)
        assertEquals(1, preview.checkInCount)
        assertEquals(3, preview.totalEntries)
    }

    @Test
    fun `profile section reads goals and preferences from settings`() = runTest {
        val settings = FakeAppSettingsRepository().apply {
            setHeight(180.0)
            setHeightUnit("cm")
            setCurrentWeight(80.0)
            setWeightUnit("kg")
            setSex("Male")
            setDateOfBirth("1990-01-01")
            setActivityLevel("Moderate")
            setGoalsAndPreferences("Lose weight")
        }

        val data = gathererWith(FakeTrackedEntryRepository(), FakeEntryAnalysisRepository(), settings = settings)
            .gather(request(sections = setOf(HealthReportSection.PROFILE_AND_GOALS)))

        val profile = assertNotNull(data.profile)
        assertEquals(180.0, profile.height)
        assertEquals("cm", profile.heightUnit)
        assertEquals("Lose weight", profile.goalsAndPreferences)
    }

    @Test
    fun `profile section is omitted when not selected even if data exists`() = runTest {
        val settings = FakeAppSettingsRepository().apply { setHeight(180.0) }

        val data = gathererWith(FakeTrackedEntryRepository(), FakeEntryAnalysisRepository(), settings = settings)
            .gather(request(sections = setOf(HealthReportSection.WEIGHT_HISTORY)))

        assertNull(data.profile)
    }

    @Test
    fun `entry ordering is chronological within the snapshot`() = runTest {
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(
                tracked(1, EntryType.MEAL, Instant.parse("2024-01-11T12:00:00Z")),
                tracked(2, EntryType.MEAL, Instant.parse("2024-01-11T08:00:00Z")),
                tracked(3, EntryType.EXERCISE, Instant.parse("2024-01-10T09:00:00Z"))
            )
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply { parentEntries = trackedRepo.entries }

        val data = gathererWith(trackedRepo, analysisRepo)
            .gather(request(sections = setOf(HealthReportSection.MEALS_AND_NUTRITION, HealthReportSection.EXERCISE_AND_ACTIVITY)))

        assertEquals(listOf(LocalDate(2024, 1, 11), LocalDate(2024, 1, 11)), data.meals.map { it.date })
        assertEquals(1, data.exercise.size)
    }

    @Test
    fun `meal-only request excludes OTHER and UNKNOWN entries`() = runTest {
        val start = Instant.parse("2024-01-10T00:00:00Z")
        val endExclusive = Instant.parse("2024-01-13T00:00:00Z")
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(
                tracked(1, EntryType.MEAL, Instant.parse("2024-01-11T08:00:00Z")),
                tracked(2, EntryType.OTHER, Instant.parse("2024-01-11T09:00:00Z"), notes = "Doctor visit"),
                tracked(3, EntryType.UNKNOWN, Instant.parse("2024-01-11T10:00:00Z"), notes = "Mystery entry")
            )
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply { parentEntries = trackedRepo.entries }

        val data = gathererWith(trackedRepo, analysisRepo)
            .gather(request(sections = setOf(HealthReportSection.MEALS_AND_NUTRITION)))

        assertEquals(1, data.meals.size)
        assertEquals(0, data.exercise.size)
        assertEquals(0, data.sleep.size)
        assertEquals(1, data.toPreview().totalEntries)
        assertTrue(data.meals.none { it.userNotes == "Doctor visit" })
        assertTrue(data.meals.none { it.userNotes == "Mystery entry" })
    }

    @Test
    fun `sleep-only request excludes OTHER and UNKNOWN entries`() = runTest {
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(
                tracked(1, EntryType.SLEEP, Instant.parse("2024-01-11T08:00:00Z")),
                tracked(2, EntryType.OTHER, Instant.parse("2024-01-11T09:00:00Z")),
                tracked(3, EntryType.UNKNOWN, Instant.parse("2024-01-11T10:00:00Z"))
            )
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply { parentEntries = trackedRepo.entries }

        val data = gathererWith(trackedRepo, analysisRepo)
            .gather(request(sections = setOf(HealthReportSection.SLEEP_AND_RECOVERY)))

        assertEquals(1, data.sleep.size)
        assertTrue(data.meals.isEmpty())
        assertTrue(data.exercise.isEmpty())
    }

    @Test
    fun `exercise-only request excludes OTHER and UNKNOWN entries`() = runTest {
        val trackedRepo = FakeTrackedEntryRepository().apply {
            entries = listOf(
                tracked(1, EntryType.EXERCISE, Instant.parse("2024-01-11T08:00:00Z")),
                tracked(2, EntryType.OTHER, Instant.parse("2024-01-11T09:00:00Z")),
                tracked(3, EntryType.UNKNOWN, Instant.parse("2024-01-11T10:00:00Z"))
            )
        }
        val analysisRepo = FakeEntryAnalysisRepository().apply { parentEntries = trackedRepo.entries }

        val data = gathererWith(trackedRepo, analysisRepo)
            .gather(request(sections = setOf(HealthReportSection.EXERCISE_AND_ACTIVITY)))

        assertEquals(1, data.exercise.size)
        assertTrue(data.meals.isEmpty())
        assertTrue(data.sleep.isEmpty())
    }

    @Test
    fun `selected but empty profile yields no profile section`() = runTest {
        val settings = FakeAppSettingsRepository()

        val data = gathererWith(FakeTrackedEntryRepository(), FakeEntryAnalysisRepository(), settings = settings)
            .gather(request(sections = setOf(HealthReportSection.PROFILE_AND_GOALS)))

        assertTrue(data.profile == null)
        assertEquals(false, data.toPreview().profileIncluded)
    }

    private fun checkIn(date: LocalDate) = DailyCheckIn(
        checkInId = 1,
        checkInDate = date,
        slot = CheckInSlot.MORNING,
        capturedAt = Instant.parse("2024-01-11T07:00:00Z"),
        responseText = "Rough night",
        inputSource = CheckInInputSource.TYPED
    )

    private fun storedActivity(date: LocalDate) = StoredPolarActivity(
        recordId = 1,
        externalId = "a1",
        source = "Polar",
        localDate = date,
        startedAt = null,
        syncedAt = Instant.parse("2024-01-11T06:00:00Z"),
        data = PolarDailyActivity(
            date = date.toString(),
            totalSteps = 8_000,
            stepSampleStartTime = "00:00",
            stepSampleIntervalMs = 60_000,
            stepSamples = emptyList()
        )
    )
}
