package com.wellnesswingman.ui.screens.checkin

import com.wellnesswingman.data.model.CheckInInputSource
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.DailyCheckInRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** The view model works in "today", so the expected ids follow the clock. */
    private val today: LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val expectedConversationId = "checkin-$today-morning"

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeDailyCheckInRepository : DailyCheckInRepository {
        private val stored = mutableMapOf<Pair<LocalDate, CheckInSlot>, DailyCheckIn>()
        val saved = mutableListOf<DailyCheckIn>()
        val attached = mutableListOf<Triple<LocalDate, CheckInSlot, String>>()

        fun seed(checkIn: DailyCheckIn) {
            stored[checkIn.checkInDate to checkIn.slot] = checkIn
        }

        override suspend fun getAllCheckIns() = stored.values.toList()
        override suspend fun getCheckInsForDate(date: LocalDate) =
            stored.values.filter { it.checkInDate == date }
        override suspend fun getCheckIn(date: LocalDate, slot: CheckInSlot) = stored[date to slot]
        override suspend fun getCheckInsForDateRange(startDate: LocalDate, endDate: LocalDate) =
            stored.values.toList()
        override suspend fun getCheckInByExternalId(externalId: String): DailyCheckIn? = null
        override suspend fun saveCheckIn(checkIn: DailyCheckIn): Long {
            saved += checkIn
            stored[checkIn.checkInDate to checkIn.slot] = checkIn
            return 1L
        }
        override suspend fun attachConversation(
            date: LocalDate,
            slot: CheckInSlot,
            conversationExternalId: String
        ) {
            attached += Triple(date, slot, conversationExternalId)
        }
        override suspend fun deleteCheckIn(date: LocalDate, slot: CheckInSlot) {}
        override suspend fun deleteOldCheckIns(beforeDate: LocalDate) {}
        override suspend fun upsertCheckIn(checkIn: DailyCheckIn) {}
    }

    private class RecordingConversationStarter(
        private val result: CheckInConversationResult = CheckInConversationResult.Started
    ) : CheckInConversationStarter {
        data class Call(val externalId: String, val openingMessage: String, val title: String)

        val calls = mutableListOf<Call>()

        override suspend fun start(
            conversationExternalId: String,
            openingMessage: String,
            title: String
        ): CheckInConversationResult {
            calls += Call(conversationExternalId, openingMessage, title)
            return result
        }
    }

    private class MinimalSettings : AppSettingsRepository {
        override fun getApiKey(provider: LlmProvider): String? = "key"
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
        override fun isMorningCheckInEnabled(): Boolean = false
        override fun setMorningCheckInEnabled(enabled: Boolean) {}
        override fun getMorningCheckInTime(): String = "07:00"
        override fun setMorningCheckInTime(time: String) {}
        override fun isEveningCheckInEnabled(): Boolean = false
        override fun setEveningCheckInEnabled(enabled: Boolean) {}
        override fun getEveningCheckInTime(): String = "21:00"
        override fun setEveningCheckInTime(time: String) {}
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

    private fun viewModel(
        checkInRepo: DailyCheckInRepository,
        starter: CheckInConversationStarter,
        checkInDate: LocalDate? = null
    ) = CheckInViewModel(
        slot = CheckInSlot.MORNING,
        checkInDate = checkInDate,
        dailyCheckInRepository = checkInRepo,
        audioRecordingService = AudioRecordingService(),
        llmClientFactory = LlmClientFactory(MinimalSettings()),
        fileSystem = FileSystem(),
        conversationStarter = starter
    )

    @Test
    fun `talking about a check-in seeds a thread and links it back`() = runTest(dispatcher) {
        val checkInRepo = FakeDailyCheckInRepository()
        val starter = RecordingConversationStarter()
        val vm = viewModel(checkInRepo, starter)
        advanceUntilIdle()

        vm.updateText("Slept badly, woke at three. Feeling flat.")
        var opened: String? = null
        vm.talkAboutThis { opened = it }
        advanceUntilIdle()

        assertEquals(expectedConversationId, opened)

        val call = starter.calls.single()
        assertEquals(expectedConversationId, call.externalId)
        // The user's words go through unaltered, with framing around them.
        assertTrue(call.openingMessage.contains("Slept badly, woke at three. Feeling flat."))
        assertTrue(call.openingMessage.contains("morning check-in"))
        // Without this the assistant reads the message as "please record this" and apologises
        // for being unable to save a check-in that is in fact already stored.
        assertTrue(
            call.openingMessage.contains("already saved"),
            "The opening turn must say the check-in is already stored"
        )
        // Renamed, because the chat service would otherwise title the thread from the framing.
        assertEquals("Morning check-in — $today", call.title)

        assertEquals(
            Triple(today, CheckInSlot.MORNING, expectedConversationId),
            checkInRepo.attached.single()
        )
        assertEquals(expectedConversationId, vm.uiState.value.conversationExternalId)
    }

    @Test
    fun `talking about an unsaved answer saves it first`() = runTest(dispatcher) {
        // attachConversation is an UPDATE against an existing row. Starting a conversation
        // without saving first lost the answer entirely and left the thread unlinked, so
        // reopening sent a second opening turn into the same conversation.
        val checkInRepo = FakeDailyCheckInRepository()
        val starter = RecordingConversationStarter()
        val vm = viewModel(checkInRepo, starter)
        advanceUntilIdle()

        vm.updateText("Slept badly and never tapped save")
        vm.talkAboutThis { }
        advanceUntilIdle()

        val saved = checkInRepo.saved.singleOrNull()
        assertNotNull(saved, "The answer must be persisted before the conversation starts")
        assertEquals("Slept badly and never tapped save", saved.responseText)

        // And the row exists, so the conversation link actually lands.
        assertEquals(expectedConversationId, checkInRepo.attached.single().third)
        assertTrue(vm.uiState.value.hasSavedAnswer)
    }

    @Test
    fun `a failed save does not open a conversation`() = runTest(dispatcher) {
        val failingRepo = object : DailyCheckInRepository by FakeDailyCheckInRepository() {
            override suspend fun saveCheckIn(checkIn: DailyCheckIn): Long =
                throw RuntimeException("disk full")
        }
        val starter = RecordingConversationStarter()
        val vm = viewModel(failingRepo, starter)
        advanceUntilIdle()

        vm.updateText("Slept badly")
        var opened: String? = null
        vm.talkAboutThis { opened = it }
        advanceUntilIdle()

        assertTrue(starter.calls.isEmpty(), "No conversation when the answer could not be kept")
        assertNull(opened)
        assertNotNull(vm.uiState.value.saveError)
    }

    @Test
    fun `re-opening an existing thread does not start another conversation`() = runTest(dispatcher) {
        val checkInRepo = FakeDailyCheckInRepository()
        checkInRepo.seed(
            DailyCheckIn(
                checkInDate = today,
                slot = CheckInSlot.MORNING,
                capturedAt = Clock.System.now(),
                responseText = "Slept badly",
                inputSource = CheckInInputSource.TYPED,
                conversationExternalId = expectedConversationId
            )
        )
        val starter = RecordingConversationStarter()
        val vm = viewModel(checkInRepo, starter)
        advanceUntilIdle()

        var opened: String? = null
        vm.talkAboutThis { opened = it }
        advanceUntilIdle()

        assertEquals(expectedConversationId, opened)
        // No second opening turn, so no duplicate assistant reply in the thread.
        assertTrue(starter.calls.isEmpty())
    }

    @Test
    fun `re-answering keeps the thread already started about the check-in`() = runTest(dispatcher) {
        val checkInRepo = FakeDailyCheckInRepository()
        checkInRepo.seed(
            DailyCheckIn(
                checkInDate = today,
                slot = CheckInSlot.MORNING,
                capturedAt = Clock.System.now(),
                responseText = "Slept badly",
                conversationExternalId = expectedConversationId
            )
        )
        val vm = viewModel(checkInRepo, RecordingConversationStarter())
        advanceUntilIdle()

        vm.updateText("Actually, better than I first thought.")
        vm.save()
        advanceUntilIdle()

        // Saving is an upsert; losing this would orphan the conversation.
        assertEquals(expectedConversationId, checkInRepo.saved.single().conversationExternalId)
    }

    @Test
    fun `a missing API key is reported rather than opening an empty thread`() = runTest(dispatcher) {
        val checkInRepo = FakeDailyCheckInRepository()
        val vm = viewModel(
            checkInRepo,
            RecordingConversationStarter(CheckInConversationResult.ApiKeyMissing)
        )
        advanceUntilIdle()

        vm.updateText("Slept badly")
        var opened: String? = null
        vm.talkAboutThis { opened = it }
        advanceUntilIdle()

        assertNull(opened, "Navigation must not happen when the turn failed")
        assertNotNull(vm.uiState.value.conversationError)
        assertNull(vm.uiState.value.conversationExternalId)
        assertTrue(checkInRepo.attached.isEmpty(), "Nothing to link when no thread was created")
    }

    @Test
    fun `a provider failure surfaces its message and clears the busy state`() = runTest(dispatcher) {
        val checkInRepo = FakeDailyCheckInRepository()
        val vm = viewModel(
            checkInRepo,
            RecordingConversationStarter(CheckInConversationResult.Failed("rate limited"))
        )
        advanceUntilIdle()

        vm.updateText("Slept badly")
        vm.talkAboutThis { }
        advanceUntilIdle()

        assertEquals("rate limited", vm.uiState.value.conversationError)
        assertEquals(false, vm.uiState.value.isStartingConversation)
    }

    @Test
    fun `an empty answer does not start a conversation`() = runTest(dispatcher) {
        val checkInRepo = FakeDailyCheckInRepository()
        val starter = RecordingConversationStarter()
        val vm = viewModel(checkInRepo, starter)
        advanceUntilIdle()

        vm.talkAboutThis { }
        advanceUntilIdle()

        assertTrue(starter.calls.isEmpty())
    }

    @Test
    fun `a backfilled answer is recorded against the day it is about`() = runTest(dispatcher) {
        // The reason blank slots were originally today-only: storing yesterday's answer against
        // today would put it on the wrong day and feed the wrong daily summary.
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val checkInRepo = FakeDailyCheckInRepository()
        val vm = viewModel(checkInRepo, RecordingConversationStarter(), checkInDate = yesterday)
        advanceUntilIdle()

        vm.updateText("Forgot to check in last night")
        vm.save()
        advanceUntilIdle()

        val saved = checkInRepo.saved.single()
        assertEquals(yesterday, saved.checkInDate)
        assertTrue(
            saved.capturedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date >= yesterday,
            "capturedAt records when it was answered, which may be after the day it is about"
        )
        assertTrue(vm.uiState.value.isBackfill)
    }

    @Test
    fun `a backfilled conversation id uses the day being discussed`() = runTest(dispatcher) {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val checkInRepo = FakeDailyCheckInRepository()
        val starter = RecordingConversationStarter()
        val vm = viewModel(checkInRepo, starter, checkInDate = yesterday)
        advanceUntilIdle()

        vm.updateText("Rough night, forgot to say")
        vm.talkAboutThis { }
        advanceUntilIdle()

        assertEquals("checkin-$yesterday-morning", starter.calls.single().externalId)
    }

    @Test
    fun `saving an answer records it against today`() = runTest(dispatcher) {
        val checkInRepo = FakeDailyCheckInRepository()
        val vm = viewModel(checkInRepo, RecordingConversationStarter())
        advanceUntilIdle()

        vm.updateText("  Slept fine  ")
        vm.save()
        advanceUntilIdle()

        val saved = checkInRepo.saved.single()
        assertEquals("Slept fine", saved.responseText, "Whitespace is trimmed before storing")
        assertEquals(today, saved.checkInDate)
        assertEquals(CheckInSlot.MORNING, saved.slot)
        assertTrue(vm.uiState.value.hasSavedAnswer)
    }
}
