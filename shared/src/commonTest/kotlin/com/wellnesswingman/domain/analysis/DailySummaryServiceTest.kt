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
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import com.wellnesswingman.domain.llm.LlmAnalysisResult
import com.wellnesswingman.domain.llm.LlmClient
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.LlmDiagnostics
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
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.NoEntries>(result)
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
        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(1L to makeAnalysis(1L, makeUnifiedJson(mealAnalysis)))
            ),
            dailySummaryRepository = fakeSummaryRepo,
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
        assertEquals(1, fakeSummaryRepo.inserted.size)
        val saved = fakeSummaryRepo.inserted.first()
        assertNotNull(saved.payloadJson)
        assertTrue(saved.payloadJson!!.contains("2025-03-01"))
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

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(2L to makeAnalysis(2L, makeUnifiedJson(exerciseAnalysis)))
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

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(3L to makeAnalysis(3L, makeUnifiedJson(sleepAnalysis)))
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
    fun `generateSummary with other entry calls buildOtherDetailLine`() = runTest {
        val entry = makeCompletedEntry(4, EntryType.OTHER, userNotes = "Took vitamins")
        val otherAnalysis = UnifiedAnalysisResult(
            otherAnalysis = OtherAnalysisResult(
                summary = "Vitamin supplement",
                tags = listOf("supplement", "wellness")
            )
        )
        val llmResponse = """{"insights":["Good supplement habit"],"recommendations":["Continue vitamins"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(
                mapOf(4L to makeAnalysis(4L, makeUnifiedJson(otherAnalysis)))
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
    fun `generateSummary with userComments succeeds`() = runTest {
        val entry = makeCompletedEntry(5, EntryType.MEAL)
        val mealJson = makeUnifiedJson(UnifiedAnalysisResult(
            mealAnalysis = MealAnalysisResult(
                nutrition = NutritionEstimate(totalCalories = 500.0, protein = 30.0, carbohydrates = 60.0, fat = 10.0)
            )
        ))
        val llmResponse = """{"insights":["Good day"],"recommendations":["Keep going"]}"""

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(5L to makeAnalysis(5L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1), userComments = "Felt energetic today!")

        assertIs<DailySummaryResult.Success>(result)
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

        val service = DailySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(entry)),
            entryAnalysisRepository = FakeEntryAnalysisRepository(mapOf(8L to makeAnalysis(8L, mealJson))),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = llmResponse),
            dailyTotalsCalculator = DailyTotalsCalculator(),
            weightHistoryRepository = FakeWeightHistoryRepository(listOf(weightRecord))
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<DailySummaryResult.Success>(result)
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
}
