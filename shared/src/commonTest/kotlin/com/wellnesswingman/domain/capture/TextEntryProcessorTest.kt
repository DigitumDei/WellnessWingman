package com.wellnesswingman.domain.capture

import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.analysis.BackgroundAnalysisService
import com.wellnesswingman.domain.llm.LlmClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

/**
 * Covers creating an entry from a description rather than a photo.
 *
 * The entry must be stored with a null blobPath, because that is precisely what tells
 * AnalysisOrchestrator to send it to generateCompletion instead of analyzeImage.
 */
class TextEntryProcessorTest {

    private class FakeTrackedEntryRepository : TrackedEntryRepository {
        val inserted = mutableListOf<TrackedEntry>()
        private var nextId = 1L
        var nextInsertFailure: Exception? = null

        override suspend fun insertEntry(entry: TrackedEntry): Long {
            nextInsertFailure?.let { error ->
                nextInsertFailure = null
                throw error
            }
            inserted.add(entry)
            return nextId++
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
        val queued = mutableListOf<Pair<Long, String?>>()

        override fun queueEntry(entryId: Long, userProvidedDetails: String?) {
            queued.add(entryId to userProvidedDetails)
        }

        override fun queueCorrection(entryId: Long, correction: String) = Unit
    }

    /** Reports whether a key is configured; that is the only behaviour under test here. */
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

    private fun processor(
        repository: FakeTrackedEntryRepository = FakeTrackedEntryRepository(),
        analysis: FakeBackgroundAnalysisService = FakeBackgroundAnalysisService(),
        hasApiKey: Boolean = true
    ) = TextEntryProcessor(
        trackedEntryRepository = repository,
        backgroundAnalysisService = analysis,
        llmClientFactory = FakeLlmClientFactory(hasApiKey)
    )

    @Test
    fun `stores the entry with no blob path`() = runTest {
        val repository = FakeTrackedEntryRepository()

        processor(repository).process("two slices of pizza and a beer")

        val entry = repository.inserted.single()
        // A null blobPath is what routes this to generateCompletion rather than analyzeImage.
        assertNull(entry.blobPath)
        assertEquals("two slices of pizza and a beer", entry.userNotes)
    }

    @Test
    fun `entry type is left unknown for the analysis to decide`() = runTest {
        val repository = FakeTrackedEntryRepository()

        processor(repository).process("ran about 5k, watch was dead")

        // Exactly as a photo entry behaves: the analysis detects meal vs exercise, not the UI.
        assertEquals(EntryType.UNKNOWN, repository.inserted.single().entryType)
    }

    @Test
    fun `analysis is queued with the description as context`() = runTest {
        val repository = FakeTrackedEntryRepository()
        val analysis = FakeBackgroundAnalysisService()

        val result = processor(repository, analysis).process("a flat white and a croissant")

        val (queuedEntryId, queuedContext) = analysis.queued.single()
        assertEquals(result.entryId, queuedEntryId)
        assertEquals("a flat white and a croissant", queuedContext)
    }

    @Test
    fun `without an API key the entry is still saved but nothing is queued`() = runTest {
        val repository = FakeTrackedEntryRepository()
        val analysis = FakeBackgroundAnalysisService()

        val result = processor(repository, analysis, hasApiKey = false).process("a sandwich")

        // Losing what the user typed because a key is missing would be the worse failure.
        assertTrue(result.apiKeyMissing)
        assertEquals(1, repository.inserted.size)
        assertTrue(analysis.queued.isEmpty())
    }

    @Test
    fun `the description is trimmed before it is stored`() = runTest {
        val repository = FakeTrackedEntryRepository()

        processor(repository).process("   a bowl of soup  \n")

        assertEquals("a bowl of soup", repository.inserted.single().userNotes)
    }

    @Test
    fun `a blank description is rejected`() = runTest {
        // An entry with neither photo nor words has nothing to analyse.
        assertFailsWith<IllegalArgumentException> {
            processor().process("   ")
        }
    }

    @Test
    fun `overlapping saves of the same text create one entry`() = runTest {
        val repository = FakeTrackedEntryRepository()
        // Runs the processor's work on this test's scheduler so the two calls genuinely overlap
        // rather than depending on how fast a background dispatcher happens to be.
        val subject = TextEntryProcessor(
            trackedEntryRepository = repository,
            backgroundAnalysisService = FakeBackgroundAnalysisService(),
            llmClientFactory = FakeLlmClientFactory(hasKey = true),
            scope = this
        )

        // A double-tapped save. Without a file to key on there is no way to spot the duplicate
        // afterwards, so it has to be caught while the first call is still in flight.
        val first = async { subject.process("two beers") }
        val second = async { subject.process("two beers") }

        assertEquals(first.await().entryId, second.await().entryId)
        assertEquals(1, repository.inserted.size)
    }

    @Test
    fun `genuinely different descriptions both create entries`() = runTest {
        val repository = FakeTrackedEntryRepository()
        val subject = processor(repository)

        subject.process("two beers")
        subject.process("a packet of chips")

        assertEquals(2, repository.inserted.size)
    }

    @Test
    fun `the same text saved again after the first finished creates a second entry`() = runTest {
        val repository = FakeTrackedEntryRepository()
        val subject = processor(repository)

        subject.process("a flat white")
        subject.process("a flat white")

        // Deliberate: the guard covers overlapping calls only. Once the first save has finished,
        // an identical description is far more likely to be a real second coffee than a mistake.
        assertEquals(2, repository.inserted.size)
    }

    @Test
    fun `failed save clears the in-flight guard so the text can be retried`() = runTest {
        val repository = FakeTrackedEntryRepository().apply {
            nextInsertFailure = IllegalStateException("database unavailable")
        }
        val subject = TextEntryProcessor(
            trackedEntryRepository = repository,
            backgroundAnalysisService = FakeBackgroundAnalysisService(),
            llmClientFactory = FakeLlmClientFactory(hasKey = true),
            scope = this
        )

        val error = assertFailsWith<IllegalStateException> {
            subject.process("a bowl of soup")
        }
        assertEquals("database unavailable", error.message)

        val retried = subject.process("a bowl of soup")
        assertEquals(1L, retried.entryId)
        assertEquals(1, repository.inserted.size)
    }

    @Test
    fun `cancelled save cancels its waiter and clears the in-flight guard`() = runTest {
        val repository = FakeTrackedEntryRepository().apply {
            nextInsertFailure = CancellationException("cancelled")
        }
        val subject = TextEntryProcessor(
            trackedEntryRepository = repository,
            backgroundAnalysisService = FakeBackgroundAnalysisService(),
            llmClientFactory = FakeLlmClientFactory(hasKey = true),
            scope = this
        )

        assertFailsWith<CancellationException> {
            subject.process("a bowl of soup")
        }

        val retried = subject.process("a bowl of soup")
        assertEquals(1L, retried.entryId)
        assertEquals(1, repository.inserted.size)
    }
}
