package com.wellnesswingman.domain.chat

import com.wellnesswingman.data.model.ChatMessageStatus
import com.wellnesswingman.data.model.ChatRole
import com.wellnesswingman.data.model.HealthChatConversation
import com.wellnesswingman.data.model.HealthChatMessage
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.model.llm.LlmChatMessage
import com.wellnesswingman.data.model.llm.LlmChatRole
import com.wellnesswingman.data.model.llm.ToolCall
import com.wellnesswingman.data.model.llm.ToolDefinition
import com.wellnesswingman.data.model.llm.ToolResult
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.HealthChatRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.data.repository.TransactionScope
import com.wellnesswingman.domain.llm.LlmAnalysisResult
import com.wellnesswingman.domain.llm.LlmClient
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.LlmDiagnostics
import com.wellnesswingman.domain.llm.ToolExecutor
import com.wellnesswingman.domain.llm.ToolRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
                lastTouchedConversationId = id
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
                onToolRoundCompleted: (() -> Unit)?,
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
        assertEquals(ChatMessageStatus.ERROR, userMsg.status)
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
                onToolRoundCompleted: (() -> Unit)?,
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
        assertTrue(result.conversationId > 0L)
        val userMsg = chatRepo.messages.find { it.role == ChatRole.USER }
        assertNotNull(userMsg)
        assertEquals(ChatMessageStatus.ERROR, userMsg.status)
        val assistantMsg = chatRepo.messages.find { it.role == ChatRole.ASSISTANT }
        assertNotNull(assistantMsg)
        assertEquals(ChatMessageStatus.ERROR, assistantMsg.status)
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
        assertTrue(result.conversationId > 0L)
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
            override suspend fun generateChatResponse(messages: List<com.wellnesswingman.data.model.llm.LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>, toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
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
        for (i in 1..(HealthChatService.MAX_HISTORY_MESSAGES + 1)) {
            chatRepo.insertMessage(convId, ChatRole.USER, "Message $i", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        }

        val service = HealthChatService(chatRepo, factory, toolRegistry)
        val request = ChatRequest("conv-bound", "Final message", LlmProvider.OPENAI, "gpt-4o-mini")
        val result = service.sendMessage(request)

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()
        assertEquals(HealthChatService.MAX_HISTORY_MESSAGES, sentMessages.size)
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
            override suspend fun generateChatResponse(messages: List<com.wellnesswingman.data.model.llm.LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<com.wellnesswingman.data.model.llm.ToolDefinition>, toolExecutor: com.wellnesswingman.domain.llm.ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
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
        val sysInstr = capturedInstructions[0]!!
        assertTrue(sysInstr.contains("health and wellness assistant"))
        assertTrue(sysInstr.contains("medical diagnoses"))
        assertTrue(sysInstr.contains("Never expose unnecessary personal or sensitive data"))
        assertTrue(sysInstr.contains("call local emergency services immediately"))
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
        val captured = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
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

    @Test
    fun `sendMessage persists tool calls and results when LLM uses tools`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val toolCall1 = ToolCall(id = "call1", name = "get_user_profile", arguments = JsonObject(emptyMap()))
        val toolCall2 = ToolCall(id = "call2", name = "get_weight_history", arguments = JsonObject(emptyMap()))

        val toolUsingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                toolExecutor?.invoke(toolCall1)
                toolExecutor?.invoke(toolCall2)
                return LlmAnalysisResult("Here is your data", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns toolUsingClient
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
        val result = service.sendMessage(
            ChatRequest("conv-tools", "What is my weight?", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(result is ChatResult.Success)

        val toolMessages = chatRepo.messages.filter { it.role == ChatRole.TOOL }
        assertEquals(2, toolMessages.size)

        val toolMsg1 = toolMessages[0]
        assertEquals(ChatRole.TOOL, toolMsg1.role)
        val persistedResult1 = json.decodeFromString<ToolResult>(toolMsg1.toolResultJson!!)
        assertEquals("call1", persistedResult1.toolCallId)
        assertEquals("get_user_profile", persistedResult1.name)

        val toolMsg2 = toolMessages[1]
        val persistedResult2 = json.decodeFromString<ToolResult>(toolMsg2.toolResultJson!!)
        assertEquals("call2", persistedResult2.toolCallId)
        assertEquals("get_weight_history", persistedResult2.name)

        val assistantMsg = chatRepo.messages.find { it.role == ChatRole.ASSISTANT && it.status == ChatMessageStatus.COMPLETED }
        assertNotNull(assistantMsg)
        assertNotNull(assistantMsg.toolCallsJson)
        val persistedToolCalls = json.decodeFromString<List<ToolCall>>(assistantMsg.toolCallsJson!!)
        assertEquals(2, persistedToolCalls.size)
        assertEquals("call1", persistedToolCalls[0].id)
        assertEquals("get_weight_history", persistedToolCalls[1].name)
    }

    @Test
    fun `toLlmChatMessage reconstructs tool calls from persisted assistant message`() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val toolCalls = listOf(
            ToolCall(id = "call1", name = "get_user_profile", arguments = JsonObject(emptyMap())),
        )
        val toolCallsJson = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            toolCalls.forEach { add(buildJsonObject {
                put("id", it.id ?: "")
                put("name", it.name)
                put("arguments", it.arguments)
            }) }
        })

        val chatRepo = FakeHealthChatRepository()
        val convId = chatRepo.createConversation("conv-replay", "Replay", "openai", "gpt-4o-mini", now, now)
        chatRepo.insertMessage(convId, ChatRole.USER, "My data?", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val toolUsingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
                return LlmAnalysisResult("Response", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns toolUsingClient
        every { factory.hasApiKey(LlmProvider.OPENAI) } returns true
        every { factory.hasCurrentApiKey() } returns true

        val toolRegistry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
        )

        val toolResult = ToolResult(toolCallId = "call1", name = "get_user_profile", content = JsonPrimitive("data"))
        chatRepo.insertMessage(
            convId, ChatRole.ASSISTANT, "",
            now, "openai", "gpt-4o-mini",
            toolCallsJson = toolCallsJson,
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.TOOL, "",
            now, "openai", "gpt-4o-mini",
            toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult),
            status = ChatMessageStatus.COMPLETED,
        )

        val service = HealthChatService(chatRepo, factory, toolRegistry)
        service.sendMessage(ChatRequest("conv-replay", "Tell me more", LlmProvider.OPENAI, "gpt-4o-mini"))

        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        val assistantInHistory = sentMessages.find { it.role == LlmChatRole.ASSISTANT && it.toolCalls != null }
        assertNotNull(assistantInHistory)
        assertEquals(1, assistantInHistory.toolCalls?.size)
        assertEquals("get_user_profile", assistantInHistory.toolCalls?.first()?.name)

        val toolInHistory = sentMessages.find { it.role == LlmChatRole.TOOL }
        assertNotNull(toolInHistory)
        assertEquals("call1", toolInHistory.toolCallId)
        assertEquals("get_user_profile", toolInHistory.toolName)
    }

    @Test
    fun `toLlmChatMessage reconstructs tool result from persisted tool message`() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val toolResult = ToolResult(
            toolCallId = "call1",
            name = "get_user_profile",
            content = JsonPrimitive("{\"name\": \"Test\"}"),
        )
        val toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult)

        val chatRepo = FakeHealthChatRepository()
        val convId = chatRepo.createConversation("conv-tool-msg", "Tool Msg", "openai", "gpt-4o-mini", now, now)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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

        chatRepo.insertMessage(
            convId, ChatRole.USER, "My data?",
            now, "openai", "gpt-4o-mini",
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.TOOL, "",
            now, "openai", "gpt-4o-mini",
            toolResultJson = toolResultJson,
            status = ChatMessageStatus.COMPLETED,
        )

        val service = HealthChatService(chatRepo, factory, toolRegistry)
        service.sendMessage(ChatRequest("conv-tool-msg", "Tell me more", LlmProvider.OPENAI, "gpt-4o-mini"))

        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        val toolInHistory = sentMessages.find { it.role == LlmChatRole.TOOL }
        assertNotNull(toolInHistory)
        assertEquals("call1", toolInHistory.toolCallId)
        assertEquals("get_user_profile", toolInHistory.toolName)
        assertNotNull(toolInHistory.toolResultJson)
    }

    @Test
    fun `sendMessage marks both messages as ERROR on cancellation`() = runTest {
        val chatRepo = FakeHealthChatRepository()

        val cancellingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(
                imageBytes: ByteArray,
                prompt: String,
                jsonSchema: String?,
                tools: List<ToolDefinition>,
                toolExecutor: ToolExecutor?,
            ) = throw CancellationException()
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(
                prompt: String,
                jsonSchema: String?,
                tools: List<ToolDefinition>,
                toolExecutor: ToolExecutor?,
            ) = throw CancellationException()
            override suspend fun generateChatResponse(
                messages: List<LlmChatMessage>,
                systemInstruction: String?,
                jsonSchema: String?,
                tools: List<ToolDefinition>,
                toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ) = throw CancellationException()
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns cancellingClient
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

        val request = ChatRequest(
            conversationExternalId = "conv-cancel",
            messageContent = "Hello",
            provider = LlmProvider.OPENAI,
            model = "gpt-4o-mini",
        )

        try {
            service.sendMessage(request)
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // expected
        }

        assertEquals(1, chatRepo.messages.size)

        val userMsg = chatRepo.messages.find { it.role == ChatRole.USER }
        assertNotNull(userMsg)
        assertEquals(ChatMessageStatus.ERROR, userMsg.status)
    }

    @Test
    fun `failed provider turn excluded from subsequent send history`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val appSettings = FakeAppSettingsRepository()
        val convId = chatRepo.createConversation(
            "conv-fail-hist", "Prior chat", "openai", "gpt-4o-mini", now, now,
        )
        chatRepo.insertMessage(convId, ChatRole.USER, "Hello", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "Hi there!", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)

        val failingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(
                imageBytes: ByteArray, prompt: String, jsonSchema: String?,
                tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
            ) = throw RuntimeException("API error")
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(
                prompt: String, jsonSchema: String?,
                tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
            ) = throw RuntimeException("API error")
            override suspend fun generateChatResponse(
                messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?,
                tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
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

        val service = HealthChatService(chatRepo, factory, toolRegistry)
        val failRequest = ChatRequest("conv-fail-hist", "What is my weight?", LlmProvider.OPENAI, "gpt-4o-mini")
        val failResult = service.sendMessage(failRequest)

        assertTrue(failResult is ChatResult.ProviderError)
        val failedUser = chatRepo.messages.find { it.content == "What is my weight?" }
        assertNotNull(failedUser)
        assertEquals(ChatMessageStatus.ERROR, failedUser.status)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
                return LlmAnalysisResult("Sure!", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }
        every { factory.create(LlmProvider.OPENAI) } returns capturingClient

        val successRequest = ChatRequest("conv-fail-hist", "Tell me more", LlmProvider.OPENAI, "gpt-4o-mini")
        val successResult = service.sendMessage(successRequest)

        assertTrue(successResult is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        val failedContentInHistory = sentMessages.any { it.content == "What is my weight?" }
        assertFalse(failedContentInHistory, "Failed turn content must not appear in subsequent LLM history")

        val priorHelloInHistory = sentMessages.any { it.content == "Hello" }
        assertTrue(priorHelloInHistory, "Prior successfully completed messages must appear in LLM history")
    }

    @Test
    fun `api key missing turn excluded from subsequent send history`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val convId = chatRepo.createConversation(
            "conv-nokey-hist", "Health chat", "openai", "gpt-4o-mini", now, now,
        )
        chatRepo.insertMessage(convId, ChatRole.USER, "Hello", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "Hi there!", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)

        val factory = mockk<LlmClientFactory>()
        every { factory.hasApiKey(LlmProvider.OPENAI) } returns false
        every { factory.hasCurrentApiKey() } returns false

        val toolRegistry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
        )

        val service = HealthChatService(chatRepo, factory, toolRegistry)
        val failRequest = ChatRequest("conv-nokey-hist", "What is my weight?", LlmProvider.OPENAI, "gpt-4o-mini")
        val failResult = service.sendMessage(failRequest)

        assertTrue(failResult is ChatResult.ApiKeyMissing)
        val failedUser = chatRepo.messages.find { it.content == "What is my weight?" }
        assertNotNull(failedUser)
        assertEquals(ChatMessageStatus.ERROR, failedUser.status)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
                return LlmAnalysisResult("Sure!", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }
        every { factory.create(LlmProvider.OPENAI) } returns capturingClient
        every { factory.hasApiKey(LlmProvider.OPENAI) } returns true
        every { factory.hasCurrentApiKey() } returns true

        val successRequest = ChatRequest("conv-nokey-hist", "Tell me more", LlmProvider.OPENAI, "gpt-4o-mini")
        val successResult = service.sendMessage(successRequest)

        assertTrue(successResult is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        val failedContentInHistory = sentMessages.any { it.content == "What is my weight?" }
        assertFalse(failedContentInHistory, "Failed turn content must not appear in subsequent LLM history after API key missing")

        val priorHelloInHistory = sentMessages.any { it.content == "Hello" }
        assertTrue(priorHelloInHistory, "Prior successfully completed messages must appear in LLM history")
    }

    @Test
    fun `sendMessage excludes PENDING messages from prior turns in LLM history`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val convId = chatRepo.createConversation(
            "conv-pending-hist", "Prior pending", "openai", "gpt-4o-mini", now, now,
        )
        chatRepo.insertMessage(convId, ChatRole.USER, "Prior completed", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "Prior completed response", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.USER, "Pending user", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.PENDING)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "Pending assistant", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.PENDING)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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
        val result = service.sendMessage(ChatRequest("conv-pending-hist", "New message", LlmProvider.OPENAI, "gpt-4o-mini"))

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        assertTrue(sentMessages.any { it.content == "Prior completed" }, "Completed prior content must appear in history")
        assertFalse(sentMessages.any { it.content == "Pending user" }, "Pending user content must be excluded from history")
        assertFalse(sentMessages.any { it.content == "Pending assistant" }, "Pending assistant content must be excluded from history")
    }

    @Test
    fun `sendMessage persists multi-round tool calls with separate round groups`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val multiRoundClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                toolExecutor?.invoke(ToolCall(id = "round1-call", name = "get_user_profile", arguments = JsonObject(emptyMap())))
                onToolRoundCompleted?.invoke()
                toolExecutor?.invoke(ToolCall(id = "round2-call-a", name = "get_weight_history", arguments = JsonObject(emptyMap())))
                toolExecutor?.invoke(ToolCall(id = "round2-call-b", name = "get_recent_entries", arguments = JsonObject(emptyMap())))
                onToolRoundCompleted?.invoke()
                return LlmAnalysisResult("Final response", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns multiRoundClient
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
        val result = service.sendMessage(
            ChatRequest("conv-multi-round", "Get my data", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(result is ChatResult.Success)

        val assistantWithToolCalls = chatRepo.messages.filter { it.role == ChatRole.ASSISTANT && it.toolCallsJson != null }
        assertEquals(2, assistantWithToolCalls.size, "Must persist two assistant(toolCalls) messages for two rounds")

        val round1ToolCalls = json.decodeFromString<List<ToolCall>>(assistantWithToolCalls[0].toolCallsJson!!)
        assertEquals(1, round1ToolCalls.size)
        assertEquals("round1-call", round1ToolCalls[0].id)

        val round2ToolCalls = json.decodeFromString<List<ToolCall>>(assistantWithToolCalls[1].toolCallsJson!!)
        assertEquals(2, round2ToolCalls.size)
        assertEquals("round2-call-a", round2ToolCalls[0].id)
        assertEquals("round2-call-b", round2ToolCalls[1].id)

        val toolMessages = chatRepo.messages.filter { it.role == ChatRole.TOOL }
        assertEquals(3, toolMessages.size, "Must persist three tool results for 2 rounds (1+2)")

        val finalAssistant = chatRepo.messages.find { it.role == ChatRole.ASSISTANT && it.toolCallsJson == null }
        assertNotNull(finalAssistant)
        assertEquals("Final response", finalAssistant.content)
        assertEquals(ChatMessageStatus.COMPLETED, finalAssistant.status)
    }

    @Test
    fun `sendMessage preserves tool turn groups when trimming history at boundary`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val convId = chatRepo.createConversation(
            "conv-trim-group", "Trim group", "openai", "gpt-4o-mini", now, now,
        )

        for (i in 1..(HealthChatService.MAX_HISTORY_MESSAGES - 2)) {
            chatRepo.insertMessage(
                convId, ChatRole.USER, "User $i",
                now, "openai", "gpt-4o-mini",
                status = ChatMessageStatus.COMPLETED,
            )
        }
        val toolCallsJson = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            add(buildJsonObject {
                put("id", "call-1")
                put("name", "get_user_profile")
                put("arguments", JsonObject(emptyMap()))
            })
        })
        chatRepo.insertMessage(
            convId, ChatRole.ASSISTANT, "",
            now, "openai", "gpt-4o-mini",
            toolCallsJson = toolCallsJson,
            status = ChatMessageStatus.COMPLETED,
        )
        val toolResult = ToolResult(toolCallId = "call-1", name = "get_user_profile", content = JsonPrimitive("data"))
        chatRepo.insertMessage(
            convId, ChatRole.TOOL, "",
            now, "openai", "gpt-4o-mini",
            toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult),
            status = ChatMessageStatus.COMPLETED,
        )

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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
        val result = service.sendMessage(
            ChatRequest("conv-trim-group", "Final message", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        val assistantWithToolCalls = sentMessages.find {
            it.role == LlmChatRole.ASSISTANT && it.toolCalls != null
        }
        assertNotNull(assistantWithToolCalls, "Tool-call assistant message must be preserved in history")

        val toolInHistory = sentMessages.find { it.role == LlmChatRole.TOOL }
        assertNotNull(toolInHistory, "Tool result message must be preserved in history")
    }

    @Test
    fun `multi-round tool calls reconstructed in subsequent send history`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val firstRoundCaptured = mutableListOf<List<LlmChatMessage>>()
        val multiRoundClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                firstRoundCaptured.add(messages)
                toolExecutor?.invoke(ToolCall(id = "r1-call", name = "get_user_profile", arguments = JsonObject(emptyMap())))
                onToolRoundCompleted?.invoke()
                toolExecutor?.invoke(ToolCall(id = "r2-call", name = "get_weight", arguments = JsonObject(emptyMap())))
                onToolRoundCompleted?.invoke()
                return LlmAnalysisResult("First response with data", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns multiRoundClient
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
        val firstResult = service.sendMessage(
            ChatRequest("conv-multi-replay", "Get my data", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(firstResult is ChatResult.Success)

        val secondRoundCaptured = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                secondRoundCaptured.add(messages)
                return LlmAnalysisResult("Follow-up response", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }
        every { factory.create(LlmProvider.OPENAI) } returns capturingClient

        val secondResult = service.sendMessage(
            ChatRequest("conv-multi-replay", "Tell me more", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(secondResult is ChatResult.Success)
        assertTrue(secondRoundCaptured.isNotEmpty())
        val history = secondRoundCaptured.first()

        val userMessages = history.filter { it.role == LlmChatRole.USER }
        assertTrue(userMessages.any { it.content == "Get my data" }, "Original user message must be in history")
        assertTrue(userMessages.any { it.content == "Tell me more" }, "Follow-up user message must be in history")

        val assistantWithToolCalls = history.filter { it.role == LlmChatRole.ASSISTANT && it.toolCalls != null }
        assertEquals(2, assistantWithToolCalls.size, "Both tool-round assistant messages must be in history")

        assertEquals("r1-call", assistantWithToolCalls[0].toolCalls?.first()?.id)
        assertEquals("get_user_profile", assistantWithToolCalls[0].toolCalls?.first()?.name)

        assertEquals("r2-call", assistantWithToolCalls[1].toolCalls?.first()?.id)
        assertEquals("get_weight", assistantWithToolCalls[1].toolCalls?.first()?.name)

        val toolResults = history.filter { it.role == LlmChatRole.TOOL }
        assertEquals(2, toolResults.size, "Both tool results must be in history")

        val toolResultIds = toolResults.map { it.toolCallId }
        assertEquals("r1-call", toolResultIds[0], "First TOOL result must reference r1-call")
        assertEquals("r2-call", toolResultIds[1], "Second TOOL result must reference r2-call")
        val toolResultNames = toolResults.map { it.toolName }
        assertEquals("get_user_profile", toolResultNames[0], "First TOOL result name must match first tool call")
        assertEquals("get_weight", toolResultNames[1], "Second TOOL result name must match second tool call")

        val assistantIdx = history.indexOfFirst { it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "r1-call" }
        val toolForR1Idx = history.indexOfFirst { it.role == LlmChatRole.TOOL && it.toolCallId == "r1-call" }
        val assistant2Idx = history.indexOfFirst { it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "r2-call" }
        val toolForR2Idx = history.indexOfFirst { it.role == LlmChatRole.TOOL && it.toolCallId == "r2-call" }
        val finalAssistantIdx = history.indexOfFirst { it.role == LlmChatRole.ASSISTANT && it.toolCalls == null && it.content == "First response with data" }

        assertTrue(assistantIdx >= 0, "First ASSISTANT(toolCalls) must exist")
        assertTrue(toolForR1Idx >= 0, "TOOL(result for r1-call) must exist")
        assertTrue(assistant2Idx >= 0, "Second ASSISTANT(toolCalls) must exist")
        assertTrue(toolForR2Idx >= 0, "TOOL(result for r2-call) must exist")
        assertTrue(finalAssistantIdx >= 0, "Final ASSISTANT(response) must exist")

        assertTrue(assistantIdx < toolForR1Idx, "ASSISTANT with r1-call must precede its TOOL result")
        assertTrue(toolForR1Idx < assistant2Idx, "TOOL for r1-call must precede second ASSISTANT(r2-call)")
        assertTrue(assistant2Idx < toolForR2Idx, "Second ASSISTANT must precede its TOOL result")
        assertTrue(toolForR2Idx < finalAssistantIdx, "TOOL for r2-call must precede final ASSISTANT response")
        assertTrue(finalAssistantIdx < history.indexOfFirst { it.role == LlmChatRole.USER && it.content == "Tell me more" },
            "Final ASSISTANT must precede new USER message")
    }

    @Test
    fun `cancelled turn excluded from subsequent send history`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val convId = chatRepo.createConversation(
            "conv-cancel-hist", "Prior chat", "openai", "gpt-4o-mini", now, now,
        )
        chatRepo.insertMessage(convId, ChatRole.USER, "Hello", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "Hi there!", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)

        val cancellingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = throw CancellationException()
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = throw CancellationException()
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ) = throw CancellationException()
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns cancellingClient
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
        val cancelRequest = ChatRequest("conv-cancel-hist", "Cancelled message", LlmProvider.OPENAI, "gpt-4o-mini")
        try {
            service.sendMessage(cancelRequest)
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // expected
        }

        val cancelledUser = chatRepo.messages.find { it.content == "Cancelled message" }
        assertNotNull(cancelledUser)
        assertEquals(ChatMessageStatus.ERROR, cancelledUser.status)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
                return LlmAnalysisResult("Sure!", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }
        every { factory.create(LlmProvider.OPENAI) } returns capturingClient

        val successRequest = ChatRequest("conv-cancel-hist", "Follow-up message", LlmProvider.OPENAI, "gpt-4o-mini")
        val successResult = service.sendMessage(successRequest)

        assertTrue(successResult is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        val cancelledContentInHistory = sentMessages.any { it.content == "Cancelled message" }
        assertFalse(cancelledContentInHistory, "Cancelled turn content must not appear in subsequent LLM history")

        val priorHelloInHistory = sentMessages.any { it.content == "Hello" }
        assertTrue(priorHelloInHistory, "Prior successfully completed messages must appear in LLM history")
    }

    @Test
    fun `CapturingToolExecutor creates error tool round when delegate fails`() = runTest {
        val chatRepo = FakeHealthChatRepository()

        val failingToolClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                toolExecutor?.invoke(ToolCall(id = "call1", name = "unknown_tool", arguments = JsonObject(emptyMap())))
                onToolRoundCompleted?.invoke()
                return LlmAnalysisResult("Response", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns failingToolClient
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
        val result = service.sendMessage(
            ChatRequest("conv-err-tool", "Get my data", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(result is ChatResult.Success)
        val toolMessages = chatRepo.messages.filter { it.role == ChatRole.TOOL }
        assertEquals(1, toolMessages.size)
        assertNotNull(toolMessages[0].toolResultJson)
        val json = Json { ignoreUnknownKeys = true }
        val persistedToolResult = json.decodeFromString<ToolResult>(toolMessages[0].toolResultJson!!)
        assertTrue(persistedToolResult.isError, "Persisted tool result should be an error")
    }

    @Test
    fun `toLlmChatMessage reconstructs primitive content from Gemini tool result`() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val toolResult = ToolResult(
            toolCallId = "gemini-call",
            name = "get_user_profile",
            content = JsonPrimitive("{\"name\":\"Test User\",\"age\":30}"),
        )
        val toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult)

        val chatRepo = FakeHealthChatRepository()
        val convId = chatRepo.createConversation("conv-gemini-msg", "Gemini", "gemini", "gemini-2.0-flash", now, now)
        chatRepo.insertMessage(convId, ChatRole.USER, "My profile?", now, "gemini", "gemini-2.0-flash", status = ChatMessageStatus.COMPLETED)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "gemini"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
                return LlmAnalysisResult("Response", LlmDiagnostics(model = "gemini-2.0-flash"))
            }
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.GEMINI) } returns capturingClient
        every { factory.hasApiKey(LlmProvider.GEMINI) } returns true
        every { factory.hasCurrentApiKey() } returns true

        val toolRegistry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
        )

        chatRepo.insertMessage(
            convId, ChatRole.ASSISTANT, "",
            now, "gemini", "gemini-2.0-flash",
            toolCallsJson = json.encodeToString(JsonElement.serializer(), buildJsonArray {
                add(buildJsonObject {
                    put("id", "gemini-call")
                    put("name", "get_user_profile")
                    put("arguments", JsonObject(emptyMap()))
                })
            }),
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.TOOL, "",
            now, "gemini", "gemini-2.0-flash",
            toolResultJson = toolResultJson,
            status = ChatMessageStatus.COMPLETED,
        )

        val service = HealthChatService(chatRepo, factory, toolRegistry)
        service.sendMessage(ChatRequest("conv-gemini-msg", "Tell me more", LlmProvider.GEMINI, "gemini-2.0-flash"))

        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        val toolInHistory = sentMessages.find { it.role == LlmChatRole.TOOL }
        assertNotNull(toolInHistory)
        assertEquals("gemini-call", toolInHistory.toolCallId)
        assertEquals("get_user_profile", toolInHistory.toolName)
        assertNotNull(toolInHistory.toolResultJson)
        assertTrue(toolInHistory.toolResultJson!!.contains("ok"), "Gemini wire format must contain ok field")
        assertTrue(toolInHistory.toolResultJson!!.contains("content"), "Gemini wire format must contain content field")
    }

    @Test
    fun `toLlmChatMessage reconstructs error tool result from persisted message`() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val toolResult = ToolResult(
            toolCallId = "err-call",
            name = "get_user_profile",
            content = JsonPrimitive("Tool execution failed."),
            isError = true,
        )
        val toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult)

        val chatRepo = FakeHealthChatRepository()
        val convId = chatRepo.createConversation("conv-err-result", "Error", "openai", "gpt-4o-mini", now, now)
        chatRepo.insertMessage(convId, ChatRole.USER, "Data?", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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

        chatRepo.insertMessage(
            convId, ChatRole.ASSISTANT, "",
            now, "openai", "gpt-4o-mini",
            toolCallsJson = json.encodeToString(JsonElement.serializer(), buildJsonArray {
                add(buildJsonObject {
                    put("id", "err-call")
                    put("name", "get_user_profile")
                    put("arguments", JsonObject(emptyMap()))
                })
            }),
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.TOOL, "",
            now, "openai", "gpt-4o-mini",
            toolResultJson = toolResultJson,
            status = ChatMessageStatus.COMPLETED,
        )

        val service = HealthChatService(chatRepo, factory, toolRegistry)
        service.sendMessage(ChatRequest("conv-err-result", "Tell me more", LlmProvider.OPENAI, "gpt-4o-mini"))

        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        val toolInHistory = sentMessages.find { it.role == LlmChatRole.TOOL }
        assertNotNull(toolInHistory)
        assertEquals("err-call", toolInHistory.toolCallId)
        assertNotNull(toolInHistory.toolResultJson)
        assertTrue(toolInHistory.toolResultJson!!.contains("\"ok\":false"), "Error tool result must produce ok=false in wire format")
    }

    @Test
    fun `complete-turn tool groups preserved when trimming history at exact boundary`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val convId = chatRepo.createConversation(
            "conv-trim-exact", "Trim exact", "openai", "gpt-4o-mini", now, now,
        )

        val toolCallsJson = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            add(buildJsonObject {
                put("id", "boundary-call")
                put("name", "get_user_profile")
                put("arguments", JsonObject(emptyMap()))
            })
        })
        val toolResult = ToolResult(toolCallId = "boundary-call", name = "get_user_profile", content = JsonPrimitive("data"))

        for (i in 1..(HealthChatService.MAX_HISTORY_MESSAGES - 1)) {
            chatRepo.insertMessage(
                convId, ChatRole.USER, "User $i",
                now, "openai", "gpt-4o-mini",
                status = ChatMessageStatus.COMPLETED,
            )
        }
        val assistantToolMsgId = chatRepo.insertMessage(
            convId, ChatRole.ASSISTANT, "",
            now, "openai", "gpt-4o-mini",
            toolCallsJson = toolCallsJson,
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.TOOL, "",
            now, "openai", "gpt-4o-mini",
            toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult),
            status = ChatMessageStatus.COMPLETED,
        )

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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
        val result = service.sendMessage(
            ChatRequest("conv-trim-exact", "Final message", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        val sentAssistantTool = sentMessages.find {
            it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "boundary-call"
        }
        assertNotNull(sentAssistantTool, "Boundary assistant(toolCalls) must be preserved")
        val sentToolResult = sentMessages.find { it.role == LlmChatRole.TOOL && it.toolCallId == "boundary-call" }
        assertNotNull(sentToolResult, "Boundary TOOL result must be preserved")

        val assistantIdx = sentMessages.indexOf(sentAssistantTool)
        val toolIdx = sentMessages.indexOf(sentToolResult)
        assertTrue(assistantIdx < toolIdx, "ASSISTANT(toolCalls) must precede its TOOL result in trimmed history")
        assertFalse(sentMessages.any { it.content == "User 1" }, "Earliest user message should be trimmed")
    }

    @Test
    fun `messages with unknown status are excluded from LLM history`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val convId = chatRepo.createConversation(
            "conv-unknown-status", "Unknown", "openai", "gpt-4o-mini", now, now,
        )
        chatRepo.insertMessage(convId, ChatRole.USER, "Completed hello", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "Completed response", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.USER, "Error message", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.ERROR)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "Error response", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.ERROR)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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
        val result = service.sendMessage(ChatRequest("conv-unknown-status", "New message", LlmProvider.OPENAI, "gpt-4o-mini"))

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        assertTrue(sentMessages.any { it.content == "Completed hello" }, "COMPLETED messages must be in history")
        assertTrue(sentMessages.any { it.content == "Completed response" }, "COMPLETED assistant must be in history")
        assertFalse(sentMessages.any { it.content == "Error message" }, "ERROR messages must be excluded from history")
        assertFalse(sentMessages.any { it.content == "Error response" }, "ERROR assistant must be excluded from history")
    }

    @Test
    fun `single round tool calls reconstructed with exact ordering in subsequent send history`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val toolCall1 = ToolCall(id = "single-call", name = "get_user_profile", arguments = JsonObject(emptyMap()))

        val toolUsingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                toolExecutor?.invoke(toolCall1)
                return LlmAnalysisResult("Profile info", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }

        val factory = mockk<LlmClientFactory>()
        every { factory.create(LlmProvider.OPENAI) } returns toolUsingClient
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
        val firstResult = service.sendMessage(
            ChatRequest("conv-single-replay", "Get my profile", LlmProvider.OPENAI, "gpt-4o-mini")
        )
        assertTrue(firstResult is ChatResult.Success)

        val secondRoundCaptured = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                secondRoundCaptured.add(messages)
                return LlmAnalysisResult("Follow-up", LlmDiagnostics(model = "gpt-4o-mini"))
            }
        }
        every { factory.create(LlmProvider.OPENAI) } returns capturingClient

        val secondResult = service.sendMessage(
            ChatRequest("conv-single-replay", "Tell me more", LlmProvider.OPENAI, "gpt-4o-mini")
        )
        assertTrue(secondResult is ChatResult.Success)
        assertTrue(secondRoundCaptured.isNotEmpty())
        val history = secondRoundCaptured.first()

        val singleAssistantTool = history.filter { it.role == LlmChatRole.ASSISTANT && it.toolCalls != null }
        assertEquals(1, singleAssistantTool.size, "Single tool-round assistant message must be in history")
        assertEquals("single-call", singleAssistantTool[0].toolCalls?.first()?.id)

        val toolResults = history.filter { it.role == LlmChatRole.TOOL }
        assertEquals(1, toolResults.size, "Single tool result must be in history")
        assertEquals("single-call", toolResults[0].toolCallId, "Tool result callId must match tool call id")

        val assistantIdx = history.indexOfFirst { it.role == LlmChatRole.ASSISTANT && it.toolCalls != null }
        val toolIdx = history.indexOfFirst { it.role == LlmChatRole.TOOL }
        assertTrue(assistantIdx >= 0 && toolIdx >= 0, "Both assistant and tool must be present")
        assertTrue(assistantIdx < toolIdx, "ASSISTANT(toolCalls) must precede TOOL result in history")
    }

    @Test
    fun `multi-result tool round preserved when trim cuts at second tool result`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val convId = chatRepo.createConversation(
            "conv-multi-result-trim", "Multi result trim", "openai", "gpt-4o-mini", now, now,
        )

        val toolCallsJson = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            add(buildJsonObject {
                put("id", "multi-call")
                put("name", "get_user_profile")
                put("arguments", JsonObject(emptyMap()))
            })
        })
        val toolResult1 = ToolResult(toolCallId = "multi-call", name = "get_user_profile", content = JsonPrimitive("profile data"))
        val toolResult2 = ToolResult(toolCallId = "multi-call", name = "get_user_profile", content = JsonPrimitive("additional data"))

        chatRepo.insertMessage(
            convId, ChatRole.USER, "Filler",
            now, "openai", "gpt-4o-mini",
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.ASSISTANT, "",
            now, "openai", "gpt-4o-mini",
            toolCallsJson = toolCallsJson,
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.TOOL, "",
            now, "openai", "gpt-4o-mini",
            toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult1),
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.TOOL, "",
            now, "openai", "gpt-4o-mini",
            toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult2),
            status = ChatMessageStatus.COMPLETED,
        )

        for (i in 1..48) {
            chatRepo.insertMessage(
                convId, ChatRole.USER, "Pad $i",
                now, "openai", "gpt-4o-mini",
                status = ChatMessageStatus.COMPLETED,
            )
        }

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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
        val result = service.sendMessage(
            ChatRequest("conv-multi-result-trim", "Final message", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        val assistantTool = sentMessages.find {
            it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "multi-call"
        }
        assertNotNull(assistantTool, "ASSISTANT(toolCalls) must be preserved when trim cuts at second TOOL result")

        val toolResults = sentMessages.filter { it.role == LlmChatRole.TOOL }
        assertEquals(2, toolResults.size, "Both TOOL results from the multi-result round must be preserved")
        assertEquals("multi-call", toolResults[0].toolCallId, "First TOOL result must reference multi-call")
        assertEquals("multi-call", toolResults[1].toolCallId, "Second TOOL result must reference multi-call")

        val assistantIdx = sentMessages.indexOfFirst { it.role == LlmChatRole.ASSISTANT && it.toolCalls != null }
        val tool1Idx = sentMessages.indexOfFirst { it.role == LlmChatRole.TOOL }
        val tool2Idx = sentMessages.indexOfLast { it.role == LlmChatRole.TOOL }

        assertTrue(assistantIdx >= 0, "ASSISTANT(toolCalls) must be present")
        assertTrue(tool1Idx >= 0, "First TOOL result must be present")
        assertTrue(tool2Idx >= 0, "Second TOOL result must be present")
        assertTrue(assistantIdx < tool1Idx, "ASSISTANT(toolCalls) must precede first TOOL result")
        assertTrue(tool1Idx < tool2Idx, "First TOOL result must precede second TOOL result")

        assertFalse(sentMessages.any { it.content == "Filler" }, "Earliest filler message should be trimmed")
    }

    @Test
    fun `two adjacent tool rounds when trim cuts within second round preserves only that round`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val convId = chatRepo.createConversation(
            "conv-adjacent-rounds", "Adjacent rounds", "openai", "gpt-4o-mini", now, now,
        )

        val round1ToolCalls = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            add(buildJsonObject {
                put("id", "round1-call")
                put("name", "get_user_profile")
                put("arguments", JsonObject(emptyMap()))
            })
        })
        val round2ToolCalls = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            add(buildJsonObject {
                put("id", "round2-call")
                put("name", "get_user_profile")
                put("arguments", JsonObject(emptyMap()))
            })
        })
        val round1Result = ToolResult(toolCallId = "round1-call", name = "get_user_profile", content = JsonPrimitive("round1 data"))
        val round2Result1 = ToolResult(toolCallId = "round2-call", name = "get_user_profile", content = JsonPrimitive("round2 data"))
        val round2Result2 = ToolResult(toolCallId = "round2-call", name = "get_user_profile", content = JsonPrimitive("round2 extra"))

        chatRepo.insertMessage(
            convId, ChatRole.USER, "Round 1 user",
            now, "openai", "gpt-4o-mini",
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.ASSISTANT, "",
            now, "openai", "gpt-4o-mini",
            toolCallsJson = round1ToolCalls,
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.TOOL, "",
            now, "openai", "gpt-4o-mini",
            toolResultJson = json.encodeToString(ToolResult.serializer(), round1Result),
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.ASSISTANT, "",
            now, "openai", "gpt-4o-mini",
            toolCallsJson = round2ToolCalls,
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.TOOL, "",
            now, "openai", "gpt-4o-mini",
            toolResultJson = json.encodeToString(ToolResult.serializer(), round2Result1),
            status = ChatMessageStatus.COMPLETED,
        )
        chatRepo.insertMessage(
            convId, ChatRole.TOOL, "",
            now, "openai", "gpt-4o-mini",
            toolResultJson = json.encodeToString(ToolResult.serializer(), round2Result2),
            status = ChatMessageStatus.COMPLETED,
        )

        for (i in 1..48) {
            chatRepo.insertMessage(
                convId, ChatRole.USER, "Pad $i",
                now, "openai", "gpt-4o-mini",
                status = ChatMessageStatus.COMPLETED,
            )
        }

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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
        val result = service.sendMessage(
            ChatRequest("conv-adjacent-rounds", "Final message", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        val round2Assistant = sentMessages.find {
            it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "round2-call"
        }
        assertNotNull(round2Assistant, "Round 2 ASSISTANT(toolCalls) must be preserved when trim cuts at second TOOL result")

        val toolResults = sentMessages.filter { it.role == LlmChatRole.TOOL }
        assertEquals(2, toolResults.size, "Both TOOL results from round 2 must be preserved")
        assertEquals("round2-call", toolResults[0].toolCallId, "First TOOL result must reference round2-call")
        assertEquals("round2-call", toolResults[1].toolCallId, "Second TOOL result must reference round2-call")

        val assistantIdx = sentMessages.indexOfFirst {
            it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "round2-call"
        }
        val tool1Idx = sentMessages.indexOfFirst { m -> m.role == LlmChatRole.TOOL && m.toolCallId == "round2-call" }
        val tool2Idx = sentMessages.indexOfLast { m -> m.role == LlmChatRole.TOOL && m.toolCallId == "round2-call" }

        assertTrue(assistantIdx >= 0, "Round 2 ASSISTANT(toolCalls) must be present")
        assertTrue(tool1Idx >= 0, "First TOOL result must be present")
        assertTrue(tool2Idx >= 0, "Second TOOL result must be present")
        assertTrue(assistantIdx < tool1Idx, "Round 2 ASSISTANT must precede its first TOOL result")
        assertTrue(tool1Idx < tool2Idx, "Round 2 TOOL results must be in order")

        assertNull(
            sentMessages.find { it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "round1-call" },
            "Round 1 ASSISTANT(toolCalls) must be excluded from trimmed history",
        )
        assertFalse(
            sentMessages.any { it.content == "Round 1 user" },
            "Round 1 user message must be excluded from trimmed history",
        )
    }

    @Test
    fun `multi-result round with three results when trim boundary lands at first tool result preserves complete round`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val convId = chatRepo.createConversation(
            "conv-mr-first", "MR first", "openai", "gpt-4o-mini", now, now,
        )

        val toolCallsJson = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            add(buildJsonObject {
                put("id", "mr-first-call")
                put("name", "get_user_profile")
                put("arguments", JsonObject(emptyMap()))
            })
        })
        val toolResult1 = ToolResult(toolCallId = "mr-first-call", name = "get_user_profile", content = JsonPrimitive("data 1"))
        val toolResult2 = ToolResult(toolCallId = "mr-first-call", name = "get_user_profile", content = JsonPrimitive("data 2"))
        val toolResult3 = ToolResult(toolCallId = "mr-first-call", name = "get_user_profile", content = JsonPrimitive("data 3"))

        chatRepo.insertMessage(convId, ChatRole.USER, "Filler", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "", now, "openai", "gpt-4o-mini", toolCallsJson = toolCallsJson, status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult1), status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult2), status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult3), status = ChatMessageStatus.COMPLETED)

        for (i in 1..46) {
            chatRepo.insertMessage(convId, ChatRole.USER, "Pad $i", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        }

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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
        val result = service.sendMessage(
            ChatRequest("conv-mr-first", "Final message", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        assertTrue(sentMessages.isNotEmpty())
        assertEquals(LlmChatRole.ASSISTANT, sentMessages.first().role, "Trimmed history must start with ASSISTANT(toolCalls)")
        assertEquals("mr-first-call", sentMessages.first().toolCalls?.first()?.id, "Trim boundary landed at first TOOL result; ASSISTANT must be first retained message")

        val assistantTool = sentMessages.find { it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "mr-first-call" }
        assertNotNull(assistantTool, "ASSISTANT(toolCalls) must be preserved when boundary lands on first TOOL result")

        val toolResults = sentMessages.filter { it.role == LlmChatRole.TOOL }
        assertEquals(3, toolResults.size, "All three TOOL results must be preserved")
        toolResults.forEach { assertEquals("mr-first-call", it.toolCallId, "Every TOOL result must reference the initiating call") }

        val assistantIdx = sentMessages.indexOfFirst { it.role == LlmChatRole.ASSISTANT && it.toolCalls != null }
        val firstToolIdx = sentMessages.indexOfFirst { it.role == LlmChatRole.TOOL }
        assertTrue(assistantIdx < firstToolIdx, "ASSISTANT(toolCalls) must precede first TOOL result")

        assertFalse(sentMessages.any { it.content == "Filler" }, "Filler must be excluded from trimmed history")
    }

    @Test
    fun `multi-result round with three results when trim boundary lands at middle tool result preserves complete round`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val convId = chatRepo.createConversation(
            "conv-mr-middle", "MR middle", "openai", "gpt-4o-mini", now, now,
        )

        val toolCallsJson = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            add(buildJsonObject {
                put("id", "mr-middle-call")
                put("name", "get_user_profile")
                put("arguments", JsonObject(emptyMap()))
            })
        })
        val toolResult1 = ToolResult(toolCallId = "mr-middle-call", name = "get_user_profile", content = JsonPrimitive("data 1"))
        val toolResult2 = ToolResult(toolCallId = "mr-middle-call", name = "get_user_profile", content = JsonPrimitive("data 2"))
        val toolResult3 = ToolResult(toolCallId = "mr-middle-call", name = "get_user_profile", content = JsonPrimitive("data 3"))

        chatRepo.insertMessage(convId, ChatRole.USER, "Filler", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "", now, "openai", "gpt-4o-mini", toolCallsJson = toolCallsJson, status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult1), status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult2), status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult3), status = ChatMessageStatus.COMPLETED)

        for (i in 1..47) {
            chatRepo.insertMessage(convId, ChatRole.USER, "Pad $i", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        }

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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
        val result = service.sendMessage(
            ChatRequest("conv-mr-middle", "Final message", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        assertTrue(sentMessages.isNotEmpty())
        assertEquals(LlmChatRole.ASSISTANT, sentMessages.first().role, "Trimmed history must start with ASSISTANT(toolCalls)")
        assertEquals("mr-middle-call", sentMessages.first().toolCalls?.first()?.id, "Trim boundary landed at middle TOOL result; ASSISTANT must be first retained message")

        val assistantTool = sentMessages.find { it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "mr-middle-call" }
        assertNotNull(assistantTool, "ASSISTANT(toolCalls) must be preserved when boundary lands on middle TOOL result")

        val toolResults = sentMessages.filter { it.role == LlmChatRole.TOOL }
        assertEquals(3, toolResults.size, "All three TOOL results must be preserved")
        toolResults.forEach { assertEquals("mr-middle-call", it.toolCallId, "Every TOOL result must reference the initiating call") }

        val assistantIdx = sentMessages.indexOfFirst { it.role == LlmChatRole.ASSISTANT && it.toolCalls != null }
        val firstToolIdx = sentMessages.indexOfFirst { it.role == LlmChatRole.TOOL }
        assertTrue(assistantIdx < firstToolIdx, "ASSISTANT(toolCalls) must precede first TOOL result")

        assertFalse(sentMessages.any { it.content == "Filler" }, "Filler must be excluded from trimmed history")
    }

    @Test
    fun `adjacent rounds with multi results when trim boundary lands at first tool of second round preserves only that round`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val convId = chatRepo.createConversation(
            "conv-ar-first", "AR first", "openai", "gpt-4o-mini", now, now,
        )

        val round1ToolCalls = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            add(buildJsonObject {
                put("id", "round1-call")
                put("name", "get_user_profile")
                put("arguments", JsonObject(emptyMap()))
            })
        })
        val round2ToolCalls = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            add(buildJsonObject {
                put("id", "round2-call")
                put("name", "get_user_profile")
                put("arguments", JsonObject(emptyMap()))
            })
        })

        val round1Result = ToolResult(toolCallId = "round1-call", name = "get_user_profile", content = JsonPrimitive("r1 data"))
        val round2Result1 = ToolResult(toolCallId = "round2-call", name = "get_user_profile", content = JsonPrimitive("r2 data 1"))
        val round2Result2 = ToolResult(toolCallId = "round2-call", name = "get_user_profile", content = JsonPrimitive("r2 data 2"))
        val round2Result3 = ToolResult(toolCallId = "round2-call", name = "get_user_profile", content = JsonPrimitive("r2 data 3"))

        chatRepo.insertMessage(convId, ChatRole.USER, "Round 1 user", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "", now, "openai", "gpt-4o-mini", toolCallsJson = round1ToolCalls, status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), round1Result), status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "", now, "openai", "gpt-4o-mini", toolCallsJson = round2ToolCalls, status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), round2Result1), status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), round2Result2), status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), round2Result3), status = ChatMessageStatus.COMPLETED)

        for (i in 1..46) {
            chatRepo.insertMessage(convId, ChatRole.USER, "Pad $i", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        }

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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
        val result = service.sendMessage(
            ChatRequest("conv-ar-first", "Final message", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        assertTrue(sentMessages.isNotEmpty())
        assertEquals(LlmChatRole.ASSISTANT, sentMessages.first().role, "Trimmed history must start with Round 2 ASSISTANT(toolCalls)")
        assertEquals("round2-call", sentMessages.first().toolCalls?.first()?.id, "Trim boundary landed at first TOOL of second round; Round 2 ASSISTANT must be first retained message")

        val round2Assistant = sentMessages.find { it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "round2-call" }
        assertNotNull(round2Assistant, "Round 2 ASSISTANT(toolCalls) must be preserved when boundary lands on first TOOL of second round")

        val round1Assistant = sentMessages.find { it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "round1-call" }
        assertNull(round1Assistant, "Round 1 ASSISTANT(toolCalls) must be excluded")

        val toolResults = sentMessages.filter { it.role == LlmChatRole.TOOL }
        assertEquals(3, toolResults.size, "All three TOOL results from round 2 must be preserved")
        toolResults.forEach { assertEquals("round2-call", it.toolCallId, "Every TOOL result must reference round2-call") }

        val assistantIdx = sentMessages.indexOfFirst { it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "round2-call" }
        val firstToolIdx = sentMessages.indexOfFirst { it.role == LlmChatRole.TOOL }
        assertTrue(assistantIdx < firstToolIdx, "Round 2 ASSISTANT must precede its first TOOL result")

        assertFalse(sentMessages.any { it.content == "Round 1 user" }, "Round 1 user message must be excluded")
    }

    @Test
    fun `adjacent rounds with multi results when trim boundary lands at middle tool of second round preserves only that round`() = runTest {
        val chatRepo = FakeHealthChatRepository()
        val json = Json { ignoreUnknownKeys = true }

        val convId = chatRepo.createConversation(
            "conv-ar-middle", "AR middle", "openai", "gpt-4o-mini", now, now,
        )

        val round1ToolCalls = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            add(buildJsonObject {
                put("id", "round1-call")
                put("name", "get_user_profile")
                put("arguments", JsonObject(emptyMap()))
            })
        })
        val round2ToolCalls = json.encodeToString(JsonElement.serializer(), buildJsonArray {
            add(buildJsonObject {
                put("id", "round2-call")
                put("name", "get_user_profile")
                put("arguments", JsonObject(emptyMap()))
            })
        })

        val round1Result = ToolResult(toolCallId = "round1-call", name = "get_user_profile", content = JsonPrimitive("r1 data"))
        val round2Result1 = ToolResult(toolCallId = "round2-call", name = "get_user_profile", content = JsonPrimitive("r2 data 1"))
        val round2Result2 = ToolResult(toolCallId = "round2-call", name = "get_user_profile", content = JsonPrimitive("r2 data 2"))
        val round2Result3 = ToolResult(toolCallId = "round2-call", name = "get_user_profile", content = JsonPrimitive("r2 data 3"))

        chatRepo.insertMessage(convId, ChatRole.USER, "Round 1 user", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "", now, "openai", "gpt-4o-mini", toolCallsJson = round1ToolCalls, status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), round1Result), status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.ASSISTANT, "", now, "openai", "gpt-4o-mini", toolCallsJson = round2ToolCalls, status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), round2Result1), status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), round2Result2), status = ChatMessageStatus.COMPLETED)
        chatRepo.insertMessage(convId, ChatRole.TOOL, "", now, "openai", "gpt-4o-mini", toolResultJson = json.encodeToString(ToolResult.serializer(), round2Result3), status = ChatMessageStatus.COMPLETED)

        for (i in 1..47) {
            chatRepo.insertMessage(convId, ChatRole.USER, "Pad $i", now, "openai", "gpt-4o-mini", status = ChatMessageStatus.COMPLETED)
        }

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturingClient = object : LlmClient {
            override val providerId: String get() = "openai"
            override suspend fun analyzeImage(imageBytes: ByteArray, prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun transcribeAudio(imageBytes: ByteArray, mimeType: String) = ""
            override suspend fun generateCompletion(prompt: String, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?) = LlmAnalysisResult("ok", LlmDiagnostics())
            override suspend fun generateChatResponse(messages: List<LlmChatMessage>, systemInstruction: String?, jsonSchema: String?, tools: List<ToolDefinition>, toolExecutor: ToolExecutor?,
                onToolRoundCompleted: (() -> Unit)?,
            ): LlmAnalysisResult {
                capturedMessages.add(messages)
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
        val result = service.sendMessage(
            ChatRequest("conv-ar-middle", "Final message", LlmProvider.OPENAI, "gpt-4o-mini")
        )

        assertTrue(result is ChatResult.Success)
        assertTrue(capturedMessages.isNotEmpty())
        val sentMessages = capturedMessages.first()

        assertTrue(sentMessages.isNotEmpty())
        assertEquals(LlmChatRole.ASSISTANT, sentMessages.first().role, "Trimmed history must start with Round 2 ASSISTANT(toolCalls)")
        assertEquals("round2-call", sentMessages.first().toolCalls?.first()?.id, "Trim boundary landed at middle TOOL of second round; Round 2 ASSISTANT must be first retained message")

        val round2Assistant = sentMessages.find { it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "round2-call" }
        assertNotNull(round2Assistant, "Round 2 ASSISTANT(toolCalls) must be preserved when boundary lands on middle TOOL of second round")

        val round1Assistant = sentMessages.find { it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "round1-call" }
        assertNull(round1Assistant, "Round 1 ASSISTANT(toolCalls) must be excluded")

        val toolResults = sentMessages.filter { it.role == LlmChatRole.TOOL }
        assertEquals(3, toolResults.size, "All three TOOL results from round 2 must be preserved")
        toolResults.forEach { assertEquals("round2-call", it.toolCallId, "Every TOOL result must reference round2-call") }

        val assistantIdx = sentMessages.indexOfFirst { it.role == LlmChatRole.ASSISTANT && it.toolCalls?.first()?.id == "round2-call" }
        val firstToolIdx = sentMessages.indexOfFirst { it.role == LlmChatRole.TOOL }
        assertTrue(assistantIdx < firstToolIdx, "Round 2 ASSISTANT must precede its first TOOL result")

        assertFalse(sentMessages.any { it.content == "Round 1 user" }, "Round 1 user message must be excluded")
    }
}
