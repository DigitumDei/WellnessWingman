package com.wellnesswingman.ui.screens.textentry

import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.analysis.BackgroundAnalysisService
import com.wellnesswingman.domain.capture.TextEntryProcessor
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.FileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.*

/**
 * Covers the screen behind "Describe it instead".
 *
 * Hand-written fakes throughout, matching the rest of the suite.
 */
class TextEntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private class FakeTrackedEntryRepository : TrackedEntryRepository {
        val inserted = mutableListOf<TrackedEntry>()

        override suspend fun insertEntry(entry: TrackedEntry): Long {
            inserted.add(entry)
            return inserted.size.toLong()
        }

        override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long) = inserted
        override suspend fun getEntriesForDay(date: LocalDate) = inserted
        override suspend fun getAllEntries() = inserted
        override suspend fun getRecentEntries(limit: Int, entryType: EntryType?) = inserted
        override fun observeAllEntries(): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntryById(id: Long): TrackedEntry? = null
        override suspend fun getEntryByExternalId(externalId: String): TrackedEntry? = null
        override suspend fun getEntryByBlobPath(blobPath: String): TrackedEntry? = null
        override fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntriesForWeek(startMillis: Long, endMillis: Long) = inserted
        override suspend fun getEntriesForMonth(startMillis: Long, endMillis: Long) = inserted
        override suspend fun getEntriesByStatus(status: ProcessingStatus) = inserted
        override suspend fun getPendingEntries() = emptyList<TrackedEntry>()
        override suspend fun updateEntryStatus(id: Long, status: ProcessingStatus) = Unit
        override suspend fun updateEntryType(id: Long, entryType: EntryType) = Unit
        override suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int) = Unit
        override suspend fun updateUserNotes(id: Long, notes: String?) = Unit
        override suspend fun deleteEntry(id: Long) = Unit
        override suspend fun upsertEntry(entry: TrackedEntry) = Unit
    }

    private class FakeBackgroundAnalysisService : BackgroundAnalysisService {
        val queued = mutableListOf<Long>()
        override fun queueEntry(entryId: Long, userProvidedDetails: String?) {
            queued.add(entryId)
        }
        override fun queueCorrection(entryId: Long, correction: String) = Unit
    }

    private class FakeLlmClientFactory(
        private val hasKey: Boolean
    ) : LlmClientFactory(settingsRepository = FakeAppSettingsRepository()) {
        override fun hasCurrentApiKey(): Boolean = hasKey
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

    /**
     * @param scope drives the processor's work. The processor otherwise runs on
     *   [Dispatchers.Default], which the test scheduler cannot advance, so the entry would never
     *   be inserted before the assertions ran.
     */
    private fun viewModel(
        scope: CoroutineScope,
        repository: FakeTrackedEntryRepository = FakeTrackedEntryRepository(),
        hasApiKey: Boolean = true
    ): TextEntryViewModel {
        val factory = FakeLlmClientFactory(hasApiKey)
        return TextEntryViewModel(
            textEntryProcessor = TextEntryProcessor(
                trackedEntryRepository = repository,
                backgroundAnalysisService = FakeBackgroundAnalysisService(),
                llmClientFactory = factory,
                scope = scope
            ),
            // Platform classes rather than interfaces, so the real ones are constructed here as
            // the check-in tests do. Nothing in these tests records audio.
            audioRecordingService = AudioRecordingService(),
            llmClientFactory = factory,
            fileSystem = FileSystem()
        )
    }

    @Test
    fun `saving creates an entry and reports its id`() = runTest {
        val repository = FakeTrackedEntryRepository()
        val subject = viewModel(this, repository)
        var openedEntryId: Long? = null

        subject.updateText("two slices of pizza and a beer")
        subject.save { openedEntryId = it }
        advanceUntilIdle()

        assertEquals(1, repository.inserted.size)
        assertNotNull(openedEntryId)
        assertNull(repository.inserted.single().blobPath)
    }

    @Test
    fun `an empty description does nothing`() = runTest {
        val repository = FakeTrackedEntryRepository()
        val subject = viewModel(this, repository)
        var opened = false

        subject.save { opened = true }
        advanceUntilIdle()

        // Guards against creating an entry with nothing in it to analyse.
        assertTrue(repository.inserted.isEmpty())
        assertFalse(opened)
    }

    @Test
    fun `whitespace alone does nothing`() = runTest {
        val repository = FakeTrackedEntryRepository()
        val subject = viewModel(this, repository)

        subject.updateText("    ")
        subject.save { }
        advanceUntilIdle()

        assertTrue(repository.inserted.isEmpty())
    }

    @Test
    fun `a missing API key surfaces before navigating away`() = runTest {
        val repository = FakeTrackedEntryRepository()
        val subject = viewModel(this, repository, hasApiKey = false)
        var opened = false

        subject.updateText("a sandwich")
        subject.save { opened = true }
        advanceUntilIdle()

        // Saved, but landing on a detail screen that will never fill in explains nothing.
        assertEquals(1, repository.inserted.size)
        assertTrue(subject.uiState.value.apiKeyMissing)
        assertFalse(opened)
    }

    @Test
    fun `acknowledging the missing key continues to the saved entry`() = runTest {
        val subject = viewModel(this, hasApiKey = false)
        var openedEntryId: Long? = null

        subject.updateText("a sandwich")
        subject.save { }
        advanceUntilIdle()

        subject.acknowledgeApiKeyMissing { openedEntryId = it }

        assertNotNull(openedEntryId)
        assertFalse(subject.uiState.value.apiKeyMissing)
    }

    @Test
    fun `saving twice in a row does not create a second entry`() = runTest {
        val repository = FakeTrackedEntryRepository()
        val subject = viewModel(this, repository)

        subject.updateText("two beers")
        subject.save { }
        subject.save { }
        advanceUntilIdle()

        // The second tap lands while the first save is still in flight, which is exactly the
        // double-tap the guard exists for.
        assertEquals(1, repository.inserted.size)
    }
}
