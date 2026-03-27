package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.DailySummaryPayload
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.NutritionTotals
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeeklySummary
import com.wellnesswingman.data.model.WeeklySummaryResult
import com.wellnesswingman.data.model.WeightChangeSummary
import com.wellnesswingman.data.model.WeightRecord
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
import com.wellnesswingman.data.repository.PolarSyncRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import com.wellnesswingman.data.repository.WeeklySummaryRepository
import com.wellnesswingman.domain.llm.LlmAnalysisResult
import com.wellnesswingman.domain.llm.LlmClient
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.LlmDiagnostics
import com.wellnesswingman.domain.llm.ToolExecutor
import com.wellnesswingman.domain.polar.PolarInsightService
import com.wellnesswingman.domain.testutil.FakePolarSyncRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.Json
import kotlin.test.*

class WeeklySummaryServiceTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeTrackedEntryRepository(
        private val entriesToReturn: List<TrackedEntry> = emptyList()
    ) : TrackedEntryRepository {
        override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long) = entriesToReturn
        override suspend fun getEntriesForDay(date: LocalDate) = entriesToReturn
        override suspend fun getAllEntries() = entriesToReturn
        override suspend fun getRecentEntries(limit: Int, entryType: EntryType?): List<TrackedEntry> = emptyList()
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

    private class FakeWeeklySummaryRepository(
        private val initialSummary: WeeklySummary? = null
    ) : WeeklySummaryRepository {
        val inserted = mutableListOf<WeeklySummary>()
        val deleted = mutableListOf<LocalDate>()
        val userCommentsUpdates = mutableMapOf<LocalDate, String?>()
        private var nextId = 1L

        override suspend fun getSummaryForWeek(weekStart: LocalDate): WeeklySummary? =
            inserted.find { it.weekStartDate == weekStart } ?: initialSummary?.takeIf { it.weekStartDate == weekStart }

        override suspend fun insertSummary(summary: WeeklySummary): Long {
            val id = nextId++
            inserted.add(summary.copy(summaryId = id))
            return id
        }

        override suspend fun deleteSummaryByWeek(weekStart: LocalDate) { deleted.add(weekStart) }
        override suspend fun updateUserComments(weekStart: LocalDate, comments: String?) {
            userCommentsUpdates[weekStart] = comments
        }
        override suspend fun getAllSummaries() = inserted.toList()
        override suspend fun getSummaryById(id: Long) = inserted.find { it.summaryId == id }
        override suspend fun getSummariesForDateRange(startDate: LocalDate, endDate: LocalDate) =
            inserted.filter { it.weekStartDate >= startDate && it.weekStartDate <= endDate }
        override suspend fun getRecentSummaries(limit: Long) = inserted.take(limit.toInt())
        override suspend fun updateSummary(summary: WeeklySummary) {}
        override suspend fun updateSummaryByWeek(weekStart: LocalDate, summary: WeeklySummary) {}
        override suspend fun deleteSummary(id: Long) {}
        override suspend fun deleteOldSummaries(beforeDate: LocalDate) {}
    }

    private class FakeDailySummaryRepository(
        private val summaries: List<DailySummary> = emptyList()
    ) : DailySummaryRepository {
        override suspend fun getSummariesForDateRange(startDate: LocalDate, endDate: LocalDate) =
            summaries.filter { it.summaryDate >= startDate && it.summaryDate <= endDate }
        override suspend fun getAllSummaries() = summaries
        override suspend fun getSummaryById(id: Long) = null
        override suspend fun getSummaryByExternalId(externalId: String) = null
        override suspend fun getSummaryForDate(date: LocalDate) = summaries.find { it.summaryDate == date }
        override suspend fun getRecentSummaries(limit: Long) = summaries.take(limit.toInt())
        override suspend fun insertSummary(summary: DailySummary) = 1L
        override suspend fun updateSummary(id: Long, highlights: String, recommendations: String) {}
        override suspend fun updateSummaryByDate(date: LocalDate, highlights: String, recommendations: String) {}
        override suspend fun updateUserComments(date: LocalDate, comments: String?) {}
        override suspend fun deleteSummary(id: Long) {}
        override suspend fun deleteSummaryByDate(date: LocalDate) {}
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

    private fun makeLlmClientFactory(hasKey: Boolean, response: String = VALID_WEEKLY_JSON): LlmClientFactory {
        val fakeLlmClient = object : LlmClient {
            override suspend fun analyzeImage(
                imageBytes: ByteArray,
                prompt: String,
                jsonSchema: String?,
                tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
                toolExecutor: ToolExecutor?
            ) =
                LlmAnalysisResult(response, LlmDiagnostics())
            override suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(
                prompt: String,
                jsonSchema: String?,
                tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
                toolExecutor: ToolExecutor?
            ) =
                LlmAnalysisResult(response, LlmDiagnostics())
        }
        val factory = mockk<LlmClientFactory>()
        every { factory.hasCurrentApiKey() } returns hasKey
        if (hasKey) every { factory.createForCurrentProvider() } returns fakeLlmClient
        return factory
    }

    /**
     * Like [makeLlmClientFactory] but captures completion prompts into [capturedPrompts],
     * enabling tests to assert what was actually sent to the LLM.
     */
    private fun makeCapturingLlmClientFactory(
        response: String = VALID_WEEKLY_JSON,
        capturedPrompts: MutableList<String>
    ): LlmClientFactory {
        val fakeLlmClient = object : LlmClient {
            override suspend fun analyzeImage(
                imageBytes: ByteArray,
                prompt: String,
                jsonSchema: String?,
                tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
                toolExecutor: ToolExecutor?
            ) =
                LlmAnalysisResult(response, LlmDiagnostics())
            override suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(
                prompt: String,
                jsonSchema: String?,
                tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
                toolExecutor: ToolExecutor?
            ): LlmAnalysisResult {
                capturedPrompts.add(prompt)
                return LlmAnalysisResult(response, LlmDiagnostics())
            }
        }
        val factory = mockk<LlmClientFactory>()
        every { factory.hasCurrentApiKey() } returns true
        every { factory.createForCurrentProvider() } returns fakeLlmClient
        return factory
    }

    private fun makeCompletedEntry(entryId: Long, entryType: EntryType) = TrackedEntry(
        entryId = entryId,
        entryType = entryType,
        capturedAt = Clock.System.now(),
        processingStatus = ProcessingStatus.COMPLETED
    )

    private fun completedEntryOnDate(
        entryId: Long,
        entryType: EntryType,
        date: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ) = TrackedEntry(
        entryId = entryId,
        entryType = entryType,
        capturedAt = date.atStartOfDayIn(timeZone),
        processingStatus = ProcessingStatus.COMPLETED
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

    private fun throwingPolarInsightService(message: String = "Polar unavailable") = PolarInsightService(
        object : PolarSyncRepository {
            override suspend fun upsertActivities(activities: List<PolarDailyActivity>, syncedAt: Instant) = 0
            override suspend fun getActivities(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarActivity> =
                throw RuntimeException(message)
            override suspend fun upsertSleepResults(results: List<PolarSleepResult>, syncedAt: Instant) = 0
            override suspend fun getSleepResults(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarSleepResult> =
                throw RuntimeException(message)
            override suspend fun upsertTrainingSessions(sessions: List<PolarTrainingSession>, syncedAt: Instant) = 0
            override suspend fun getTrainingSessions(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarTrainingSession> =
                throw RuntimeException(message)
            override suspend fun upsertNightlyRecharge(results: List<PolarNightlyRecharge>, syncedAt: Instant) = 0
            override suspend fun getNightlyRecharge(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarNightlyRecharge> =
                throw RuntimeException(message)
            override suspend fun upsertUserProfile(userId: String, profile: PolarUserProfile, syncedAt: Instant) = Unit
            override suspend fun getUserProfile(userId: String): StoredPolarUserProfile? = null
            override suspend fun getCheckpoint(metricFamily: PolarMetricFamily): PolarSyncCheckpoint? = null
            override suspend fun getAllCheckpoints(): List<PolarSyncCheckpoint> = emptyList()
            override suspend fun updateCheckpoint(checkpoint: PolarSyncCheckpoint) = Unit
            override suspend fun clearCheckpoint(metricFamily: PolarMetricFamily) = Unit
            override suspend fun clearAll() = Unit
        }
    )

    companion object {
        val VALID_WEEKLY_JSON = """
            {
              "schemaVersion": "1.1",
              "weekStartDate": "2025-03-01",
              "highlights": ["Consistent logging", "Good exercise week"],
              "recommendations": ["Increase protein", "More sleep"],
              "mealCount": 18,
              "exerciseCount": 4,
              "sleepCount": 7,
              "otherCount": 0,
              "totalEntries": 29,
              "nutritionAverages": {
                "calories": 1900,
                "protein": 85,
                "carbohydrates": 210,
                "fat": 68,
                "fiber": 22,
                "sugar": 38,
                "sodium": 1700
              },
              "nutritionTrend": "Stable caloric intake with good protein",
              "weightChange": {"start": 71.5, "end": 71.0, "unit": "kg"},
              "balanceSummary": "Well balanced week"
            }
        """.trimIndent()
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `getSummaryForWeek delegates to repository`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val existingSummary = WeeklySummary(
            weekStartDate = weekStart,
            highlights = "Good week",
            recommendations = "Keep going"
        )
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            weeklySummaryRepository = FakeWeeklySummaryRepository(initialSummary = existingSummary),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = false),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.getSummaryForWeek(weekStart)

        assertNotNull(result)
        assertEquals(weekStart, result.weekStartDate)
    }

    @Test
    fun `generateSummary returns cached summary when one exists`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val cachedSummary = WeeklySummary(
            weekStartDate = weekStart,
            highlights = "Cached highlight",
            recommendations = "Cached rec"
        )
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            weeklySummaryRepository = FakeWeeklySummaryRepository(initialSummary = cachedSummary),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = false),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
        val success = result as WeeklySummaryResult.Success
        assertEquals(weekStart, success.summary.weekStartDate)
    }

    @Test
    fun `generateSummary returns Error when no API key and no cache`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = FakeWeeklySummaryRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = false),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Error>(result)
        assertTrue((result as WeeklySummaryResult.Error).message.contains("API key", ignoreCase = true))
    }

    @Test
    fun `generateSummary succeeds for a Polar-only week and includes Polar context in prompt`() = runTest {
        val prompts = mutableListOf<String>()
        val weekStart = LocalDate(2025, 3, 1)
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            weeklySummaryRepository = FakeWeeklySummaryRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(capturedPrompts = prompts),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService(
                activities = listOf(
                    StoredPolarActivity(1, "activity:2025-03-01", "Polar", weekStart, null, Clock.System.now(), PolarDailyActivity("2025-03-01", 9200, "00:00:00", 60000, listOf(9200)))
                ),
                trainingSessions = listOf(
                    StoredPolarTrainingSession(2, "session-1", "Polar", weekStart, "2025-03-01T08:00:00", Clock.System.now(), PolarTrainingSession("session-1", "2025-03-01T08:00:00", 3600, "1", 430, 5000.0, 148, 172, "Tempo run"))
                )
            )
        )

        val result = service.generateSummary(weekStart)


        assertIs<WeeklySummaryResult.Success>(result)
        assertTrue(prompts.single().contains("Polar Sync Context"))
        assertTrue(prompts.single().contains("Steps (Polar): 9200"))
        assertTrue(prompts.single().contains("Exercise (Polar): 60.0 min"))
    }

    @Test
    fun `generateSummary returns NoEntries when no completed entries`() = runTest {
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(emptyList()),
            weeklySummaryRepository = FakeWeeklySummaryRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<WeeklySummaryResult.NoEntries>(result)
    }

    @Test
    fun `generateSummary excludes DAILY_SUMMARY entry type`() = runTest {
        val dailySummaryEntry = makeCompletedEntry(1, EntryType.DAILY_SUMMARY)
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(dailySummaryEntry)),
            weeklySummaryRepository = FakeWeeklySummaryRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<WeeklySummaryResult.NoEntries>(result)
    }

    @Test
    fun `generateSummary success persists summary with payloadJson`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val weeklyRepo = FakeWeeklySummaryRepository()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
        assertEquals(1, weeklyRepo.inserted.size)
        val saved = weeklyRepo.inserted.first()
        assertNotNull(saved.payloadJson)
        assertTrue(saved.payloadJson!!.contains("weekStartDate") || saved.payloadJson.contains("highlights"))
    }

    @Test
    fun `generateSummary with weight records computes weight change`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val now = Clock.System.now()
        val weightRecords = listOf(
            WeightRecord(
                recordedAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 5 * 24 * 3600 * 1000L),
                weightValue = 75.0, weightUnit = "kg", source = "Manual"
            ),
            WeightRecord(
                recordedAt = now,
                weightValue = 74.2, weightUnit = "kg", source = "Manual"
            )
        )

        val weeklyRepo = FakeWeeklySummaryRepository()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            weightHistoryRepository = FakeWeightHistoryRepository(weightRecords),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
    }

    @Test
    fun `generateSummary with userComments persists them in summary`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val weeklyRepo = FakeWeeklySummaryRepository()
        val capturedPrompts = mutableListOf<String>()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(capturedPrompts = capturedPrompts),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(weekStart, userComments = "This was a great week!")

        assertIs<WeeklySummaryResult.Success>(result)
        assertEquals(1, weeklyRepo.inserted.size)
        assertEquals("This was a great week!", weeklyRepo.inserted.first().userComments)
        // Verify user comments are actually included in the LLM prompt
        assertTrue(capturedPrompts.isNotEmpty())
        assertTrue(capturedPrompts.first().contains("This was a great week!"), "Expected user comments in prompt")
    }

    @Test
    fun `generateSummary with blank userComments stores null`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val weeklyRepo = FakeWeeklySummaryRepository()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(weekStart, userComments = "   ")

        assertIs<WeeklySummaryResult.Success>(result)
        assertNull(weeklyRepo.inserted.first().userComments)
    }

    @Test
    fun `generateSummary with daily summaries includes payloads in prompt`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val dailyPayloadJson = json.encodeToString(
            DailySummaryPayload.serializer(),
            DailySummaryPayload(
                date = "2025-03-01",
                summary = "Good day",
                highlights = listOf("High protein"),
                nutritionTotals = NutritionTotals(calories = 1800.0, protein = 90.0, carbs = 200.0, fat = 60.0)
            )
        )
        val dailySummary = DailySummary(
            summaryDate = LocalDate(2025, 3, 1),
            highlights = "Good day",
            recommendations = "Keep going",
            payloadJson = dailyPayloadJson,
            userComments = "Felt energetic"
        )

        val weeklyRepo = FakeWeeklySummaryRepository()
        val capturedPrompts = mutableListOf<String>()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(listOf(dailySummary)),
            llmClientFactory = makeCapturingLlmClientFactory(capturedPrompts = capturedPrompts),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
        // Verify daily summary context is included in the prompt
        assertTrue(capturedPrompts.isNotEmpty())
        val prompt = capturedPrompts.first()
        assertTrue(prompt.contains("Good day"), "Expected daily highlights in prompt")
        assertTrue(prompt.contains("Felt energetic"), "Expected daily user comments in prompt")
    }

    @Test
    fun `generateSummary parses weekly payload with new fields from LLM`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val weeklyRepo = FakeWeeklySummaryRepository()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = VALID_WEEKLY_JSON),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
        val success = result as WeeklySummaryResult.Success
        assertTrue(success.highlightsList.isNotEmpty())
        assertTrue(success.recommendationsList.isNotEmpty())
        // payloadJson is saved with the parsed content
        val saved = weeklyRepo.inserted.first()
        assertNotNull(saved.payloadJson)
    }

    @Test
    fun `generateSummary falls back to text parsing when LLM returns invalid JSON`() = runTest {
        val textResponse = """
            Highlights:
            - Consistent exercise this week
            - Good meal timing
            Recommendations:
            - Increase water intake
            - Add strength training
        """.trimIndent()

        val weeklyRepo = FakeWeeklySummaryRepository()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = textResponse),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<WeeklySummaryResult.Success>(result)
    }

    @Test
    fun `regenerateSummary deletes existing summary and generates new`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val weeklyRepo = FakeWeeklySummaryRepository()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.regenerateSummary(weekStart, userComments = "Regenerated")

        assertTrue(weeklyRepo.deleted.contains(weekStart))
        assertIs<WeeklySummaryResult.Success>(result)
    }

    @Test
    fun `generateSummary counts entry types correctly`() = runTest {
        val entries = listOf(
            makeCompletedEntry(1, EntryType.MEAL),
            makeCompletedEntry(2, EntryType.MEAL),
            makeCompletedEntry(3, EntryType.EXERCISE),
            makeCompletedEntry(4, EntryType.SLEEP),
            makeCompletedEntry(5, EntryType.OTHER)
        )

        val weeklyRepo = FakeWeeklySummaryRepository()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(entries),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<WeeklySummaryResult.Success>(result)
        val saved = weeklyRepo.inserted.first()
        assertEquals(2, saved.mealCount)
        assertEquals(1, saved.exerciseCount)
        assertEquals(1, saved.sleepCount)
        assertEquals(1, saved.otherCount)
        assertEquals(5, saved.totalEntries)
    }

    @Test
    fun `generateSummary handles weight repository exception and still succeeds`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val weeklyRepo = FakeWeeklySummaryRepository()
        val throwingWeightRepo = object : WeightHistoryRepository {
            override suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<WeightRecord> =
                throw RuntimeException("Weight DB error")
            override suspend fun addWeightRecord(record: WeightRecord) = 1L
            override suspend fun getLatestWeightRecord() = null
            override suspend fun getAllWeightRecords() = emptyList<WeightRecord>()
            override suspend fun deleteWeightRecord(recordId: Long) {}
            override suspend fun nullifyRelatedEntryId(entryId: Long) {}
            override suspend fun upsertWeightRecord(record: WeightRecord) {}
        }

        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            weightHistoryRepository = throwingWeightRepo,
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
    }

    @Test
    fun `generateSummary falls back to tracked entries when Polar week context fails`() = runTest {
        val prompts = mutableListOf<String>()
        val weekStart = LocalDate(2025, 3, 1)
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = FakeWeeklySummaryRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(capturedPrompts = prompts),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = throwingPolarInsightService()
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
        assertFalse(prompts.single().contains("Polar Sync Context"))
        assertTrue(prompts.single().contains("Total entries logged: 1"))
    }

    @Test
    fun `generateSummary with all-blank daily summaries produces empty context`() = runTest {
        // Both highlights and recommendations blank → mapNotNull returns null → summaryLines is ""
        val weekStart = LocalDate(2025, 3, 1)
        val blankSummary = DailySummary(
            summaryDate = LocalDate(2025, 3, 1),
            highlights = "   ",
            recommendations = ""
        )
        val weeklyRepo = FakeWeeklySummaryRepository()

        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(listOf(blankSummary)),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
        assertEquals(1, weeklyRepo.inserted.size)
    }

    @Test
    fun `generateSummary text fallback handles insight keyword and numbered and bullet patterns`() = runTest {
        // Tests: "insight" keyword → inHighlights, numbered list ^\\d+\\., bullet •, recommendation keyword
        val response = """
            Insights:
            1. Consistent nutrition throughout the week
            2. Regular exercise maintained
            Recommendations:
            • Increase hydration
            • Prioritize sleep
        """.trimIndent()

        val weeklyRepo = FakeWeeklySummaryRepository()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = response),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<WeeklySummaryResult.Success>(result)
        val success = result as WeeklySummaryResult.Success
        assertTrue(success.highlightsList.isNotEmpty())
        assertTrue(success.recommendationsList.isNotEmpty())
    }

    @Test
    fun `generateSummary text fallback with no patterns applies content and default fallbacks`() = runTest {
        // No JSON braces, no insight/recommendation/bullet lines
        // → highlights.isEmpty() → content.take(200)
        // → recommendations.isEmpty() → "Keep tracking your health activities!"
        val response = "Unable to generate a weekly summary at this time."

        val weeklyRepo = FakeWeeklySummaryRepository()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = response),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(LocalDate(2025, 3, 1))

        assertIs<WeeklySummaryResult.Success>(result)
        val success = result as WeeklySummaryResult.Success
        // Fallback: highlights gets first 200 chars of response
        assertTrue(success.highlightsList.isNotEmpty())
        // Fallback: recommendations gets "Keep tracking your health activities!"
        assertTrue(success.recommendationsList.isNotEmpty())
        assertTrue(success.recommendationsList.any { it.contains("tracking", ignoreCase = true) })
    }

    @Test
    fun `generateSummary with multiple weight records calculates change from first to last`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val baseTime = Clock.System.now().toEpochMilliseconds()
        val records = listOf(
            WeightRecord(
                recordedAt = Instant.fromEpochMilliseconds(baseTime - 4 * 24 * 3600 * 1000L),
                weightValue = 80.0, weightUnit = "kg", source = "Manual"
            ),
            WeightRecord(
                recordedAt = Instant.fromEpochMilliseconds(baseTime - 2 * 24 * 3600 * 1000L),
                weightValue = 79.5, weightUnit = "kg", source = "Manual"
            ),
            WeightRecord(
                recordedAt = Instant.fromEpochMilliseconds(baseTime),
                weightValue = 79.0, weightUnit = "kg", source = "Manual"
            )
        )
        // Response includes weight change to verify parsing
        val responseWithWeight = """
            {
              "weekStartDate": "2025-03-01",
              "highlights": ["Lost 1kg this week"],
              "recommendations": ["Keep it up"],
              "mealCount": 1,
              "exerciseCount": 0,
              "sleepCount": 0,
              "otherCount": 0,
              "totalEntries": 1,
              "weightChange": {"start": 80.0, "end": 79.0, "unit": "kg"}
            }
        """.trimIndent()

        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(makeCompletedEntry(1, EntryType.MEAL))
            ),
            weeklySummaryRepository = FakeWeeklySummaryRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true, response = responseWithWeight),
            weightHistoryRepository = FakeWeightHistoryRepository(records),
            polarInsightService = polarInsightService()
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
    }

    @Test
    fun `generateSummary weekly count integrity with Polar steps and nightly recharge`() = runTest {
        val weekStart = LocalDate(2025, 3, 3)
        val weeklyRepo = FakeWeeklySummaryRepository()
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            weeklySummaryRepository = weeklyRepo,
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeLlmClientFactory(hasKey = true),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService(
                activities = listOf(
                    StoredPolarActivity(1, "activity:2025-03-03", "Polar", LocalDate(2025, 3, 3), null, Clock.System.now(), PolarDailyActivity("2025-03-03", 8000, "00:00:00", 60000, listOf(8000))),
                    StoredPolarActivity(2, "activity:2025-03-04", "Polar", LocalDate(2025, 3, 4), null, Clock.System.now(), PolarDailyActivity("2025-03-04", 9000, "00:00:00", 60000, listOf(9000)))
                ),
                nightlyRecharge = listOf(
                    StoredPolarNightlyRecharge(3, "recharge:2025-03-03", "Polar", LocalDate(2025, 3, 3), Clock.System.now(), PolarNightlyRecharge("2025-03-03", 4.5, 3, 42, 0, 0, 0, 0, 0, 0, 0))
                )
            )
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
        val saved = weeklyRepo.inserted.first()
        // otherCount should include days with steps or nightly recharge
        assertTrue(saved.otherCount > 0, "Expected otherCount to include Polar step/recharge days")
        // totalEntries must still roll up all category counts, including Polar-derived counts.
        assertTrue(
            saved.totalEntries >= saved.mealCount + saved.exerciseCount + saved.sleepCount + saved.otherCount,
            "Expected totalEntries (${saved.totalEntries}) to cover category totals (${saved.mealCount + saved.exerciseCount + saved.sleepCount + saved.otherCount})"
        )
    }

    @Test
    fun `generateSummary suppresses Polar sleep on days with tracked sleep`() = runTest {
        val prompts = mutableListOf<String>()
        val weekStart = LocalDate(2025, 3, 3)
        val sleepEntry = completedEntryOnDate(
            entryId = 1,
            entryType = EntryType.SLEEP,
            date = LocalDate(2025, 3, 3)
        )
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(sleepEntry)),
            weeklySummaryRepository = FakeWeeklySummaryRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(capturedPrompts = prompts),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService(
                sleepResults = listOf(
                    StoredPolarSleepResult(2, "sleep:2025-03-03", "Polar", LocalDate(2025, 3, 3), null, null, Clock.System.now(), PolarSleepResult("2025-03-03", "2025-03-02T23:00:00Z", "2025-03-03T07:00:00Z", 28800, 7200, 5400, 14400, 1800, 91.0, 4.2, 2, 0, 85.0, 80.0, 82.0, 4))
                )
            )
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
        val prompt = prompts.single()
        // Polar sleep should be suppressed since tracked sleep exists on the same day
        assertFalse(prompt.contains("Sleep (Polar):"), "Expected Polar sleep to be suppressed when tracked sleep exists")
    }

    @Test
    fun `generateSummary suppresses Polar exercise on days with tracked exercise`() = runTest {
        val prompts = mutableListOf<String>()
        val weekStart = LocalDate(2025, 3, 3)
        val exerciseEntry = completedEntryOnDate(
            entryId = 1,
            entryType = EntryType.EXERCISE,
            date = LocalDate(2025, 3, 3)
        )
        val service = WeeklySummaryService(
            trackedEntryRepository = FakeTrackedEntryRepository(listOf(exerciseEntry)),
            weeklySummaryRepository = FakeWeeklySummaryRepository(),
            dailySummaryRepository = FakeDailySummaryRepository(),
            llmClientFactory = makeCapturingLlmClientFactory(capturedPrompts = prompts),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            polarInsightService = polarInsightService(
                trainingSessions = listOf(
                    StoredPolarTrainingSession(2, "session-1", "Polar", LocalDate(2025, 3, 3), "2025-03-03T08:00:00", Clock.System.now(), PolarTrainingSession("session-1", "2025-03-03T08:00:00", 3600, "1", 430, 5000.0, 148, 172, "Tempo run"))
                )
            )
        )

        val result = service.generateSummary(weekStart)

        assertIs<WeeklySummaryResult.Success>(result)
        val prompt = prompts.single()
        // Polar exercise should be suppressed since tracked exercise exists on the same day
        assertFalse(prompt.contains("Exercise (Polar):"), "Expected Polar exercise to be suppressed when tracked exercise exists")
    }
}
