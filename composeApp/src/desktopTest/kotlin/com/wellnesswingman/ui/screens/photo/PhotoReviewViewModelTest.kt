package com.wellnesswingman.ui.screens.photo

import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.analysis.BackgroundAnalysisService
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.platform.PhotoResizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoReviewViewModelTest {

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the configured number of recent entries`() = runTest {
        val entries = listOf(entry(1L, "/photo.jpg"))
        val repository = FakeTrackedEntryRepository(entries)
        val subject = viewModel(repository)

        subject.loadPreviousEntries(limit = 3)
        advanceUntilIdle()

        assertEquals(3, repository.requestedLimit)
        assertEquals(entries, subject.previousEntriesState.value.entries)
        assertFalse(subject.previousEntriesState.value.isLoading)
    }

    @Test
    fun `copyPreviousPhoto verifies the copied destination`() = runTest {
        val fileSystem = mockk<FileSystem>(relaxed = true)
        every { fileSystem.exists("/source.jpg") } returns true
        every { fileSystem.exists("/destination.jpg") } returns true
        coEvery { fileSystem.copyFile("/source.jpg", "/destination.jpg") } just Runs
        val subject = viewModel(fileSystem = fileSystem)

        assertTrue(subject.copyPreviousPhoto(entry(1L, "/source.jpg"), "/destination.jpg"))

        coVerify(exactly = 1) { fileSystem.copyFile("/source.jpg", "/destination.jpg") }
    }

    @Test
    fun `copyPreviousPhoto removes a destination that was not created`() = runTest {
        val fileSystem = mockk<FileSystem>(relaxed = true)
        every { fileSystem.exists("/source.jpg") } returns true
        every { fileSystem.exists("/destination.jpg") } returns false
        coEvery { fileSystem.copyFile("/source.jpg", "/destination.jpg") } just Runs
        coEvery { fileSystem.delete("/destination.jpg") } returns true
        val subject = viewModel(fileSystem = fileSystem)

        assertFalse(subject.copyPreviousPhoto(entry(1L, "/source.jpg"), "/destination.jpg"))

        coVerify(exactly = 1) { fileSystem.delete("/destination.jpg") }
    }

    @Test
    fun `cancelAndCleanup removes copied photo and preview before returning`() = runTest {
        val fileSystem = mockk<FileSystem>(relaxed = true)
        val photoResizer = mockk<PhotoResizer>()
        val sourceBytes = byteArrayOf(1, 2, 3)
        val copiedBytes = byteArrayOf(4, 5, 6)
        every { fileSystem.exists("/source.jpg") } returns true
        every { fileSystem.getAppDataDirectory() } returns "/app"
        coEvery { fileSystem.readBytes("/source.jpg") } returns sourceBytes
        coEvery { photoResizer.resize(any(), any(), any(), any(), any()) } returns copiedBytes
        coEvery { fileSystem.writeBytes(any(), any()) } just Runs
        coEvery { fileSystem.delete(any()) } returns true
        val subject = viewModel(fileSystem = fileSystem, photoResizer = photoResizer)

        assertTrue(subject.preparePreviousEntry(entry(1L, "/source.jpg", "leftover pasta")))
        val review = assertIs<PhotoReviewUiState.Review>(subject.uiState.value)
        assertEquals("leftover pasta", review.initialNotes)

        subject.cancelAndCleanup()

        coVerify(exactly = 1) { fileSystem.delete(review.blobPath) }
        coVerify(exactly = 1) { fileSystem.delete(PhotoReviewViewModel.getPreviewPath(review.blobPath)) }
        assertIs<PhotoReviewUiState.Cancelled>(subject.uiState.value)
    }

    private fun viewModel(
        repository: FakeTrackedEntryRepository = FakeTrackedEntryRepository(),
        fileSystem: FileSystem = mockk(relaxed = true),
        photoResizer: PhotoResizer = mockk(relaxed = true)
    ): PhotoReviewViewModel {
        val llmClientFactory = mockk<LlmClientFactory>(relaxed = true)
        every { llmClientFactory.hasCurrentApiKey() } returns false
        return PhotoReviewViewModel(
            cameraService = mockk(relaxed = true),
            photoResizer = photoResizer,
            trackedEntryRepository = repository,
            backgroundAnalysisService = FakeBackgroundAnalysisService(),
            audioRecordingService = mockk(relaxed = true),
            fileSystem = fileSystem,
            llmClientFactory = llmClientFactory
        )
    }

    private class FakeTrackedEntryRepository(
        private val recentEntries: List<TrackedEntry> = emptyList()
    ) : TrackedEntryRepository {
        var requestedLimit: Int? = null

        override suspend fun getRecentEntries(limit: Int, entryType: EntryType?): List<TrackedEntry> {
            requestedLimit = limit
            return recentEntries
        }

        override suspend fun getAllEntries() = recentEntries
        override fun observeAllEntries(): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntryById(id: Long): TrackedEntry? = recentEntries.find { it.entryId == id }
        override suspend fun getEntryByExternalId(externalId: String): TrackedEntry? = null
        override suspend fun getEntryByBlobPath(blobPath: String): TrackedEntry? = null
        override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long) = recentEntries
        override suspend fun getEntriesForDay(date: LocalDate) = recentEntries
        override fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntriesByStatus(status: ProcessingStatus) = recentEntries
        override suspend fun getPendingEntries() = emptyList<TrackedEntry>()
        override suspend fun insertEntry(entry: TrackedEntry) = entry.entryId
        override suspend fun updateEntryStatus(id: Long, status: ProcessingStatus) = Unit
        override suspend fun updateEntryType(id: Long, entryType: EntryType) = Unit
        override suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int) = Unit
        override suspend fun updateUserNotes(id: Long, notes: String?) = Unit
        override suspend fun deleteEntry(id: Long) = Unit
        override suspend fun upsertEntry(entry: TrackedEntry) = Unit
    }

    private class FakeBackgroundAnalysisService : BackgroundAnalysisService {
        override fun queueEntry(entryId: Long, userProvidedDetails: String?) = Unit
        override fun queueCorrection(entryId: Long, correction: String) = Unit
    }

    private companion object {
        fun entry(id: Long, blobPath: String?, notes: String? = null) = TrackedEntry(
            entryId = id,
            capturedAt = Clock.System.now(),
            blobPath = blobPath,
            userNotes = notes
        )
    }
}
