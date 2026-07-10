package com.wellnesswingman.ui.screens.chat

import com.wellnesswingman.data.model.ChatMessageStatus
import com.wellnesswingman.data.model.ChatRole
import com.wellnesswingman.data.model.HealthChatConversation
import com.wellnesswingman.data.model.HealthChatMessage
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.HealthChatRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.data.repository.NutritionalProfileRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.TransactionScope
import com.wellnesswingman.data.repository.WeightHistoryRepository
import com.wellnesswingman.domain.chat.ChatRequest
import com.wellnesswingman.domain.chat.HealthChatService
import com.wellnesswingman.domain.llm.LlmAnalysisResult
import com.wellnesswingman.domain.llm.LlmClient
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.LlmDiagnostics
import com.wellnesswingman.domain.llm.ToolExecutor
import com.wellnesswingman.domain.llm.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class HealthChatThreadViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `draft preserved after ApiKeyMissing result`() = runTest(dispatcher.scheduler) {
        val chatRepo = FakeHealthChatRepository()
        val appSettings = FakeAppSettingsRepository()
        val factory = FakeLlmClientFactory(hasApiKey = false)
        val service = makeService(chatRepo, appSettings, factory)

        val viewModel = HealthChatThreadViewModel(
            conversationExternalId = "conv-draft",
            healthChatService = service,
            healthChatRepository = chatRepo,
            settingsRepository = appSettings,
        )
        advanceUntilIdle()

        viewModel.updateDraft("Hello, health assistant!")
        viewModel.send()
        advanceUntilIdle()

        assertEquals("Hello, health assistant!", viewModel.draft.value)
        assertIs<HealthChatThreadUiState.ApiKeyMissing>(viewModel.uiState.value)
    }

    @Test
    fun `draft cleared after successful send`() = runTest(dispatcher.scheduler) {
        val chatRepo = FakeHealthChatRepository()
        val appSettings = FakeAppSettingsRepository()
        val factory = FakeLlmClientFactory(hasApiKey = true)
        val service = makeService(chatRepo, appSettings, factory)

        val viewModel = HealthChatThreadViewModel(
            conversationExternalId = "conv-success",
            healthChatService = service,
            healthChatRepository = chatRepo,
            settingsRepository = appSettings,
        )
        advanceUntilIdle()

        viewModel.updateDraft("New message")
        viewModel.send()
        advanceUntilIdle()

        assertEquals("", viewModel.draft.value)
        val state = viewModel.uiState.value
        assertIs<HealthChatThreadUiState.Success>(state)
        assertEquals(2, state.messages.size)
    }

    @Test
    fun `recheckConfiguration transitions to Success when key becomes available`() = runTest(dispatcher.scheduler) {
        val chatRepo = FakeHealthChatRepository()
        val appSettings = FakeAppSettingsRepository(apiKey = null)
        val factory = FakeLlmClientFactory(hasApiKey = false)
        val service = makeService(chatRepo, appSettings, factory)

        val viewModel = HealthChatThreadViewModel(
            conversationExternalId = "conv-recheck",
            healthChatService = service,
            healthChatRepository = chatRepo,
            settingsRepository = appSettings,
        )
        advanceUntilIdle()

        viewModel.updateDraft("Check config")
        viewModel.send()
        advanceUntilIdle()
        assertIs<HealthChatThreadUiState.ApiKeyMissing>(viewModel.uiState.value)

        appSettings.apiKey = "sk-test-key"
        factory.hasApiKeyReturn = true
        viewModel.recheckConfiguration()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<HealthChatThreadUiState.Success>(state)
        assertFalse(viewModel.isSending.value)
    }

    @Test
    fun `recheckConfiguration stays on ApiKeyMissing when key is still blank`() = runTest(dispatcher.scheduler) {
        val chatRepo = FakeHealthChatRepository()
        val appSettings = FakeAppSettingsRepository(apiKey = null)
        val factory = FakeLlmClientFactory(hasApiKey = false)
        val service = makeService(chatRepo, appSettings, factory)

        val viewModel = HealthChatThreadViewModel(
            conversationExternalId = "conv-still-missing",
            healthChatService = service,
            healthChatRepository = chatRepo,
            settingsRepository = appSettings,
        )
        advanceUntilIdle()

        viewModel.updateDraft("Hello")
        viewModel.send()
        advanceUntilIdle()
        assertIs<HealthChatThreadUiState.ApiKeyMissing>(viewModel.uiState.value)

        viewModel.recheckConfiguration()
        advanceUntilIdle()

        assertIs<HealthChatThreadUiState.ApiKeyMissing>(viewModel.uiState.value)
        assertEquals("Hello", viewModel.draft.value)
    }

    @Test
    fun `draft preserved after successful reconfiguration recovery at any navigation depth`() = runTest(dispatcher.scheduler) {
        val chatRepo = FakeHealthChatRepository()
        val appSettings = FakeAppSettingsRepository(apiKey = null)
        val factory = FakeLlmClientFactory(hasApiKey = false)
        val service = makeService(chatRepo, appSettings, factory)

        val viewModel = HealthChatThreadViewModel(
            conversationExternalId = "conv-depth",
            healthChatService = service,
            healthChatRepository = chatRepo,
            settingsRepository = appSettings,
        )
        advanceUntilIdle()

        viewModel.updateDraft("Hello after settings")
        viewModel.send()
        advanceUntilIdle()
        assertIs<HealthChatThreadUiState.ApiKeyMissing>(viewModel.uiState.value)
        assertEquals("Hello after settings", viewModel.draft.value)

        appSettings.apiKey = "sk-recovered-key"
        factory.hasApiKeyReturn = true
        viewModel.recheckConfiguration()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<HealthChatThreadUiState.Success>(state)
        assertEquals("Hello after settings", viewModel.draft.value)
        assertFalse(viewModel.isSending.value)
    }

    @Test
    fun `composer available after successful reconfiguration`() = runTest(dispatcher.scheduler) {
        val chatRepo = FakeHealthChatRepository()
        val appSettings = FakeAppSettingsRepository(apiKey = null)
        val factory = FakeLlmClientFactory(hasApiKey = false)
        val service = makeService(chatRepo, appSettings, factory)

        val viewModel = HealthChatThreadViewModel(
            conversationExternalId = "conv-composer",
            healthChatService = service,
            healthChatRepository = chatRepo,
            settingsRepository = appSettings,
        )
        advanceUntilIdle()

        viewModel.updateDraft("Check key")
        viewModel.send()
        advanceUntilIdle()
        assertIs<HealthChatThreadUiState.ApiKeyMissing>(viewModel.uiState.value)
        assertFalse(viewModel.isSending.value)

        appSettings.apiKey = "sk-valid-key"
        factory.hasApiKeyReturn = true
        viewModel.recheckConfiguration()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<HealthChatThreadUiState.Success>(state)
        assertFalse(viewModel.isSending.value)
    }

    private fun makeService(
        chatRepo: HealthChatRepository,
        appSettings: AppSettingsRepository,
        factory: LlmClientFactory,
    ): HealthChatService {
        val toolRegistry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = appSettings,
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
        )
        return HealthChatService(
            healthChatRepository = chatRepo,
            llmClientFactory = factory,
            toolRegistry = toolRegistry,
        )
    }
}

private class FakeHealthChatRepository : HealthChatRepository {
    val conversations = mutableListOf<HealthChatConversation>()
    val messages = mutableListOf<HealthChatMessage>()
    private var nextConversationId = 1L
    private var nextMessageId = 1L

    private inner class FakeTransactionScope : TransactionScope {
        override fun insertMessage(
            conversationId: Long,
            role: ChatRole,
            content: String,
            createdAt: Instant,
            provider: String?,
            model: String?,
            toolCallsJson: String?,
            toolResultJson: String?,
            status: ChatMessageStatus,
        ): Long {
            val id = nextMessageId++
            messages.add(
                HealthChatMessage(
                    messageId = id,
                    conversationId = conversationId,
                    role = role,
                    content = content,
                    createdAt = createdAt,
                    provider = provider,
                    model = model,
                    toolCallsJson = toolCallsJson,
                    toolResultJson = toolResultJson,
                    status = status,
                )
            )
            return id
        }

        override fun updateMessageStatus(messageId: Long, status: ChatMessageStatus) {
            val idx = messages.indexOfFirst { it.messageId == messageId }
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(status = status)
            }
        }

        override fun updateAssistantMessage(
            messageId: Long,
            content: String,
            toolCallsJson: String?,
            model: String?,
            status: ChatMessageStatus,
        ) {
            val idx = messages.indexOfFirst { it.messageId == messageId }
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(
                    content = content,
                    toolCallsJson = toolCallsJson,
                    model = model,
                    status = status,
                )
            }
        }

        override fun touchConversation(id: Long, updatedAt: Instant) {
            val idx = conversations.indexOfFirst { it.conversationId == id }
            if (idx >= 0) {
                conversations[idx] = conversations[idx].copy(updatedAt = updatedAt)
            }
        }
    }

    override suspend fun <T> transaction(block: (TransactionScope) -> T): T =
        block(FakeTransactionScope())

    override suspend fun createConversation(
        externalId: String,
        title: String,
        provider: String?,
        model: String?,
        createdAt: Instant,
        updatedAt: Instant,
    ): Long {
        val id = nextConversationId++
        conversations.add(
            HealthChatConversation(
                conversationId = id,
                externalId = externalId,
                title = title,
                provider = provider,
                model = model,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        )
        return id
    }

    override suspend fun getAllConversations(): List<HealthChatConversation> = conversations.toList()
    override suspend fun getConversationById(id: Long): HealthChatConversation? = conversations.find { it.conversationId == id }
    override suspend fun getConversationByExternalId(externalId: String): HealthChatConversation? = conversations.find { it.externalId == externalId }
    override suspend fun renameConversation(id: Long, title: String, updatedAt: Instant) {
        val idx = conversations.indexOfFirst { it.conversationId == id }
        if (idx >= 0) {
            conversations[idx] = conversations[idx].copy(title = title, updatedAt = updatedAt)
        }
    }
    override suspend fun deleteConversation(id: Long) {
        conversations.removeAll { it.conversationId == id }
        messages.removeAll { it.conversationId == id }
    }
    override suspend fun touchConversation(id: Long, updatedAt: Instant) {
        val idx = conversations.indexOfFirst { it.conversationId == id }
        if (idx >= 0) {
            conversations[idx] = conversations[idx].copy(updatedAt = updatedAt)
        }
    }
    override suspend fun getMessagesForConversation(conversationId: Long): List<HealthChatMessage> =
        messages.filter { it.conversationId == conversationId }.sortedBy { it.createdAt }

    override suspend fun insertMessage(
        conversationId: Long,
        role: ChatRole,
        content: String,
        createdAt: Instant,
        provider: String?,
        model: String?,
        toolCallsJson: String?,
        toolResultJson: String?,
        status: ChatMessageStatus,
    ): Long {
        val id = nextMessageId++
        messages.add(
            HealthChatMessage(
                messageId = id,
                conversationId = conversationId,
                role = role,
                content = content,
                createdAt = createdAt,
                provider = provider,
                model = model,
                toolCallsJson = toolCallsJson,
                toolResultJson = toolResultJson,
                status = status,
            )
        )
        return id
    }

    override suspend fun updateMessageStatus(messageId: Long, status: ChatMessageStatus) {
        val idx = messages.indexOfFirst { it.messageId == messageId }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(status = status)
        }
    }

    override suspend fun updateAssistantMessage(
        messageId: Long,
        content: String,
        toolCallsJson: String?,
        model: String?,
        status: ChatMessageStatus,
    ) {
        val idx = messages.indexOfFirst { it.messageId == messageId }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(
                content = content,
                toolCallsJson = toolCallsJson,
                model = model,
                status = status,
            )
        }
    }

    override suspend fun deleteMessage(messageId: Long) {
        messages.removeAll { it.messageId == messageId }
    }

    override suspend fun getConversationCount(): Long = conversations.size.toLong()
    override suspend fun getMessageCountForConversation(conversationId: Long): Long =
        messages.count { it.conversationId == conversationId }.toLong()
}

private class FakeAppSettingsRepository(
    var apiKey: String? = "test-api-key",
) : AppSettingsRepository {
    override fun getApiKey(provider: LlmProvider): String? = apiKey
    override fun setApiKey(provider: LlmProvider, apiKey: String) { this.apiKey = apiKey }
    override fun removeApiKey(provider: LlmProvider) { apiKey = null }
    override fun getSelectedProvider(): LlmProvider = LlmProvider.OPENAI
    override fun setSelectedProvider(provider: LlmProvider) {}
    override fun getModel(provider: LlmProvider): String? = "gpt-4o-mini"
    override fun setModel(provider: LlmProvider, model: String) {}
    override fun clear() {}
    override fun getHeight(): Double? = null
    override fun setHeight(height: Double) {}
    override fun getHeightUnit(): String = "cm"
    override fun setHeightUnit(unit: String) {}
    override fun getSex(): String? = null
    override fun setSex(sex: String) {}
    override fun getCurrentWeight(): Double? = null
    override fun setCurrentWeight(weight: Double) {}
    override fun getWeightUnit(): String = "kg"
    override fun setWeightUnit(unit: String) {}
    override fun getDateOfBirth(): String? = null
    override fun setDateOfBirth(dob: String) {}
    override fun getActivityLevel(): String? = null
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

private class FakeLlmClientFactory(
    var hasApiKey: Boolean = true,
) : LlmClientFactory(
    settingsRepository = FakeAppSettingsRepository(),
) {
    var hasApiKeyReturn: Boolean = hasApiKey
    private val llmClient = FakeLlmClient()

    override fun create(provider: LlmProvider): LlmClient = llmClient
    override fun hasApiKey(provider: LlmProvider): Boolean = hasApiKeyReturn
    override fun hasCurrentApiKey(): Boolean = hasApiKeyReturn
}

private class FakeLlmClient : LlmClient {
    override val providerId: String get() = "openai"
    override suspend fun analyzeImage(
        imageBytes: ByteArray,
        prompt: String,
        jsonSchema: String?,
        tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
        toolExecutor: ToolExecutor?,
    ) = LlmAnalysisResult("ok", LlmDiagnostics(model = "gpt-4o-mini"))

    override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""

    override suspend fun generateCompletion(
        prompt: String,
        jsonSchema: String?,
        tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
        toolExecutor: ToolExecutor?,
    ) = LlmAnalysisResult("ok", LlmDiagnostics(model = "gpt-4o-mini"))

    override suspend fun generateChatResponse(
        messages: List<com.wellnesswingman.data.model.llm.LlmChatMessage>,
        systemInstruction: String?,
        jsonSchema: String?,
        tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
        toolExecutor: ToolExecutor?,
    ) = LlmAnalysisResult("Response from LLM", LlmDiagnostics(model = "gpt-4o-mini"))
}

private class FakeTrackedEntryRepository : TrackedEntryRepository {
    override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long): List<com.wellnesswingman.data.model.TrackedEntry> = emptyList()
    override suspend fun getEntriesForDay(date: kotlinx.datetime.LocalDate): List<com.wellnesswingman.data.model.TrackedEntry> = emptyList()
    override suspend fun getAllEntries(): List<com.wellnesswingman.data.model.TrackedEntry> = emptyList()
    override suspend fun getRecentEntries(limit: Int, entryType: com.wellnesswingman.data.model.EntryType?): List<com.wellnesswingman.data.model.TrackedEntry> = emptyList()
    override fun observeAllEntries(): Flow<List<com.wellnesswingman.data.model.TrackedEntry>> = emptyFlow()
    override suspend fun getEntryById(id: Long) = null
    override suspend fun getEntryByExternalId(externalId: String) = null
    override suspend fun getEntryByBlobPath(blobPath: String) = null
    override fun observeEntriesForDay(date: kotlinx.datetime.LocalDate): Flow<List<com.wellnesswingman.data.model.TrackedEntry>> = emptyFlow()
    override suspend fun getEntriesForWeek(startMillis: Long, endMillis: Long): List<com.wellnesswingman.data.model.TrackedEntry> = emptyList()
    override suspend fun getEntriesForMonth(startMillis: Long, endMillis: Long): List<com.wellnesswingman.data.model.TrackedEntry> = emptyList()
    override suspend fun getEntriesByStatus(status: com.wellnesswingman.data.model.ProcessingStatus): List<com.wellnesswingman.data.model.TrackedEntry> = emptyList()
    override suspend fun getPendingEntries(): List<com.wellnesswingman.data.model.TrackedEntry> = emptyList()
    override suspend fun insertEntry(entry: com.wellnesswingman.data.model.TrackedEntry) = 1L
    override suspend fun updateEntryStatus(id: Long, status: com.wellnesswingman.data.model.ProcessingStatus) {}
    override suspend fun updateEntryType(id: Long, entryType: com.wellnesswingman.data.model.EntryType) {}
    override suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int) {}
    override suspend fun updateUserNotes(id: Long, notes: String?) {}
    override suspend fun deleteEntry(id: Long) {}
    override suspend fun upsertEntry(entry: com.wellnesswingman.data.model.TrackedEntry) {}
}

private class FakeEntryAnalysisRepository : EntryAnalysisRepository {
    override suspend fun getLatestAnalysisForEntry(entryId: Long) = null
    override suspend fun getAllAnalyses(): List<com.wellnesswingman.data.model.EntryAnalysis> = emptyList()
    override suspend fun getAnalysisById(id: Long) = null
    override suspend fun getAnalysisByExternalId(externalId: String) = null
    override suspend fun getAnalysesForEntry(entryId: Long): List<com.wellnesswingman.data.model.EntryAnalysis> = emptyList()
    override suspend fun insertAnalysis(analysis: com.wellnesswingman.data.model.EntryAnalysis) = 1L
    override suspend fun updateAnalysis(id: Long, insightsJson: String, schemaVersion: String) {}
    override suspend fun deleteAnalysis(id: Long) {}
    override suspend fun deleteAnalysesForEntry(entryId: Long) {}
    override suspend fun upsertAnalysis(analysis: com.wellnesswingman.data.model.EntryAnalysis) {}
}

private class FakeWeightHistoryRepository : WeightHistoryRepository {
    override suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<com.wellnesswingman.data.model.WeightRecord> = emptyList()
    override suspend fun addWeightRecord(record: com.wellnesswingman.data.model.WeightRecord) = 1L
    override suspend fun getLatestWeightRecord() = null
    override suspend fun getAllWeightRecords(): List<com.wellnesswingman.data.model.WeightRecord> = emptyList()
    override suspend fun deleteWeightRecord(recordId: Long) {}
    override suspend fun nullifyRelatedEntryId(entryId: Long) {}
    override suspend fun upsertWeightRecord(record: com.wellnesswingman.data.model.WeightRecord) {}
}

private class FakeNutritionalProfileRepository : NutritionalProfileRepository {
    override fun getAllAsFlow(): Flow<List<NutritionalProfile>> = emptyFlow()
    override suspend fun getAll(): List<NutritionalProfile> = emptyList()
    override suspend fun getById(id: Long): NutritionalProfile? = null
    override suspend fun getByExternalId(externalId: String): NutritionalProfile? = null
    override suspend fun searchByName(query: String, limit: Int): List<NutritionalProfile> = emptyList()
    override suspend fun insert(profile: NutritionalProfile) = 1L
    override suspend fun update(profile: NutritionalProfile) {}
    override suspend fun delete(id: Long) {}
    override suspend fun upsert(profile: NutritionalProfile) {}
}
