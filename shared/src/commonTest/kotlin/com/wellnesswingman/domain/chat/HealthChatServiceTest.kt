package com.wellnesswingman.domain.chat

import com.wellnesswingman.data.model.ChatMessageStatus
import com.wellnesswingman.data.model.ChatRole
import com.wellnesswingman.data.model.HealthChatConversation
import com.wellnesswingman.data.model.HealthChatMessage
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.HealthChatRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.domain.llm.LlmAnalysisResult
import com.wellnesswingman.domain.llm.LlmClient
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.LlmDiagnostics
import com.wellnesswingman.domain.llm.ToolRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*

class HealthChatServiceTest {

    private val now = Clock.System.now()

    private class FakeHealthChatRepository : HealthChatRepository {
        val conversations = mutableListOf<HealthChatConversation>()
        val messages = mutableListOf<HealthChatMessage>()
        private var nextConversationId = 1L
        private var nextMessageId = 1L
        var lastTouchedConversationId: Long? = null
        var lastRenamedTitle: String? = null
        var lastRenamedConversationId: Long? = null

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

        override suspend fun getConversationById(id: Long): HealthChatConversation? =
            conversations.find { it.conversationId == id }

        override suspend fun getConversationByExternalId(externalId: String): HealthChatConversation? =
            conversations.find { it.externalId == externalId }

        override suspend fun renameConversation(id: Long, title: String, updatedAt: Instant) {
            lastRenamedConversationId = id
            lastRenamedTitle = title
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
            lastTouchedConversationId = id
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

        override suspend fun deleteMessage(messageId: Long) {
            messages.removeAll { it.messageId == messageId }
        }

        override suspend fun getConversationCount(): Long = conversations.size.toLong()

        override suspend fun getMessageCountForConversation(conversationId: Long): Long =
            messages.count { it.conversationId == conversationId }.toLong()
    }

    private class FakeAppSettingsRepository : AppSettingsRepository {
        override fun getApiKey(provider: LlmProvider): String? = "test-api-key"
        override fun setApiKey(provider: LlmProvider, apiKey: String) {}
        override fun removeApiKey(provider: LlmProvider) {}
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

    private class FakeNutritionalProfileRepository : com.wellnesswingman.data.repository.NutritionalProfileRepository {
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

    private fun createClientWithResponse(responseContent: String, model: String = "gpt-4o-mini"): LlmClient {
        return object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(
                imageBytes: ByteArray,
                prompt: String,
                jsonSchema: String?,
                tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
                toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?,
            ) = LlmAnalysisResult(responseContent, LlmDiagnostics(model = model))
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(
                prompt: String,
                jsonSchema: String?,
                tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
                toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?,
            ) = LlmAnalysisResult(responseContent, LlmDiagnostics(model = model))
            override suspend fun generateChatResponse(
                messages: List<com.wellnesswingman.data.model.llm.LlmChatMessage>,
                systemInstruction: String?,
                jsonSchema: String?,
                tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
                toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?,
            ) = LlmAnalysisResult(responseContent, LlmDiagnostics(model = model))
        }
    }

    private class FakeTrackedEntryRepository : com.wellnesswingman.data.repository.TrackedEntryRepository {
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

    private class FakeEntryAnalysisRepository : com.wellnesswingman.data.repository.EntryAnalysisRepository {
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

    private class FakeWeightHistoryRepository : com.wellnesswingman.data.repository.WeightHistoryRepository {
        override suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<com.wellnesswingman.data.model.WeightRecord> = emptyList()
        override suspend fun addWeightRecord(record: com.wellnesswingman.data.model.WeightRecord) = 1L
        override suspend fun getLatestWeightRecord() = null
        override suspend fun getAllWeightRecords(): List<com.wellnesswingman.data.model.WeightRecord> = emptyList()
        override suspend fun deleteWeightRecord(recordId: Long) {}
        override suspend fun nullifyRelatedEntryId(entryId: Long) {}
        override suspend fun upsertWeightRecord(record: com.wellnesswingman.data.model.WeightRecord) {}
    }

    private fun makeService(
        hasKey: Boolean = true,
        responseContent: String = "Hello! I'm your health assistant.",
        responseModel: String = "gpt-4o-mini",
    ): HealthChatService {
        val chatRepo = FakeHealthChatRepository()
        val appSettings = FakeAppSettingsRepository()
        val llmClient = createClientWithResponse(responseContent, responseModel)

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns llmClient
        every { factory.hasApiKey(LlmProvider.OPENAI) } returns hasKey
        every { factory.hasCurrentApiKey() } returns hasKey

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

    private fun makeServiceWithRepo(
        chatRepo: FakeHealthChatRepository,
        hasKey: Boolean = true,
        responseContent: String = "Hello! I'm your health assistant.",
        responseModel: String = "gpt-4o-mini",
    ): HealthChatService {
        val appSettings = FakeAppSettingsRepository()
        val llmClient = createClientWithResponse(responseContent, responseModel)

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns llmClient
        every { factory.hasApiKey(LlmProvider.OPENAI) } returns hasKey
        every { factory.hasCurrentApiKey() } returns hasKey

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

    @Test
    fun `sendMessage to new conversation creates conversation and persists messages`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo)

        val request = ChatRequest(
            conversationExternalId = "conv-1",
            messageContent = "What did I eat yesterday?",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.Success)
        assertEquals("conv-1", result.conversation.externalId)
        assertEquals(2, chatRepo.messages.size)

        val userMsg = chatRepo.messages[0]
        assertEquals(ChatRole.USER, userMsg.role)
        assertEquals("What did I eat yesterday?", userMsg.content)
        assertEquals(ChatMessageStatus.COMPLETED, userMsg.status)
        assertEquals("openai", userMsg.provider)
        assertEquals("gpt-4o-mini", userMsg.model)

        val assistantMsg = chatRepo.messages[1]
        assertEquals(ChatRole.ASSISTANT, assistantMsg.role)
        assertEquals("Hello! I'm your health assistant.", assistantMsg.content)
        assertEquals(ChatMessageStatus.COMPLETED, assistantMsg.status)
        assertEquals("openai", assistantMsg.provider)
        assertEquals("gpt-4o-mini", assistantMsg.model)

        assertEquals(assistantMsg, result.assistantMessage)
    }

    @Test
    fun `sendMessage to existing conversation loads history`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo)

        chatRepo.createConversation(
            externalId = "conv-existing",
            title = "Existing chat",
            provider = "openai",
            model = "gpt-4o-mini",
            createdAt = now,
            updatedAt = now,
        )
        chatRepo.insertMessage(
            conversationId = 1L,
            role = ChatRole.USER,
            content = "Hello",
            createdAt = now,
            provider = "openai",
            model = "gpt-4o-mini",
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            conversationId = 1L,
            role = ChatRole.ASSISTANT,
            content = "Hi there!",
            createdAt = now,
            provider = "openai",
            model = "gpt-4o-mini",
            status = ChatMessageStatus.COMPLETED,
        )

        val request = ChatRequest(
            conversationExternalId = "conv-existing",
            messageContent = "Tell me about my sleep",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.Success)
        assertEquals(4, chatRepo.messages.size)

        val userMsg = chatRepo.messages[2]
        assertEquals(ChatRole.USER, userMsg.role)
        assertEquals("Tell me about my sleep", userMsg.content)

        val assistantMsg = chatRepo.messages[3]
        assertEquals(ChatRole.ASSISTANT, assistantMsg.role)
    }

    @Test
    fun `sendMessage auto-titles conversation on first message`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo)

        val request = ChatRequest(
            conversationExternalId = "conv-auto-title",
            messageContent = "What calories are in an apple?",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.Success)
        assertEquals("What calories are in an apple?", chatRepo.lastRenamedTitle)
        assertEquals(1L, chatRepo.lastRenamedConversationId)
        assertEquals("What calories are in an apple?", result.conversation.title)
    }

    @Test
    fun `sendMessage preserves existing conversation title`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo)

        chatRepo.createConversation(
            externalId = "conv-titled",
            title = "My meal questions",
            provider = "openai",
            model = "gpt-4o-mini",
            createdAt = now,
            updatedAt = now,
        )

        val request = ChatRequest(
            conversationExternalId = "conv-titled",
            messageContent = "How much protein?",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.Success)
        assertNull(chatRepo.lastRenamedTitle)
        assertNotNull(chatRepo.lastTouchedConversationId)
        assertEquals("My meal questions", result.conversation.title)
    }

    @Test
    fun `sendMessage returns ApiKeyMissing when no API key`() = runTest {
        val service = makeService(hasKey = false)

        val request = ChatRequest(
            conversationExternalId = "conv-nokey",
            messageContent = "Hello",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.ApiKeyMissing)
    }

    @Test
    fun `sendMessage persists user message before checking api key`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo, hasKey = false)

        val request = ChatRequest(
            conversationExternalId = "conv-nokey-persist",
            messageContent = "Hello",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.ApiKeyMissing)
        assertEquals(1, chatRepo.messages.size)
        val userMsg = chatRepo.messages[0]
        assertEquals(ChatRole.USER, userMsg.role)
        assertEquals("Hello", userMsg.content)
        assertEquals(ChatMessageStatus.COMPLETED, userMsg.status)
    }

    @Test
    fun `sendMessage returns ProviderError on LLM failure`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val appSettings = FakeAppSettingsRepository()

        val failingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(
                imageBytes: ByteArray,
                prompt: String,
                jsonSchema: String?,
                tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
                toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?,
            ) = throw RuntimeException("API error")
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(
                prompt: String,
                jsonSchema: String?,
                tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
                toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?,
            ) = throw RuntimeException("API error")
            override suspend fun generateChatResponse(
                messages: List<com.wellnesswingman.data.model.llm.LlmChatMessage>,
                systemInstruction: String?,
                jsonSchema: String?,
                tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>,
                toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?,
            ) = throw RuntimeException("API error")
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns failingClient
        every { factory.hasApiKey(LlmProvider.OPENAI) } returns true
        every { factory.hasCurrentApiKey() } returns true

        val toolRegistry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = appSettings,
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
        )

        val service = HealthChatService(
            healthChatRepository = chatRepo,
            llmClientFactory = factory,
            toolRegistry = toolRegistry,
        )

        val request = ChatRequest(
            conversationExternalId = "conv-fail",
            messageContent = "Hello",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.ProviderError)
        assertTrue(result.message.contains("API error"))
    }

    @Test
    fun `sendMessage returns ProviderError on missing API key at create time`() = runTest {
        val chatRepo = FakeHealthChatRepository()

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } throws IllegalStateException("API key not configured for OPENAI")
        every { factory.hasApiKey(LlmProvider.OPENAI) } returns true
        every { factory.hasCurrentApiKey() } returns true

        val toolRegistry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
        )

        val service = HealthChatService(
            healthChatRepository = chatRepo,
            llmClientFactory = factory,
            toolRegistry = toolRegistry,
        )

        val request = ChatRequest(
            conversationExternalId = "conv-fail-create",
            messageContent = "Hello",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.ProviderError)
        assertTrue(result.message.contains("API key not configured"))
    }

    @Test
    fun `getConversation returns Found for existing conversation`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo)

        chatRepo.createConversation(
            externalId = "conv-get",
            title = "Test",
            provider = "openai",
            model = "gpt-4o-mini",
            createdAt = now,
            updatedAt = now,
        )

        val result = service.getConversation("conv-get")

        assertTrue(result is ChatConversationResult.Found)
        assertEquals("conv-get", result.conversation.externalId)
    }

    @Test
    fun `getConversation returns NotFound for missing conversation`() = runTest {
        val service = makeService()

        val result = service.getConversation("nonexistent")

        assertTrue(result is ChatConversationResult.NotFound)
    }

    @Test
    fun `listConversations returns all conversations`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo)

        chatRepo.createConversation("c1", "Chat 1", "openai", "gpt-4o-mini", now, now)
        chatRepo.createConversation("c2", "Chat 2", "openai", "gpt-4o-mini", now, now)

        val result = service.listConversations()

        assertEquals(2, result.size)
    }

    @Test
    fun `deleteConversation removes conversation and its messages`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo)

        val convId = chatRepo.createConversation("c1", "To delete", "openai", "gpt-4o-mini", now, now)
        chatRepo.insertMessage(convId, ChatRole.USER, "Hello", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)

        assertEquals(1, chatRepo.conversations.size)
        assertEquals(1, chatRepo.messages.size)

        service.deleteConversation(convId)

        assertEquals(0, chatRepo.conversations.size)
        assertEquals(0, chatRepo.messages.size)
    }

    @Test
    fun `sendMessage persists provider and model metadata on assistant message`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo, responseModel = "gpt-4o")

        val request = ChatRequest(
            conversationExternalId = "conv-meta",
            messageContent = "What is my weight?",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.Success)
        val assistantMsg = result.assistantMessage
        assertEquals("openai", assistantMsg.provider)
        assertEquals("gpt-4o", assistantMsg.model)
        assertEquals(ChatMessageStatus.COMPLETED, assistantMsg.status)
    }

    @Test
    fun `sendMessage user message starts PENDING then becomes COMPLETED`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo)

        val request = ChatRequest(
            conversationExternalId = "conv-status",
            messageContent = "Test pending status",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.Success)
        val userMsg = chatRepo.messages.first { it.role == ChatRole.USER }
        assertEquals(ChatMessageStatus.COMPLETED, userMsg.status)
    }

    @Test
    fun `sendMessage uses diagnostics model from LLM response`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo, responseModel = "gpt-4o-2024-08-06")

        val request = ChatRequest(
            conversationExternalId = "conv-diag",
            messageContent = "Hello",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.Success)
        val assistantMsg = chatRepo.messages.first { it.role == ChatRole.ASSISTANT }
        assertEquals("gpt-4o-2024-08-06", assistantMsg.model)
    }

    @Test
    fun `sendMessage with new conversation stores provider and model on conversation`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo)

        val request = ChatRequest(
            conversationExternalId = "conv-provider-model",
            messageContent = "Hello",
            provider = LlmProvider.GEMINI,
            model = "gemini-1.5-flash",
        )

        val geminiClient = createClientWithResponse("Hello from Gemini", "gemini-1.5-flash")
        val appSettings = FakeAppSettingsRepository()
        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.GEMINI) } returns geminiClient
        every { factory.hasApiKey(LlmProvider.GEMINI) } returns true
        every { factory.hasCurrentApiKey() } returns true

        val toolRegistry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = appSettings,
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
        )

        val service2 = HealthChatService(chatRepo, factory, toolRegistry)
        val result = service2.sendMessage(request.copy(provider = LlmProvider.GEMINI, model = "gemini-1.5-flash"))

        assertTrue(result is ChatResult.Success)
        assertEquals("gemini", chatRepo.conversations[0].provider)
        assertEquals("gemini-1.5-flash", chatRepo.conversations[0].model)
    }

    @Test
    fun `sendMessage user message uses request provider and model`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo, hasKey = false)

        val request = ChatRequest(
            conversationExternalId = "conv-user-meta",
            messageContent = "Hello",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        service.sendMessage(request)

        val userMsg = chatRepo.messages.first { it.role == ChatRole.USER }
        assertEquals("openai", userMsg.provider)
        assertEquals("gpt-4o-mini", userMsg.model)
    }

    @Test
    fun `sendMessage thread bounded to max history messages`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val llmClient = createClientWithResponse("Response", "gpt-4o-mini")

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns llmClient
        every { factory.hasApiKey(LlmProvider.OPENAI) } returns true
        every { factory.hasCurrentApiKey() } returns true

        val capturedMessages = mutableListOf<List<com.wellnesswingman.data.model.llm.LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>, toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>, toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<com.wellnesswingman.data.model.llm.LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>, toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?): LlmAnalysisResult {
                capturedMessages.add(messages)
                return LlmAnalysisResult("Response", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }
        every { factory.create(LlmProvider.OPENAI) } returns capturingClient

        val toolRegistry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
        )

        val convId = chatRepo.createConversation("conv-bound", "Bound test", "openai", "gpt-4o-mini", now, now)
        for (i in 1..(HealthChatService.MAX_HISTORY_MESSAGES + 10)) {
            chatRepo.insertMessage(convId, ChatRole.USER, "Message $i", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        }

        val service = HealthChatService(chatRepo, factory, toolRegistry)
        val request = ChatRequest("conv-bound", "Final message", LlmProvider.OPENAI, "gpt-4o-mini")
        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()
        assertEquals(HealthChatService.MAX_HISTORY_MESSAGES + 1, sentMessages.size)
    }

    @Test
    fun `sendMessage uses system instruction constant`() = runTest {
        val capturedInstructions = mutableListOf<String?>()
        val chatRepo = FakeHealthChatRepository()

        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>, toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>, toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<com.wellnesswingman.data.model.llm.LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>, toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?): LlmAnalysisResult {
                capturedInstructions.add(systemInstruction)
                return LlmAnalysisResult("Response", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns capturingClient
        every { factory.hasApiKey(LlmProvider.OPENAI) } returns true
        every { factory.hasCurrentApiKey() } returns true

        val toolRegistry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
        )

        val service = HealthChatService(chatRepo, factory, toolRegistry)

        val request = ChatRequest("conv-sys", "Hello", LlmProvider.OPENAI, "gpt-4o-mini")
        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.Success)
        assertEquals(1, capturedInstructions.size)
        assertNotNull(capturedInstructions[0])
        assertTrue(capturedInstructions[0]!!.contains("health and wellness assistant"))
        assertTrue(capturedInstructions[0]!!.contains("medical diagnoses"))
    }

    @Test
    fun `getConversation returns the correct conversation by external id`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo)

        chatRepo.createConversation("ext-1", "First", "openai", "gpt-4o-mini", now, now)
        chatRepo.createConversation("ext-2", "Second", "openai", "gpt-4o-mini", now, now)

        val result = service.getConversation("ext-2")

        assertTrue(result is ChatConversationResult.Found)
        assertEquals("Second", result.conversation.title)
    }

    @Test
    fun `empty listConversations returns empty list`() = runTest {
        val service = makeService()

        val result = service.listConversations()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `sendMessage auto-title truncates long messages`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val service = makeServiceWithRepo(chatRepo)

        val longMessage = "A".repeat(200)
        val request = ChatRequest(
            conversationExternalId = "conv-long",
            messageContent = longMessage,
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.Success)
        assertEquals(80, chatRepo.lastRenamedTitle?.length)
        assertEquals(longMessage.take(80), chatRepo.lastRenamedTitle)
    }

    @Test
    fun `sendMessage persists single user message before LLM call`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val captured = mutableListOf<List<com.wellnesswingman.data.model.llm.LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>, toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>, toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<com.wellnesswingman.data.model.llm.LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>, toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?): LlmAnalysisResult {
                captured.add(messages)
                return LlmAnalysisResult("Hello from LLM", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns capturingClient
        every { factory.hasApiKey(LlmProvider.OPENAI) } returns true
        every { factory.hasCurrentApiKey() } returns true

        val toolRegistry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
        )

        val service = HealthChatService(chatRepo, factory, toolRegistry)
        service.sendMessage(ChatRequest("conv-verify", "Verify message", LlmProvider.OPENAI, "gpt-4o-mini"))

        val userMsg = chatRepo.messages.first { it.role == ChatRole.USER }
        assertEquals("Verify message", userMsg.content)
        assertEquals(ChatMessageStatus.COMPLETED, userMsg.status)
        val assistantMsg = chatRepo.messages.first { it.role == ChatRole.ASSISTANT }
        assertNotNull(assistantMsg)
    }
}
