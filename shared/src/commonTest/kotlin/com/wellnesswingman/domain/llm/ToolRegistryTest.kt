package com.wellnesswingman.domain.llm

import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeightRecord
import com.wellnesswingman.data.model.llm.ToolCall
import com.wellnesswingman.data.model.llm.ToolDefinition
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.data.repository.NutritionalProfileRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToolRegistryTest {

    @Test
    fun `built in recent entries tool returns latest entry with analysis`() = runTest {
        val now = Clock.System.now()
        val entryAnalysisRepository = FakeEntryAnalysisRepository(
            mapOf(
                2L to EntryAnalysis(
                    entryId = 2L,
                    capturedAt = now,
                    insightsJson = """{"summary":"Tempo run"}"""
                )
            )
        )
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(
                    TrackedEntry(
                        entryId = 1L,
                        entryType = EntryType.MEAL,
                        capturedAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 3_600_000L),
                        processingStatus = ProcessingStatus.COMPLETED,
                        userNotes = "salad"
                    ),
                    TrackedEntry(
                        entryId = 2L,
                        entryType = EntryType.EXERCISE,
                        capturedAt = now,
                        processingStatus = ProcessingStatus.COMPLETED,
                        userNotes = "run",
                        dataPayload = """{"duration":45}"""
                    )
                )
            ),
            entryAnalysisRepository = entryAnalysisRepository,
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(
            ToolCall(
                name = "get_recent_entries",
                arguments = buildJsonObject {
                    put("limit", JsonPrimitive(1))
                }
            )
        )

        assertFalse(result.isError)
        val payload = result.content as JsonObject
        val entries = payload["entries"]!!.toString()
        assertTrue(entries.contains("\"entryId\":2"))
        assertTrue(entries.contains("Tempo run"))
        assertTrue(entries.contains("\"dataPayload\":{\"duration\":45}"))
        assertTrue(entries.contains("\"latestInsightsJson\":{\"summary\":\"Tempo run\"}"))
        assertEquals(1, entryAnalysisRepository.getAllAnalysesCalls)
        assertEquals(0, entryAnalysisRepository.getLatestAnalysisCalls)
        assertEquals(5, registry.definitions().size)
    }

    @Test
    fun `built in recent entries tool ignores invalid entry type filter`() = runTest {
        val now = Clock.System.now()
        val trackedEntryRepository = FakeTrackedEntryRepository(
            listOf(
                TrackedEntry(
                    entryId = 1L,
                    entryType = EntryType.MEAL,
                    capturedAt = now,
                    processingStatus = ProcessingStatus.COMPLETED
                ),
                TrackedEntry(
                    entryId = 2L,
                    entryType = EntryType.EXERCISE,
                    capturedAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 1_000L),
                    processingStatus = ProcessingStatus.COMPLETED
                )
            )
        )
        val registry = ToolRegistry(
            trackedEntryRepository = trackedEntryRepository,
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(
            ToolCall(
                name = "get_recent_entries",
                arguments = buildJsonObject {
                    put("entryType", JsonPrimitive("NotARealType"))
                }
            )
        )

        val payload = assertIs<JsonObject>(result.content)
        assertFalse(result.isError)
        assertEquals(null, trackedEntryRepository.lastRequestedEntryType)
        assertTrue(payload["entries"].toString().contains("\"entryId\":1"))
        assertTrue(payload["entries"].toString().contains("\"entryId\":2"))
    }

    @Test
    fun `execute rethrows cancellation exceptions`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        registry.register(
            definition = ToolDefinition(
                name = "cancel_tool",
                description = "Throws cancellation.",
                parametersSchema = buildJsonObject { put("type", JsonPrimitive("object")) }
            )
        ) {
            throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> {
            registry.execute(ToolCall(name = "cancel_tool"))
        }
    }

    @Test
    fun `unknown tool returns error result`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(ToolCall(name = "missing_tool"))

        assertTrue(result.isError)
        assertEquals("missing_tool", result.name)
        assertTrue(result.content.toString().contains("not registered"))
    }

    @Test
    fun `built in user profile tool returns structured fields`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(ToolCall(name = "get_user_profile"))

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        assertEquals("male", payload["sex"]?.toString()?.trim('"'))
        assertEquals("1990-01-01", payload["dateOfBirth"]?.toString()?.trim('"'))
        assertEquals("moderate", payload["activityLevel"]?.toString()?.trim('"'))
    }

    @Test
    fun `built in weight history tool returns bounded records`() = runTest {
        val now = Clock.System.now()
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(
                records = listOf(
                    WeightRecord(
                        weightRecordId = 1L,
                        weightValue = 80.5,
                        weightUnit = "kg",
                        source = "manual",
                        recordedAt = now
                    )
                )
            ),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(
            ToolCall(
                name = "get_weight_history",
                arguments = buildJsonObject {
                    put("days", JsonPrimitive(365))
                }
            )
        )

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        assertEquals("90", payload["days"]?.toString())
        assertTrue(payload["records"].toString().contains("80.5"))
    }

    @Test
    fun `custom registration dispatches handler`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        registry.register(
            definition = ToolDefinition(
                name = "echo_tool",
                description = "Returns the provided text.",
                parametersSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                }
            )
        ) { call ->
            com.wellnesswingman.data.model.llm.ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("echo", call.arguments["text"] ?: JsonPrimitive(""))
                }
            )
        }

        val result = registry.execute(
            ToolCall(
                name = "echo_tool",
                arguments = buildJsonObject {
                    put("text", JsonPrimitive("hello"))
                }
            )
        )

        assertFalse(result.isError)
        assertEquals("""{"echo":"hello"}""", result.content.toString())
    }

    @Test
    fun `list nutritional profiles tool returns names and aliases without nutrition`() = runTest {
        val now = Clock.System.now()
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(
                listOf(
                    NutritionalProfile(
                        profileId = 7L,
                        externalId = "quest-bar",
                        primaryName = "Quest Protein Bar",
                        aliases = listOf("protein bar", "quest bar"),
                        servingSize = "1 bar",
                        calories = 190.0,
                        protein = 21.0,
                        carbohydrates = 22.0,
                        fat = 7.0,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            )
        )

        val result = registry.execute(
            ToolCall(
                name = "list_nutritional_profiles"
            )
        )

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        val profiles = assertIs<JsonArray>(payload["profiles"])
        assertEquals(1, profiles.size)
        assertTrue(profiles.toString().contains("Quest Protein Bar"))
        assertTrue(profiles.toString().contains("protein bar"))
        assertFalse(profiles.toString().contains("totalCalories"))
    }

    @Test
    fun `list nutritional profiles tool returns empty list when no profiles exist`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(ToolCall(name = "list_nutritional_profiles"))

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        assertEquals("[]", payload["profiles"]?.toString())
    }

    @Test
    fun `get nutritional profiles tool returns exact profiles for requested ids`() = runTest {
        val now = Clock.System.now()
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(
                listOf(
                    NutritionalProfile(
                        profileId = 7L,
                        externalId = "quest-bar",
                        primaryName = "Quest Protein Bar",
                        aliases = listOf("protein bar", "quest bar"),
                        servingSize = "1 bar",
                        calories = 190.0,
                        protein = 21.0,
                        carbohydrates = 22.0,
                        fat = 7.0,
                        createdAt = now,
                        updatedAt = now
                    ),
                    NutritionalProfile(
                        profileId = 9L,
                        externalId = "fairlife-shake",
                        primaryName = "Fairlife Core Power",
                        aliases = listOf("protein shake"),
                        servingSize = "1 bottle",
                        calories = 170.0,
                        protein = 26.0,
                        carbohydrates = 8.0,
                        fat = 4.0,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            )
        )

        val result = registry.execute(
            ToolCall(
                name = "get_nutritional_profiles",
                arguments = buildJsonObject {
                    put(
                        "profileIds",
                        JsonArray(
                            listOf(
                                JsonPrimitive(9),
                                JsonPrimitive(7),
                                JsonPrimitive(999)
                            )
                        )
                    )
                }
            )
        )

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        assertEquals("[9,7,999]", payload["profileIds"]?.toString())
        val profiles = assertIs<JsonArray>(payload["profiles"])
        assertEquals(2, profiles.size)
        assertTrue(profiles.toString().contains("Fairlife Core Power"))
        assertTrue(profiles.toString().contains("Quest Protein Bar"))
        assertTrue(profiles.toString().contains("\"source\":\"exact\""))
    }

    @Test
    fun `get nutritional profiles tool requires profile ids`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(
            ToolCall(
                name = "get_nutritional_profiles"
            )
        )

        assertTrue(result.isError)
        assertEquals("\"profileIds is required\"", result.content.toString())
    }

    @Test
    fun `get nutritional profiles tool returns empty profiles when ids do not exist`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(
            ToolCall(
                name = "get_nutritional_profiles",
                arguments = buildJsonObject {
                    put("profileIds", JsonArray(listOf(JsonPrimitive(9999999999L))))
                }
            )
        )

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        assertEquals("[9999999999]", payload["profileIds"]?.toString())
        assertEquals("[]", payload["profiles"]?.toString())
    }

    @Test
    fun `get nutritional profiles schema marks profile ids as required`() {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val definition = registry.definitions().first { it.name == "get_nutritional_profiles" }
        val required = assertIs<JsonArray>(assertIs<JsonObject>(definition.parametersSchema)["required"])
        assertEquals("[\"profileIds\"]", required.toString())
    }

    private class FakeTrackedEntryRepository(
        private val entries: List<TrackedEntry> = emptyList()
    ) : TrackedEntryRepository {
        var lastRequestedEntryType: EntryType? = null

        override suspend fun getAllEntries(): List<TrackedEntry> = entries
        override suspend fun getRecentEntries(limit: Int, entryType: EntryType?): List<TrackedEntry> =
            entries
                .also { lastRequestedEntryType = entryType }
                .filter { entryType == null || it.entryType == entryType }
                .sortedByDescending { it.capturedAt }
                .take(limit)
        override fun observeAllEntries(): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntryById(id: Long): TrackedEntry? = entries.find { it.entryId == id }
        override suspend fun getEntryByExternalId(externalId: String): TrackedEntry? = null
        override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long): List<TrackedEntry> = entries
        override suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry> = entries
        override fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntriesForWeek(startMillis: Long, endMillis: Long): List<TrackedEntry> = entries
        override suspend fun getEntriesForMonth(startMillis: Long, endMillis: Long): List<TrackedEntry> = entries
        override suspend fun getEntriesByStatus(status: ProcessingStatus): List<TrackedEntry> = entries.filter { it.processingStatus == status }
        override suspend fun getPendingEntries(): List<TrackedEntry> = entries.filter { it.processingStatus == ProcessingStatus.PENDING }
        override suspend fun insertEntry(entry: TrackedEntry): Long = 0L
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
        var getAllAnalysesCalls: Int = 0
        var getLatestAnalysisCalls: Int = 0

        override suspend fun getLatestAnalysisForEntry(entryId: Long): EntryAnalysis? {
            getLatestAnalysisCalls += 1
            return analyses[entryId]
        }

        override suspend fun getAllAnalyses(): List<EntryAnalysis> {
            getAllAnalysesCalls += 1
            return analyses.values.toList()
        }
        override suspend fun getAnalysisById(id: Long): EntryAnalysis? = null
        override suspend fun getAnalysisByExternalId(externalId: String): EntryAnalysis? = null
        override suspend fun getAnalysesForEntry(entryId: Long): List<EntryAnalysis> = listOfNotNull(analyses[entryId])
        override suspend fun insertAnalysis(analysis: EntryAnalysis): Long = 0L
        override suspend fun updateAnalysis(id: Long, insightsJson: String, schemaVersion: String) {}
        override suspend fun deleteAnalysis(id: Long) {}
        override suspend fun deleteAnalysesForEntry(entryId: Long) {}
        override suspend fun upsertAnalysis(analysis: EntryAnalysis) {}
    }

    private class FakeWeightHistoryRepository(
        private val records: List<WeightRecord> = emptyList()
    ) : WeightHistoryRepository {
        override suspend fun addWeightRecord(record: WeightRecord): Long = 0L
        override suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<WeightRecord> = records
        override suspend fun getLatestWeightRecord(): WeightRecord? = records.lastOrNull()
        override suspend fun getAllWeightRecords(): List<WeightRecord> = records
        override suspend fun deleteWeightRecord(recordId: Long) {}
        override suspend fun nullifyRelatedEntryId(entryId: Long) {}
        override suspend fun upsertWeightRecord(record: WeightRecord) {}
    }

    private class FakeNutritionalProfileRepository(
        private val profiles: List<NutritionalProfile> = emptyList()
    ) : NutritionalProfileRepository {
        override suspend fun getAll(): List<NutritionalProfile> = profiles
        override suspend fun getById(profileId: Long): NutritionalProfile? = profiles.find { it.profileId == profileId }
        override suspend fun getByExternalId(externalId: String): NutritionalProfile? = profiles.find { it.externalId == externalId }
        override suspend fun searchByName(query: String, limit: Int): List<NutritionalProfile> {
            val normalized = query.lowercase()
            return profiles.filter {
                it.primaryName.lowercase().contains(normalized) ||
                    it.aliases.any { alias -> alias.lowercase().contains(normalized) }
            }.take(limit)
        }
        override suspend fun insert(profile: NutritionalProfile): Long = profile.profileId
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
