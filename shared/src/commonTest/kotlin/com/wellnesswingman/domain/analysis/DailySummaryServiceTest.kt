package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.DailySummaryResult
import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeightRecord
import com.wellnesswingman.data.model.analysis.ExerciseAnalysisResult
import com.wellnesswingman.data.model.analysis.ExerciseInsights
import com.wellnesswingman.data.model.analysis.ExerciseMetrics
import com.wellnesswingman.data.model.analysis.FoodItem
import com.wellnesswingman.data.model.analysis.HealthInsights
import com.wellnesswingman.data.model.analysis.MealAnalysisResult
import com.wellnesswingman.data.model.analysis.NutritionEstimate
import com.wellnesswingman.data.model.analysis.OtherAnalysisResult
import com.wellnesswingman.data.model.analysis.SleepAnalysisResult
import com.wellnesswingman.data.model.analysis.UnifiedAnalysisResult
import com.wellnesswingman.data.model.polar.PolarDailyActivity
import com.wellnesswingman.data.model.polar.PolarMetricFamily
import com.wellnesswingman.data.model.polar.PolarNightlyRecharge
import com.wellnesswingman.data.model.polar.PolarSleepResult
import com.wellnesswingman.data.model.polar.PolarSyncCheckpoint
import com.wellnesswingman.data.model.polar.PolarTrainingSession
import com.wellnesswingman.data.model.polar.PolarUserProfile
import com.wellnesswingman.data.model.polar.StoredPolarActivity
import com.wellnesswingman.data.model.polar.StoredPolarNightlyRecharge
import com.wellnesswingman.data.model.polar.StoredPolarSleepResult
import com.wellnesswingman.data.model.polar.StoredPolarTrainingSession
import com.wellnesswingman.data.model.polar.StoredPolarUserProfile
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.PolarSyncRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import com.wellnesswingman.domain.llm.LlmAnalysisResult
import com.wellnesswingman.domain.llm.LlmClient
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.LlmDiagnostics
import com.wellnesswingman.domain.polar.PolarInsightService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.*

class DailySummaryServiceTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeTrackedEntryRepository(
        private val entriesToReturn: List<TrackedEntry> = emptyList()
    ) : TrackedEntryRepository {
        override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long) = entriesToReturn
        override suspend fun getEntriesForDay(date: LocalDate) = entriesToReturn
        override suspend fun getAllEntries() = entriesToReturn
        override fun observeAllEntries(): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntryById(id: Long) = entriesToReturn.find { it.entryId == id }
        override suspend fun getEntryByExternalId(externalId: String) = null
        override fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntriesForWeek(startMillis: Long, endMillis: Long) = entriesToReturn
        override suspend fun getEntriesForMonth(startMillis: Long, endMillis: Long) = entriesToReturn
        override suspend fun getEntriesByStatus(status: ProcessingStatus) = entriesToReturn.filter { it.processingStatus == status }
        override suspend fun getPendingEntries() = entriesToReturn.filter { it.processingStatus == ProcessingStatus.PENDING }
        override suspend fun insertEntry(entry: TrackedEntry) = 1L
        override suspend fun updateEntryStatus(id: Long, status: ProcessingStatus) {}
        override suspend fun updateEntryType(id: Long, entryType: EntryType) {}
        override suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int) {}
        override suspend fun updateUserNotes(id: Long, notes: String?) {}
        override suspend fun deleteEntry(id: Long) {}
        override suspend fun upsertEntry(entry: TrackedEntry) {}
    }

    private class FakeEntryAnalysisRepository(
        private val analyses: Map<Long, EntryAnalysis> = emptyMap()
    ) : EntryAnalysisRepository {
        override suspend fun getLatestAnalysisForEntry(entryId: Long) = analyses[entryId]
        override suspend fun getAllAnalyses() = analyses.values.toList()
        override suspend fun getAnalysisById(id: Long) = null
        override suspend fun getAnalysisByExternalId(externalId: String) = null
        override suspend fun getAnalysesForEntry(entryId: Long) = listOfNotNull(analyses[entryId])
        override suspend fun insertAnalysis(analysis: EntryAnalysis) = 1L
        override suspend fun updateAnalysis(id: Long, insightsJson: String, schemaVersion: String) {}
        override suspend fun deleteAnalysis(id: Long) {}
        override suspend fun deleteAnalysesForEntry(entryId: Long) {}
        override suspend fun upsertAnalysis(analysis: EntryAnalysis) {}
    }

    private class FakeDailySummaryRepository : DailySummaryRepository {
        val inserted = mutableListOf<DailySummary>()
        val deleted = mutableListOf<LocalDate>()
        private var nextId = 1L

        override suspend fun insertSummary(summary: DailySummary): Long {
            val id = nextId++
            inserted.add(summary.copy(summaryId = id))
            return id
        }
        override suspend fun deleteSummaryByDate(date: LocalDate) { deleted.add(date) }
        override suspend fun getAllSummaries() = inserted.toList()
        override suspend fun getSummaryById(id: Long) = inserted.find { it.summaryId == id }
        override suspend fun getSummaryByExternalId(externalId: String) = null
        override suspend fun getSummaryForDate(date: LocalDate) = inserted.find { it.summaryDate == date }
        override suspend fun getSummariesForDateRange(startDate: LocalDate, endDate: LocalDate) =
            inserted.filter { it.summaryDate >= startDate && it.summaryDate <= endDate }
        override suspend fun getRecentSummaries(limit: Long) = inserted.take(limit.toInt())
        override suspend fun updateSummary(id: Long, highlights: String, recommendations: String) {}
        override suspend fun updateSummaryByDate(date: LocalDate, highlights: String, recommendations: String) {}
        override suspend fun updateUserComments(date: LocalDate, comments: String?) {}
        override suspend fun deleteSummary(id: Long) {}
        override suspend fun deleteOldSummaries(beforeDate: LocalDate) {}
        override suspend fun upsertSummary(summary: DailySummary) {}
    }

    private class FakeWeightHistoryRepository(
        private val records: List<WeightRecord> = emptyList()
    ) : WeightHistoryRepository {
        override suspend fun getWeightHistory(startDate: Instant, endDate: Instant) = records
        override suspend fun addWeightRecord(record: WeightRecord) = 1L
        override suspend fun getLatestWeightRecord() = records.lastOrNull()
        override suspend fun getAllWeightRecords() = records
        override suspend fun deleteWeightRecord(recordId: Long) {}
        override suspend fun nullifyRelatedEntryId(entryId: Long) {}
        override suspend fun upsertWeightRecord(record: WeightRecord) {}
    }

    private class FakePolarSyncRepository(
        private val activities: List<StoredPolarActivity> = emptyList(),
        private val sleepResults: List<StoredPolarSleepResult> = emptyList(),
        private val trainingSessions: List<StoredPolarTrainingSession> = emptyList(),
        private val nightlyRecharge: List<StoredPolarNightlyRecharge> = emptyList()
    ) : PolarSyncRepository {
        override suspend fun upsertActivities(activities: List<PolarDailyActivity>, syncedAt: Instant) = 0
        override suspend fun getActivities(startDate: LocalDate, endDateExclusive: LocalDate) =
            activities.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
        override suspend fun upsertSleepResults(results: List<PolarSleepResult>, syncedAt: Instant) = 0
        override suspend fun getSleepResults(startDate: LocalDate, endDateExclusive: LocalDate) =
            sleepResults.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
        override suspend fun upsertTrainingSessions(sessions: List<PolarTrainingSession>, syncedAt: Instant) = 0
        override suspend fun getTrainingSessions(startDate: LocalDate, endDateExclusive: LocalDate) =
            trainingSessions.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
        override suspend fun upsertNightlyRecharge(results: List<PolarNightlyRecharge>, syncedAt: Instant) = 0
        override suspend fun getNightlyRecharge(startDate: LocalDate, endDateExclusive: LocalDate) =
            nightlyRecharge.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
        override suspend fun upsertUserProfile(userId: String, profile: PolarUserProfile, syncedAt: Instant) = Unit
        override suspend fun getUserProfile(userId: String): StoredPolarUserProfile? = null
        override suspend fun getCheckpoint(metricFamily: PolarMetricFamily): PolarSyncCheckpoint? = null
        override suspend fun getAllCheckpoints(): List<PolarSyncCheckpoint> = emptyList()
        override suspend fun updateCheckpoint(checkpoint: PolarSyncCheckpoint) = Unit
        override suspend fun clearCheckpoint(metricFamily: PolarMetricFamily) = Unit
        override suspend fun clearAll() = Unit
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun makeLlmClientFactory(hasKey: Boolean, response: String = """{"insights":[],"recommendations":[]}"""): LlmClientFactory {
        val fakeLlmClient = object : LlmClient {
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?) =
                LlmAnalysisResult(response, LlmDiagnostics())
            override suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?) =
                LlmAnalysisResult(response, LlmDiagnostics())
        }
        val factory = mockk<LlmClientFactory>()
        every { factory.hasCurrentApiKey() } returns hasKey
        if (hasKey) every { factory.createForCurrentProvider() } returns fakeLlmClient
        return factory
    }

    /**
     * Like [makeLlmClientFactory] but also appends each completion prompt to [capturedPrompts],
     * enabling tests to assert what was sent to the LLM.
     */
    private fun makeCapturingLlmClientFactory(
        response: String = """{"insights":[],"recommendations":[]}""",
        capturedPrompts: MutableList<String>
    ): LlmClientFactory {
        val fakeLlmClient = object : LlmClient {
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?) =
                LlmAnalysisResult(response, LlmDiagnostics())
            override suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?): LlmAnalysisResult {
                capturedPrompts.add(prompt)
                return LlmAnalysisResult(response, LlmDiagnostics())
            }
        }
        val factory = mockk<LlmClientFactory>()
        every { factory.hasCurrentApiKey() } returns true
        every { factory.createForCurrentProvider() } returns fakeLlmClient
        return factory
    }

    private fun makeCompletedEntry(
        entryId: Long,
        entryType: EntryType,
        userNotes: String? = null
    ) = TrackedEntry(
        entryId = entryId,
        entryType = entryType,
        capturedAt = Clock.System.now(),
        processingStatus = ProcessingStatus.COMPLETED,
        userNotes = userNotes
    )

    private fun makeUnifiedJson(result: UnifiedAnalysisResult) =
        json.encodeToString(UnifiedAnalysisResult.serializer(), result)

    private fun makeAnalysis(entryId: Long, insightsJson: String) = EntryAnalysis(
        entryId = entryId,
        capturedAt = Clock.System.now(),
        insightsJson = insightsJson
    )

    private fun polarInsightService(
        activities: List<StoredPolarActivity> = emptyList(),
        sleepResults: List<StoredPolarSleepResult> = emptyList(),
        trainingSessions: List<StoredPolarTrainingSession> = emptyList(),
        nightlyRecharge: List<StoredPolarNightlyRecharge> = emptyList()
    ) = PolarInsightService(
        FakePolarSyncRepository(
            activities = activities,
            sleepResults = sleepResults,
            trainingSessions = trainingSessions,
            nightlyRecharge = nightlyRecharge
        )
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `generateSummary returns Error when no API key`() = runTest {
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = false),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Error>(result)
        assertTrue((result as DailySummaryResult.Error).message.contains("API key", ignoreCase = true))
    }

    @Test
    fun `generateSummary returns NoEntries when no completed entries`() = runTest {
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(emptyList()),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.NoEntries>(result)
    }

    @Test
    fun `generateSummary succeeds for a Polar-only day and includes Polar context in prompt`() = runTest {
        val prompts = mutableListOf<String>()
        val date = LocalDate(2025, 3, 1)
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(emptyList()),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(capturedPrompts = prompts),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService(
                activities = listOf(
                    StoredPolarActivity(1, "activity:2025-03-01", "Polar", date, null, Clock.System.now(), PolarDailyActivity("2025-03-01", 8123, "00:00:00", 60000, listOf(8123)))
                ),
                sleepResults = listOf(
                    StoredPolarSleepResult(2, "sleep:2025-03-01", "Polar", date, null, null, Clock.System.now(), PolarSleepResult("2025-03-01", "2025-02-28T23:00:00Z", "2025-03-01T07:00:00Z", 28800, 7200, 5400, 14400, 1800, 91.0, 4.2, 2, 0, 85.0, 80.0, 82.0, 4))
                )
            )
        )

        val result = service.generateSummary(date)

        assertIs<DailySummaryResult.Success>(result)
        assertTrue(prompts.single().contains("Polar Sync Context"))
        assertTrue(prompts.single().contains("Steps (Polar): 8123"))
        assertTrue(prompts.single().contains("Sleep (Polar): 8.0h"))
    }

    @Test
    fun `generateSummary prefers tracked sleep details over Polar sleep when both exist`() = runTest {
        val prompts = mutableListOf<String>()
        val date = LocalDate(2025, 3, 1)
        val sleepEntry = makeCompletedEntry(1, EntryType.SLEEP)
        val sleepAnalysis = makeAnalysis(
            1,
            makeUnifiedJson(
                UnifiedAnalysisResult(
                    sleepAnalysis = SleepAnalysisResult(durationHours = 7.5, qualitySummary = "Tracked sleep")
                )
            )
        )
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(sleepEntry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(1L to sleepAnalysis)),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(capturedPrompts = prompts),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService(
                sleepResults = listOf(
                    StoredPolarSleepResult(2, "sleep:2025-03-01", "Polar", date, null, null, Clock.System.now(), PolarSleepResult("2025-03-01", "2025-02-28T23:00:00Z", "2025-03-01T06:00:00Z", 25200, 7200, 5400, 10800, 1800, 91.0, 4.2, 2, 0, 85.0, 80.0, 82.0, 4))
                )
            )
        )

        val result = service.generateSummary(date)

        assertIs<DailySummaryResult.Success>(result)
        assertTrue(prompts.single().contains("Tracked sleep"))
        assertFalse(prompts.single().contains("Sleep (Polar):"))
    }

    @Test
    fun `generateSummary returns NoEntries when only pending entries exist`() = runTest {
        val pendingEntry = TrackedEntry(
            entryId = 1,
            entryType = EntryType.MEAL,
            capturedAt = Clock.System.now(),
            processingStatus = ProcessingStatus.PENDING
        )
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(pendingEntry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.NoEntries>(result)
    }

    @Test
    fun `generateSummary excludes DAILY_SUMMARY entry type`() = runTest {
        val dailySummaryEntry = TrackedEntry(
            entryId = 1,
            entryType = EntryType.DAILY_SUMMARY,
            capturedAt = Clock.System.now(),
            processingStatus = ProcessingStatus.COMPLETED
        )
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(dailySummaryEntry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.NoEntries>(result)
    }

    @Test
    fun `generateSummary with meal entry returns Success and stores payloadJson`() = runTest {
        val entry = makeCompletedEntry(1, EntryType.MEAL, userNotes = "Felt good")
        val mealAnalysis = UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                foodItems = listOf(FoodItem(name = "Salad"), FoodItem(name = "Chicken")),
                nutrition = NutritionEstimate(
                    totalCalories = 600.0, protein = 40.0, carbohydrates = 50.0, fat = 15.0,
                    fiber = 8.0, sugar = 5.0, sodium = 500.0
                ),
                healthInsights = HealthInsights(summary = "Healthy balanced meal")
            )
        )
        val llmResponse = """
            {
              "schemaVersion": "1.0",
              "insights": ["High protein intake", "Good fiber"],
              "recommendations": ["Add more vegetables"]
            }
        """.trimIndent()

        val fakeSummaryRepo = FakeDailySummaryRepository()
        val capturedPrompts = mutableListOf<String>()
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(1L to makeAnalysis(1L, makeUnifiedJson(mealAnalysis)))
            ),
            dailySummaryRepository = fakeSummaryRepo,
            llmClientFactory = makeCapturingLlmClientFactory(response = llmResponse, capturedPrompts = capturedPrompts),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        assertEquals(1, fakeSummaryRepo.inserted.size)
        val saved = fakeSummaryRepo.inserted.first()
        assertNotNull(saved.payloadJson)
        assertTrue(saved.payloadJson!!.contains("2025-03-01"))
        // Verify food items from meal analysis appear in the prompt
        assertTrue(capturedPrompts.isNotEmpty())
        val prompt = capturedPrompts.first()
        assertTrue(prompt.contains("Salad"), "Expected food item 'Salad' in prompt")
        assertTrue(prompt.contains("Chicken"), "Expected food item 'Chicken' in prompt")
    }

    @Test
    fun `generateSummary with exercise entry calls buildExerciseDetailLine`() = runTest {
        val entry = makeCompletedEntry(2, EntryType.EXERCISE, userNotes = "Tough run")
        val exerciseAnalysis = UnifiedAnalysisResult(
            exerciseAnalysis = ExerciseAnalysisResult(
                activityType = "Running",
                metrics = ExerciseMetrics(
                    durationMinutes = 45.0,
                    distance = 8.0,
                    distanceUnit = "km",
                    calories = 400.0,
                    averageHeartRate = 155.0
                ),
                insights = ExerciseInsights(summary = "Strong endurance session")
            )
        )
        val llmResponse = """{"insights":["Great run"],"recommendations":["Rest tomorrow"]}"""

        val capturedPrompts = mutableListOf<String>()
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(2L to makeAnalysis(2L, makeUnifiedJson(exerciseAnalysis)))
            ),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(response = llmResponse, capturedPrompts = capturedPrompts),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        assertTrue(capturedPrompts.isNotEmpty())
        val prompt = capturedPrompts.first()
        assertTrue(prompt.contains("Running"), "Expected activity type 'Running' in prompt")
        assertTrue(prompt.contains("8.0 km"), "Expected distance '8.0 km' in prompt")
        assertTrue(prompt.contains("Tough run"), "Expected user note 'Tough run' in prompt")
    }

    @Test
    fun `generateSummary with sleep entry calls buildSleepDetailLine`() = runTest {
        val entry = makeCompletedEntry(3, EntryType.SLEEP)
        val sleepAnalysis = UnifiedAnalysisResult(
            sleepAnalysis = SleepAnalysisResult(
                durationHours = 7.5,
                qualitySummary = "Restful sleep",
                environmentNotes = listOf("Cool room", "Dark environment")
            )
        )
        val llmResponse = """{"insights":["Good sleep quality"],"recommendations":["Maintain schedule"]}"""

        val capturedPrompts = mutableListOf<String>()
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(3L to makeAnalysis(3L, makeUnifiedJson(sleepAnalysis)))
            ),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(response = llmResponse, capturedPrompts = capturedPrompts),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        assertTrue(capturedPrompts.isNotEmpty())
        val prompt = capturedPrompts.first()
        assertTrue(prompt.contains("7"), "Expected sleep duration in prompt")
        assertTrue(prompt.contains("Restful sleep"), "Expected quality summary in prompt")
        assertTrue(prompt.contains("Cool room"), "Expected environment note in prompt")
    }

    @Test
    fun `generateSummary with other entry calls buildOtherDetailLine`() = runTest {
        val entry = makeCompletedEntry(4, EntryType.OTHER, userNotes = "Took vitamins")
        val otherAnalysis = UnifiedAnalysisResult(
            otherAnalysis = OtherAnalysisResult(
                summary = "Vitamin supplement",
                tags = listOf("supplement", "wellness")
            )
        )
        val llmResponse = """{"insights":["Good supplement habit"],"recommendations":["Continue vitamins"]}"""

        val capturedPrompts = mutableListOf<String>()
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(4L to makeAnalysis(4L, makeUnifiedJson(otherAnalysis)))
            ),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(response = llmResponse, capturedPrompts = capturedPrompts),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        assertTrue(capturedPrompts.isNotEmpty())
        val prompt = capturedPrompts.first()
        assertTrue(prompt.contains("Vitamin supplement"), "Expected other summary in prompt")
        assertTrue(prompt.contains("supplement"), "Expected tag in prompt")
        assertTrue(prompt.contains("Took vitamins"), "Expected user note in prompt")
    }

    @Test
    fun `generateSummary with userComments succeeds`() = runTest {
        val entry = makeCompletedEntry(5, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(totalCalories = 500.0, protein = 30.0, carbohydrates = 60.0, fat = 10.0)
            )
        ))
        val llmResponse = """{"insights":["Good day"],"recommendations":["Keep going"]}"""

        val capturedPrompts = mutableListOf<String>()
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(5L to makeAnalysis(5L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(response = llmResponse, capturedPrompts = capturedPrompts),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1), userComments = "Felt energetic today!")

        assertIs<DailySummaryResult.Success>(result)
        assertTrue(capturedPrompts.isNotEmpty())
        assertTrue(capturedPrompts.first().contains("Felt energetic today!"), "Expected user comments in prompt")
    }

    @Test
    fun `generateSummary parses highlights from alt JSON format with insights key`() = runTest {
        val entry = makeCompletedEntry(6, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(totalCalories = 700.0, protein = 35.0, carbohydrates = 80.0, fat = 20.0)
            )
        ))
        // LLM returns "insights" instead of "highlights"
        val altFormatResponse = """
            {
              "insights": ["Protein target met", "Good calorie balance"],
              "recommendations": ["Add fiber", "Drink water"]
            }
        """.trimIndent()

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(6L to makeAnalysis(6L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = altFormatResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        val success = result as DailySummaryResult.Success
        assertTrue(success.payload.highlights.isNotEmpty() || success.payload.recommendations.isNotEmpty())
    }

    @Test
    fun `generateSummary falls back to text parsing when JSON is invalid`() = runTest {
        val entry = makeCompletedEntry(7, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(totalCalories = 500.0, protein = 25.0, carbohydrates = 60.0, fat = 10.0)
            )
        ))
        val textResponse = """
            Insights:
            - You consumed a balanced meal
            - Protein was adequate
            Recommendations:
            - Add more vegetables
            - Increase water intake
        """.trimIndent()

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(7L to makeAnalysis(7L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = textResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
    }

    @Test
    fun `generateSummary with weight records includes weight in prompt`() = runTest {
        val entry = makeCompletedEntry(8, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(totalCalories = 500.0)
            )
        ))
        val weightRecord = WeightRecord(
            recordedAt = Clock.System.now(),
            weightValue = 72.5,
            weightUnit = "kg",
            source = "Manual"
        )
        val llmResponse = """{"insights":["Tracked weight today"],"recommendations":["Keep tracking"]}"""

        val capturedPrompts = mutableListOf<String>()
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(8L to makeAnalysis(8L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(response = llmResponse, capturedPrompts = capturedPrompts),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository(listOf(weightRecord))
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        assertTrue(capturedPrompts.isNotEmpty())
        val prompt = capturedPrompts.first()
        assertTrue(prompt.contains("72.5"), "Expected weight value '72.5' in prompt")
        assertTrue(prompt.contains("kg"), "Expected weight unit 'kg' in prompt")
    }

    @Test
    fun `generateSummary handles legacy meal-only JSON format`() = runTest {
        val entry = makeCompletedEntry(9, EntryType.MEAL)
        // Legacy format: MealAnalysisResult directly (not wrapped in UnifiedAnalysisResult)
        val legacyMealJson = json.encodeToString(
            MealAnalysisResult.serializer(),
            MealAnalysisResult(
                foodItems = listOf(FoodItem(name = "Pizza")),
                nutrition = NutritionEstimate(totalCalories = 800.0, protein = 30.0, carbohydrates = 90.0, fat = 25.0),
                healthInsights = HealthInsights(summary = "High calorie meal")
            )
        )
        val llmResponse = """{"insights":["High calorie meal"],"recommendations":["Reduce portion"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(9L to makeAnalysis(9L, legacyMealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
    }

    @Test
    fun `regenerateSummary deletes existing and generates new`() = runTest {
        val entry = makeCompletedEntry(10, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(totalCalories = 600.0, protein = 35.0, carbohydrates = 65.0, fat = 18.0)
            )
        ))
        val fakeSummaryRepo = FakeDailySummaryRepository()
        val date = LocalDate(2025, 3, 1)
        val llmResponse = """{"insights":["Regenerated summary"],"recommendations":["Keep going"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(10L to makeAnalysis(10L, mealJson))),
            dailySummaryRepository = fakeSummaryRepo,
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.regenerateSummary(date, userComments = "Regenerated with new comment")

        assertTrue(fakeSummaryRepo.deleted.contains(date))
        assertIs<DailySummaryResult.Success>(result)
    }

    @Test
    fun `generateSummary with multiple entry types processes each correctly`() = runTest {
        val mealEntry = makeCompletedEntry(11, EntryType.MEAL)
        val exerciseEntry = makeCompletedEntry(12, EntryType.EXERCISE)
        val sleepEntry = makeCompletedEntry(13, EntryType.SLEEP)

        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(totalCalories = 500.0, protein = 25.0, carbohydrates = 60.0, fat = 15.0)
            )
        ))
        val exerciseJson = makeUnifiedJson(UnifiedAnalysisResult(
            exerciseAnalysis = ExerciseAnalysisResult(
                activityType = "Yoga",
                metrics = ExerciseMetrics(durationMinutes = 60.0)
            )
        ))
        val sleepJson = makeUnifiedJson(UnifiedAnalysisResult(
            sleepAnalysis = SleepAnalysisResult(durationHours = 8.0)
        ))

        val llmResponse = """{"insights":["Active day"],"recommendations":["Great balance"]}"""
        val fakeSummaryRepo = FakeDailySummaryRepository()

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(mealEntry, exerciseEntry, sleepEntry)
            ),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(
                    11L to makeAnalysis(11L, mealJson),
                    12L to makeAnalysis(12L, exerciseJson),
                    13L to makeAnalysis(13L, sleepJson)
                )
            ),
            dailySummaryRepository = fakeSummaryRepo,
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        assertEquals(1, fakeSummaryRepo.inserted.size)
    }

    @Test
    fun `generateSummary with entry missing analysis still succeeds`() = runTest {
        val entry = makeCompletedEntry(14, EntryType.MEAL)
        val llmResponse = """{"insights":["Summary generated"],"recommendations":["Keep tracking"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(emptyMap()), // no analyses
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
    }

    // ── calculateBalance branch coverage ────────────────────────────────────

    @Test
    fun `generateSummary exercise-only entry hits no-meals timing and null variety`() = runTest {
        // mealCount == 0 → timing = "No meals logged", variety = null
        // macroCalories == 0 → macroBalance = null
        val entry = makeCompletedEntry(15, EntryType.EXERCISE)
        val exerciseJson = makeUnifiedJson(UnifiedAnalysisResult(
            exerciseAnalysis = ExerciseAnalysisResult(
                activityType = "Walking",
                metrics = ExerciseMetrics(durationMinutes = 30.0)
            )
        ))
        val llmResponse = """{"insights":["Active day"],"recommendations":["Add meals"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(15L to makeAnalysis(15L, exerciseJson))
            ),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        val payload = (result as DailySummaryResult.Success).payload
        assertNull(payload.balance?.macroBalance)
        assertEquals("No meals logged", payload.balance?.timing)
        assertNull(payload.balance?.variety)
    }

    @Test
    fun `generateSummary two meals hits consider consistent meal timing`() = runTest {
        // mealCount == 2 → timing = "Consider more consistent meal timing"
        // variety = "Consider adding more variety" (else branch)
        val meal1 = makeCompletedEntry(16, EntryType.MEAL)
        val meal2 = makeCompletedEntry(17, EntryType.MEAL)
        val mealJsonLow = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(totalCalories = 600.0, protein = 30.0, carbohydrates = 70.0, fat = 20.0, fiber = 8.0)
            )
        ))
        val llmResponse = """{"insights":["Two meals"],"recommendations":["Add a meal"]}"""

        val fakeSummaryRepo = FakeDailySummaryRepository()
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(meal1, meal2)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(
                    16L to makeAnalysis(16L, mealJsonLow),
                    17L to makeAnalysis(17L, mealJsonLow)
                )
            ),
            dailySummaryRepository = fakeSummaryRepo,
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        val balance = (result as DailySummaryResult.Success).payload.balance
        assertEquals("Consider more consistent meal timing", balance?.timing)
        assertEquals("Consider adding more variety", balance?.variety)
    }

    @Test
    fun `generateSummary three meals with balanced nutrition hits all positive balance branches`() = runTest {
        // 3 meals × 700 kcal = 2100 kcal, protein 120g, fiber 30g → "Balanced"
        // mealCount >= 3 → timing = "Well-distributed meals", variety = "Good meal variety"
        val meals = (18L..20L).map { makeCompletedEntry(it, EntryType.MEAL) }
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(
                    totalCalories = 700.0, protein = 40.0, carbohydrates = 80.0, fat = 22.0, fiber = 10.0
                )
            )
        ))
        val llmResponse = """{"insights":["Balanced"],"recommendations":["Keep up"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(meals),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                meals.associate { it.entryId to makeAnalysis(it.entryId, mealJson) }
            ),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        val balance = (result as DailySummaryResult.Success).payload.balance
        assertEquals("Well-distributed meals", balance?.timing)
        assertEquals("Good meal variety", balance?.variety)
        assertEquals("Balanced", balance?.overall)
    }

    @Test
    fun `generateSummary with low calorie intake hits low-calorie overall balance`() = runTest {
        // calories < 1200 → overall = "Low calorie intake"
        val entry = makeCompletedEntry(21, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(
                    totalCalories = 800.0, protein = 60.0, carbohydrates = 60.0, fat = 20.0, fiber = 25.0
                )
            )
        ))
        val llmResponse = """{"insights":["Low cal"],"recommendations":["Eat more"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(21L to makeAnalysis(21L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        assertEquals("Low calorie intake", (result as DailySummaryResult.Success).payload.balance?.overall)
    }

    @Test
    fun `generateSummary with high calorie intake hits high-calorie overall balance`() = runTest {
        // calories > 3000 → overall = "High calorie intake"
        val entry = makeCompletedEntry(22, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(
                    totalCalories = 3200.0, protein = 150.0, carbohydrates = 350.0, fat = 80.0, fiber = 30.0
                )
            )
        ))
        val llmResponse = """{"insights":["High cal"],"recommendations":["Reduce portions"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(22L to makeAnalysis(22L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        assertEquals("High calorie intake", (result as DailySummaryResult.Success).payload.balance?.overall)
    }

    @Test
    fun `generateSummary with low protein hits low-protein overall balance`() = runTest {
        // protein < 50, calories in [1200, 3000] → overall = "Low protein"
        val entry = makeCompletedEntry(23, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(
                    totalCalories = 1800.0, protein = 30.0, carbohydrates = 200.0, fat = 70.0, fiber = 25.0
                )
            )
        ))
        val llmResponse = """{"insights":["Low protein"],"recommendations":["Add protein"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(23L to makeAnalysis(23L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        assertEquals("Low protein", (result as DailySummaryResult.Success).payload.balance?.overall)
    }

    @Test
    fun `generateSummary with low fiber hits low-fiber overall balance`() = runTest {
        // protein >= 50, calories in [1200, 3000], fiber < 20 → overall = "Low fiber"
        val entry = makeCompletedEntry(24, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(
                    totalCalories = 1800.0, protein = 80.0, carbohydrates = 200.0, fat = 60.0, fiber = 10.0
                )
            )
        ))
        val llmResponse = """{"insights":["Low fiber"],"recommendations":["Add fiber"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(24L to makeAnalysis(24L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        assertEquals("Low fiber", (result as DailySummaryResult.Success).payload.balance?.overall)
    }

    // ── Detail-line edge cases ───────────────────────────────────────────────

    @Test
    fun `generateSummary buildSleepDetailLine with null duration and non-null userNotes`() = runTest {
        val entry = makeCompletedEntry(25, EntryType.SLEEP, userNotes = "Rough night")
        val sleepJson = makeUnifiedJson(UnifiedAnalysisResult(
            sleepAnalysis = SleepAnalysisResult(
                durationHours = null,
                qualitySummary = null,
                environmentNotes = emptyList()
            )
        ))
        val llmResponse = """{"insights":["Sleep logged"],"recommendations":["Track more"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(25L to makeAnalysis(25L, sleepJson))
            ),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))
        assertIs<DailySummaryResult.Success>(result)
    }

    @Test
    fun `generateSummary buildOtherDetailLine with null summary and empty tags`() = runTest {
        val entry = makeCompletedEntry(26, EntryType.OTHER) // no userNotes
        val otherJson = makeUnifiedJson(UnifiedAnalysisResult(
            otherAnalysis = OtherAnalysisResult(summary = null, tags = emptyList())
        ))
        val llmResponse = """{"insights":["Other logged"],"recommendations":["Keep tracking"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(26L to makeAnalysis(26L, otherJson))
            ),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))
        assertIs<DailySummaryResult.Success>(result)
    }

    @Test
    fun `generateSummary buildExerciseDetailLine with null activityType and distance without unit`() = runTest {
        val entry = makeCompletedEntry(27, EntryType.EXERCISE) // no userNotes
        val exerciseJson = makeUnifiedJson(UnifiedAnalysisResult(
            exerciseAnalysis = ExerciseAnalysisResult(
                activityType = null, // null → activityType?.let not called
                metrics = ExerciseMetrics(
                    distance = 5.0,
                    distanceUnit = null // distance without unit → fallback "$d"
                ),
                insights = null // null → insights?.summary?.let not called
            )
        ))
        val llmResponse = """{"insights":["Exercise logged"],"recommendations":["Track more"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(27L to makeAnalysis(27L, exerciseJson))
            ),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))
        assertIs<DailySummaryResult.Success>(result)
    }

    @Test
    fun `generateSummary buildExerciseDetailLine with all-null metrics produces empty parts list`() = runTest {
        val entry = makeCompletedEntry(28, EntryType.EXERCISE)
        val exerciseJson = makeUnifiedJson(UnifiedAnalysisResult(
            exerciseAnalysis = ExerciseAnalysisResult(
                activityType = "Walk", // non-null so activityType branch is covered
                metrics = ExerciseMetrics(
                    durationMinutes = null,
                    distance = null,
                    calories = null,
                    averageHeartRate = null
                ),
                insights = ExerciseInsights(summary = null) // non-null insights, null summary
            )
        ))
        val llmResponse = """{"insights":["Walk logged"],"recommendations":["Keep it up"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(28L to makeAnalysis(28L, exerciseJson))
            ),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))
        assertIs<DailySummaryResult.Success>(result)
    }

    // ── Exception and fallback paths ─────────────────────────────────────────

    @Test
    fun `generateSummary handles weight repository exception gracefully`() = runTest {
        val entry = makeCompletedEntry(29, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(totalCalories = 1800.0, protein = 80.0, carbohydrates = 200.0, fat = 60.0)
            )
        ))
        val throwingWeightRepo = object : WeightHistoryRepository {
            override suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<WeightRecord> =
                throw RuntimeException("DB error")
            override suspend fun addWeightRecord(record: WeightRecord) = 1L
            override suspend fun getLatestWeightRecord() = null
            override suspend fun getAllWeightRecords() = emptyList<WeightRecord>()
            override suspend fun deleteWeightRecord(recordId: Long) {}
            override suspend fun nullifyRelatedEntryId(entryId: Long) {}
            override suspend fun upsertWeightRecord(record: WeightRecord) {}
        }
        val llmResponse = """{"insights":["Summary"],"recommendations":["Keep going"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(29L to makeAnalysis(29L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = throwingWeightRepo
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))
        assertIs<DailySummaryResult.Success>(result)
    }

    @Test
    fun `generateSummary text fallback with no patterns uses content and default recommendation`() = runTest {
        // Response has no JSON and no insight/recommendation/bullet patterns
        // → highlights.isEmpty() fallback: content.take(200)
        // → recommendations.isEmpty() fallback: "Continue your healthy habits!"
        val entry = makeCompletedEntry(30, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(totalCalories = 1800.0, protein = 80.0)
            )
        ))
        val unstructuredResponse = "Health data unavailable at this time."

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(30L to makeAnalysis(30L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = unstructuredResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))
        assertIs<DailySummaryResult.Success>(result)
        val payload = (result as DailySummaryResult.Success).payload
        assertTrue(payload.highlights.isNotEmpty())
        assertTrue(payload.recommendations.isNotEmpty())
    }

    @Test
    fun `generateSummary with non-MEAL entry that has legacy-parse failure is handled gracefully`() = runTest {
        // Entry is EXERCISE type; analysis JSON is not valid UnifiedAnalysisResult,
        // and the legacy fallback only applies to MEAL → exception logged, no detail line added
        val entry = makeCompletedEntry(31, EntryType.EXERCISE)
        val badJson = """{"invalid": "json structure"}"""
        val llmResponse = """{"insights":["Exercise day"],"recommendations":["Rest tomorrow"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(31L to makeAnalysis(31L, badJson))
            ),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))
        assertIs<DailySummaryResult.Success>(result)
    }
}
