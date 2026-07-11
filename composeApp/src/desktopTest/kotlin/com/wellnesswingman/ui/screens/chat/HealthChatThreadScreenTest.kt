package com.wellnesswingman.ui.screens.chat

import androidx.compose.ui.test.junit4.runComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
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
import com.wellnesswingman.domain.chat.ChatConversationResult
import com.wellnesswingman.domain.chat.ChatRequest
import com.wellnesswingman.domain.chat.ChatResult
import com.wellnesswingman.domain.chat.HealthChatService
import com.wellnesswingman.domain.llm.LlmAnalysisResult
import com.wellnesswingman.domain.llm.LlmClient
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.LlmDiagnostics
import com.wellnesswingman.domain.llm.ToolExecutor
import com.wellnesswingman.domain.llm.ToolRegistry
import com.wellnesswingman.domain.migration.DataMigrationService
import com.wellnesswingman.domain.migration.ImportResult
import com.wellnesswingman.platform.DiagnosticShare
import com.wellnesswingman.platform.ShareUtil
import com.wellnesswingman.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.compose.KoinContext
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HealthChatThreadScreenTest {

    @Test
    fun `ApiKeyMissing state displays instruction text and both buttons`() = runComposeUiTest {
        setContent {
            ApiKeyMissingState(
                onOpenSettings = {},
                onRetry = {},
            )
        }
        onNodeWithText("Set an API key for the selected provider before starting a health chat.").assertExists()
        onNodeWithText("Open LLM settings").assertExists()
        onNodeWithText("I've set my API key").assertExists()
    }

    @Test
    fun `clicking Open LLM settings triggers callback`() = runComposeUiTest {
        var settingsClicked = false
        setContent {
            ApiKeyMissingState(
                onOpenSettings = { settingsClicked = true },
                onRetry = {},
            )
        }
        onNodeWithText("Open LLM settings").performClick()
        assertTrue(settingsClicked)
    }

    @Test
    fun `clicking I've set my API key triggers retry callback`() = runComposeUiTest {
        var retryClicked = false
        setContent {
            ApiKeyMissingState(
                onOpenSettings = {},
                onRetry = { retryClicked = true },
            )
        }
        onNodeWithText("I've set my API key").performClick()
        assertTrue(retryClicked)
    }

    @Test
    fun `settings return recovery restores thread state and retains draft`() = runComposeUiTest {
        val appSettings = RecoveryAppSettings(apiKey = null)
        val chatRepo = RecoveryHealthChatRepo()
        val service = RecoveryChatService(appSettings, hasKey = false)
        val settingsRepo = appSettings
        val viewModelRef = arrayOf<HealthChatThreadViewModel?>(null)

        val testModule = module {
            single<AppSettingsRepository> { settingsRepo }
            single<HealthChatRepository> { chatRepo }
            single<HealthChatService> { service }
            factory<DataMigrationService> { StubDataMigrationService() }
            single<WeightHistoryRepository> { StubWeightHistoryRepository() }
            single<TrackedEntryRepository> { StubTrackedEntryRepository() }
            single<EntryAnalysisRepository> { StubEntryAnalysisRepository() }
            single<NutritionalProfileRepository> { StubNutritionalProfileRepository() }
            factory { DiagnosticShare() }
            factory { ShareUtil() }
            factory<HealthChatThreadViewModel> { params ->
                val vm = HealthChatThreadViewModel(
                    conversationExternalId = params.get(),
                    healthChatService = get(),
                    healthChatRepository = get(),
                    settingsRepository = get(),
                )
                viewModelRef[0] = vm
                vm
            }
            factoryOf(::SettingsViewModel)
        }

        setContent {
            KoinContext(module = testModule) {
                Navigator(HealthChatThreadScreen(conversationExternalId = "conv-recovery")) { navigator ->
                    SlideTransition(navigator)
                }
            }
        }

        waitForIdle()

        val viewModel = viewModelRef[0] ?: error("ViewModel not created")

        viewModel.updateDraft("What do you recommend for breakfast?")
        viewModel.send()
        waitForIdle()

        assertIs<HealthChatThreadUiState.ApiKeyMissing>(viewModel.uiState.value)
        onNodeWithText("Open LLM settings").assertExists()

        onNodeWithText("Open LLM settings").performClick()
        waitForIdle()

        onNodeWithText("sk-...").performTextInput("sk-recovered")
        onNodeWithText("Save LLM Settings").performClick()
        waitForIdle()

        assertIs<HealthChatThreadUiState.Success>(viewModel.uiState.value)
        onNodeWithText("Send").assertExists()
        onNodeWithText("What do you recommend for breakfast?").assertExists()
    }
}

private class RecoveryAppSettings(var apiKey: String? = null) : AppSettingsRepository {
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

private class RecoveryHealthChatRepo : HealthChatRepository {
    private val conversation = HealthChatConversation(
        conversationId = 1L,
        externalId = "conv-recovery",
        title = "Test",
        provider = null,
        model = null,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )
    private val messages = listOf(
        HealthChatMessage(
            messageId = 1L,
            conversationId = 1L,
            role = ChatRole.USER,
            content = "Hello",
            createdAt = Instant.fromEpochMilliseconds(0),
            provider = null,
            model = null,
            toolCallsJson = null,
            toolResultJson = null,
            status = ChatMessageStatus.COMPLETED,
        ),
    )

    override suspend fun <T> transaction(block: (TransactionScope) -> T): T =
        throw UnsupportedOperationException()
    override suspend fun createConversation(externalId: String, title: String, provider: String?, model: String?, createdAt: Instant, updatedAt: Instant): Long = 1L
    override suspend fun getAllConversations(): List<HealthChatConversation> = listOf(conversation)
    override suspend fun getConversationById(id: Long): HealthChatConversation? = if (id == 1L) conversation else null
    override suspend fun getConversationByExternalId(externalId: String): HealthChatConversation? =
        if (externalId == "conv-recovery") conversation else null
    override suspend fun renameConversation(id: Long, title: String, updatedAt: Instant) {}
    override suspend fun deleteConversation(id: Long) {}
    override suspend fun touchConversation(id: Long, updatedAt: Instant) {}
    override suspend fun getMessagesForConversation(conversationId: Long): List<HealthChatMessage> = messages
    override suspend fun insertMessage(conversationId: Long, role: ChatRole, content: String, createdAt: Instant, provider: String?, model: String?, toolCallsJson: String?, toolResultJson: String?, status: ChatMessageStatus): Long = 2L
    override suspend fun updateMessageStatus(messageId: Long, status: ChatMessageStatus) {}
    override suspend fun updateAssistantMessage(messageId: Long, content: String, toolCallsJson: String?, model: String?, status: ChatMessageStatus) {}
    override suspend fun deleteMessage(messageId: Long) {}
    override suspend fun getConversationCount(): Long = 1L
    override suspend fun getMessageCountForConversation(conversationId: Long): Long = messages.size.toLong()
}

private class RecoveryChatService(
    appSettings: AppSettingsRepository,
    var hasKey: Boolean = false,
) : HealthChatService(
    healthChatRepository = RecoveryHealthChatRepo(),
    llmClientFactory = object : LlmClientFactory(appSettings) {
        override fun create(provider: LlmProvider): LlmClient = RecoveryLlmClient()
        override fun hasApiKey(provider: LlmProvider): Boolean = hasKey
        override fun hasCurrentApiKey(): Boolean = hasKey
    },
    toolRegistry = ToolRegistry(
        trackedEntryRepository = StubTrackedEntryRepository(),
        entryAnalysisRepository = StubEntryAnalysisRepository(),
        weightHistoryRepository = StubWeightHistoryRepository(),
        appSettingsRepository = appSettings,
        nutritionalProfileRepository = StubNutritionalProfileRepository(),
    ),
) {
    private val conversation = HealthChatConversation(
        conversationId = 1L,
        externalId = "conv-recovery",
        title = "Test",
        provider = null,
        model = null,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    override suspend fun getConversation(conversationExternalId: String): ChatConversationResult =
        ChatConversationResult.Found(conversation)

    override suspend fun sendMessage(request: ChatRequest): ChatResult {
        if (!hasKey) return ChatResult.ApiKeyMissing
        val now = Clock.System.now()
        return ChatResult.Success(
            conversation = conversation,
            assistantMessage = HealthChatMessage(
                messageId = 2L,
                conversationId = 1L,
                role = ChatRole.ASSISTANT,
                content = "Try oatmeal with berries!",
                createdAt = now,
                provider = null,
                model = null,
                toolCallsJson = null,
                toolResultJson = null,
                status = ChatMessageStatus.COMPLETED,
            ),
            diagnostics = LlmDiagnostics(model = "gpt-4o-mini"),
        )
    }
}

private class RecoveryLlmClient : LlmClient {
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
        onToolRoundCompleted: (() -> Unit)?,
    ) = LlmAnalysisResult("Response from LLM", LlmDiagnostics(model = "gpt-4o-mini"))
}

private class StubTrackedEntryRepository : TrackedEntryRepository {
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

private class StubEntryAnalysisRepository : EntryAnalysisRepository {
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

private class StubWeightHistoryRepository : WeightHistoryRepository {
    override suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<com.wellnesswingman.data.model.WeightRecord> = emptyList()
    override suspend fun addWeightRecord(record: com.wellnesswingman.data.model.WeightRecord) = 1L
    override suspend fun getLatestWeightRecord() = null
    override suspend fun getAllWeightRecords(): List<com.wellnesswingman.data.model.WeightRecord> = emptyList()
    override suspend fun deleteWeightRecord(recordId: Long) {}
    override suspend fun nullifyRelatedEntryId(entryId: Long) {}
    override suspend fun upsertWeightRecord(record: com.wellnesswingman.data.model.WeightRecord) {}
}

private class StubNutritionalProfileRepository : NutritionalProfileRepository {
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

private class StubDataMigrationService : DataMigrationService {
    override suspend fun exportData(): String = ""
    override suspend fun importData(zipFilePath: String): ImportResult = ImportResult()
}
