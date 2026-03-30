package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.data.repository.NutritionalProfileRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import com.wellnesswingman.domain.llm.LlmAnalysisResult
import com.wellnesswingman.domain.llm.LlmClient
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.LlmDiagnostics
import com.wellnesswingman.domain.llm.ToolExecutor
import com.wellnesswingman.domain.llm.ToolRegistry
import com.wellnesswingman.platform.FileSystem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnalysisOrchestratorTest {

    @Test
    fun `processEntry passes tool definitions and executor to llm client`() = runTest {
        val trackedEntryRepository = FakeTrackedEntryRepository()
        val entryAnalysisRepository = FakeEntryAnalysisRepository()
        val toolRegistry = ToolRegistry(
            trackedEntryRepository = trackedEntryRepository,
            entryAnalysisRepository = entryAnalysisRepository,
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )
        val llmClient = mockk<LlmClient>()
        val llmClientFactory = mockk<LlmClientFactory>()
        val fileSystem = mockk<FileSystem>()
        val capturedTools = mutableListOf<com.wellnesswingman.data.model.llm.ToolDefinition>()
        var capturedExecutor: ToolExecutor? = null

        every { llmClientFactory.hasCurrentApiKey() } returns true
        every { llmClientFactory.createForCurrentProvider() } returns llmClient
        coEvery {
            llmClient.generateCompletion(
                prompt = any(),
                jsonSchema = null,
                tools = any(),
                toolExecutor = any()
            )
        } answers {
            capturedTools += thirdArg<List<com.wellnesswingman.data.model.llm.ToolDefinition>>()
            capturedExecutor = arg(3)
            LlmAnalysisResult(
                content = """
                    {
                      "schemaVersion": "1.0",
                      "entryType": "Other",
                      "confidence": 0.9,
                      "detectedWeight": null,
                      "mealAnalysis": null,
                      "exerciseAnalysis": null,
                      "sleepAnalysis": null,
                      "otherAnalysis": {
                        "summary": "Reviewed"
                      },
                      "warnings": []
                    }
                """.trimIndent(),
                diagnostics = LlmDiagnostics(model = "gemini-test")
            )
        }
        every { fileSystem.exists(any()) } returns false

        val orchestrator = AnalysisOrchestrator(
            trackedEntryRepository = trackedEntryRepository,
            entryAnalysisRepository = entryAnalysisRepository,
            llmClientFactory = llmClientFactory,
            toolRegistry = toolRegistry,
            fileSystem = fileSystem,
            appSettingsRepository = FakeAppSettingsRepository()
        )

        val entry = TrackedEntry(
            entryId = 7L,
            entryType = EntryType.UNKNOWN,
            capturedAt = Clock.System.now(),
            processingStatus = ProcessingStatus.PENDING,
            userNotes = "notes"
        )

        val result = orchestrator.processEntry(entry)

        val success = assertIs<AnalysisInvocationResult.Success>(result)
        assertEquals(ProcessingStatus.COMPLETED, trackedEntryRepository.statusById[7L])
        assertEquals(EntryType.OTHER, trackedEntryRepository.typeById[7L])
        assertEquals(toolRegistry.definitions().map { it.name }, capturedTools.map { it.name })
        assertNotNull(capturedExecutor)

        val toolResult = capturedExecutor!!.invoke(
            com.wellnesswingman.data.model.llm.ToolCall(name = "get_user_profile")
        )
        assertTrue(toolResult.content.toString().contains("currentWeight"))
        assertEquals(1, entryAnalysisRepository.inserted.size)
        assertEquals(success.analysis, entryAnalysisRepository.inserted.first())
    }

    @Test
    fun `processEntry rethrows cancellation instead of marking entry failed`() = runTest {
        val trackedEntryRepository = FakeTrackedEntryRepository()
        val llmClient = mockk<LlmClient>()
        val llmClientFactory = mockk<LlmClientFactory>()

        every { llmClientFactory.hasCurrentApiKey() } returns true
        every { llmClientFactory.createForCurrentProvider() } returns llmClient
        coEvery {
            llmClient.generateCompletion(
                prompt = any(),
                jsonSchema = null,
                tools = any(),
                toolExecutor = any()
            )
        } throws CancellationException("cancelled")

        val orchestrator = AnalysisOrchestrator(
            trackedEntryRepository = trackedEntryRepository,
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            llmClientFactory = llmClientFactory,
            toolRegistry = ToolRegistry(
                trackedEntryRepository = trackedEntryRepository,
                entryAnalysisRepository = FakeEntryAnalysisRepository(),
                weightHistoryRepository = FakeWeightHistoryRepository(),
                appSettingsRepository = FakeAppSettingsRepository(),
                nutritionalProfileRepository = FakeNutritionalProfileRepository()
            ),
            fileSystem = mockk<FileSystem>(),
            appSettingsRepository = FakeAppSettingsRepository()
        )

        val entry = TrackedEntry(
            entryId = 9L,
            entryType = EntryType.UNKNOWN,
            capturedAt = Clock.System.now(),
            processingStatus = ProcessingStatus.PENDING
        )

        assertFailsWith<CancellationException> {
            orchestrator.processEntry(entry)
        }
        assertEquals(ProcessingStatus.PROCESSING, trackedEntryRepository.statusById[9L])
    }

    private class FakeTrackedEntryRepository : TrackedEntryRepository {
        val statusById = linkedMapOf<Long, ProcessingStatus>()
        val typeById = linkedMapOf<Long, EntryType>()

        override suspend fun getAllEntries(): List<TrackedEntry> = emptyList()
        override suspend fun getRecentEntries(limit: Int, entryType: EntryType?): List<TrackedEntry> = emptyList()
        override fun observeAllEntries(): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntryById(id: Long): TrackedEntry? = null
        override suspend fun getEntryByExternalId(externalId: String): TrackedEntry? = null
        override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long): List<TrackedEntry> = emptyList()
        override suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry> = emptyList()
        override fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntriesForWeek(startMillis: Long, endMillis: Long): List<TrackedEntry> = emptyList()
        override suspend fun getEntriesForMonth(startMillis: Long, endMillis: Long): List<TrackedEntry> = emptyList()
        override suspend fun getEntriesByStatus(status: ProcessingStatus): List<TrackedEntry> = emptyList()
        override suspend fun getPendingEntries(): List<TrackedEntry> = emptyList()
        override suspend fun insertEntry(entry: TrackedEntry): Long = 0L
        override suspend fun updateEntryStatus(id: Long, status: ProcessingStatus) {
            statusById[id] = status
        }
        override suspend fun updateEntryType(id: Long, entryType: EntryType) {
            typeById[id] = entryType
        }
        override suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int) {}
        override suspend fun updateUserNotes(id: Long, notes: String?) {}
        override suspend fun deleteEntry(id: Long) {}
        override suspend fun upsertEntry(entry: TrackedEntry) {}
    }

    private class FakeEntryAnalysisRepository : EntryAnalysisRepository {
        val inserted = mutableListOf<EntryAnalysis>()

        override suspend fun getAllAnalyses(): List<EntryAnalysis> = inserted
        override suspend fun getAnalysisById(id: Long): EntryAnalysis? = inserted.find { it.analysisId == id }
        override suspend fun getAnalysisByExternalId(externalId: String): EntryAnalysis? = null
        override suspend fun getAnalysesForEntry(entryId: Long): List<EntryAnalysis> = inserted.filter { it.entryId == entryId }
        override suspend fun getLatestAnalysisForEntry(entryId: Long): EntryAnalysis? = inserted.lastOrNull { it.entryId == entryId }
        override suspend fun insertAnalysis(analysis: EntryAnalysis): Long {
            inserted += analysis.copy(analysisId = 42L)
            return 42L
        }
        override suspend fun updateAnalysis(id: Long, insightsJson: String, schemaVersion: String) {}
        override suspend fun deleteAnalysis(id: Long) {}
        override suspend fun deleteAnalysesForEntry(entryId: Long) {}
        override suspend fun upsertAnalysis(analysis: EntryAnalysis) {}
    }

    private class FakeWeightHistoryRepository : WeightHistoryRepository {
        override suspend fun addWeightRecord(record: com.wellnesswingman.data.model.WeightRecord): Long = 0L
        override suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<com.wellnesswingman.data.model.WeightRecord> = emptyList()
        override suspend fun getLatestWeightRecord(): com.wellnesswingman.data.model.WeightRecord? = null
        override suspend fun getAllWeightRecords(): List<com.wellnesswingman.data.model.WeightRecord> = emptyList()
        override suspend fun deleteWeightRecord(recordId: Long) {}
        override suspend fun nullifyRelatedEntryId(entryId: Long) {}
        override suspend fun upsertWeightRecord(record: com.wellnesswingman.data.model.WeightRecord) {}
    }

    private class FakeNutritionalProfileRepository : NutritionalProfileRepository {
        override fun getAllAsFlow(): Flow<List<NutritionalProfile>> = emptyFlow()
        override suspend fun getAll(): List<NutritionalProfile> = emptyList()
        override suspend fun getById(profileId: Long): NutritionalProfile? = null
        override suspend fun getByExternalId(externalId: String): NutritionalProfile? = null
        override suspend fun searchByName(query: String, limit: Int): List<NutritionalProfile> = emptyList()
        override suspend fun insert(profile: NutritionalProfile): Long = 0L
        override suspend fun update(profile: NutritionalProfile) {}
        override suspend fun delete(profileId: Long) {}
        override suspend fun upsert(profile: NutritionalProfile) {}
    }

    private class FakeAppSettingsRepository : AppSettingsRepository {
        override fun getApiKey(provider: LlmProvider): String? = null
        override fun setApiKey(provider: LlmProvider, apiKey: String) {}
        override fun removeApiKey(provider: LlmProvider) {}
        override fun getSelectedProvider(): LlmProvider = LlmProvider.GEMINI
        override fun setSelectedProvider(provider: LlmProvider) {}
        override fun getModel(provider: LlmProvider): String? = null
        override fun setModel(provider: LlmProvider, model: String) {}
        override fun clear() {}
        override fun getHeight(): Double? = 180.0
        override fun setHeight(height: Double) {}
        override fun getHeightUnit(): String = "cm"
        override fun setHeightUnit(unit: String) {}
        override fun getSex(): String? = "male"
        override fun setSex(sex: String) {}
        override fun getCurrentWeight(): Double? = 80.0
        override fun setCurrentWeight(weight: Double) {}
        override fun getWeightUnit(): String = "kg"
        override fun setWeightUnit(unit: String) {}
        override fun getDateOfBirth(): String? = "1990-01-01"
        override fun setDateOfBirth(dob: String) {}
        override fun getActivityLevel(): String? = "moderate"
        override fun setActivityLevel(level: String) {}
        override fun clearHeight() {}
        override fun clearCurrentWeight() {}
        override fun clearProfileData() {}
        override fun getImageRetentionThresholdDays(): Int = 30
        override fun setImageRetentionThresholdDays(days: Int) {}
        override fun getPolarAccessToken(): String? = null
        override fun setPolarAccessToken(token: String) {}
        override fun getPolarRefreshToken(): String? = null
        override fun setPolarRefreshToken(token: String) {}
        override fun getPolarTokenExpiresAt(): Long = 0L
        override fun setPolarTokenExpiresAt(expiresAt: Long) {}
        override fun getPolarUserId(): String? = null
        override fun setPolarUserId(userId: String) {}
        override fun getPendingOAuthState(): String? = null
        override fun setPendingOAuthState(state: String) {}
        override fun getPendingOAuthSessionId(): String? = null
        override fun setPendingOAuthSessionId(sessionId: String) {}
        override fun clearPendingOAuthSession() {}
        override fun clearPolarTokens() {}
        override fun isPolarConnected(): Boolean = false
    }
}
