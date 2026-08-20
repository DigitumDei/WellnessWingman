package com.wellnesswingman.domain.capture

import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.analysis.BackgroundAnalysisService
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.FileSystemOperations
import com.wellnesswingman.platform.PhotoResizer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoEntryProcessorTest {

    private val files = mutableMapOf<String, ByteArray>()
    private val directories = mutableSetOf("/app/data", "/app/data/photos")

    private val fileSystem: FileSystemOperations = mockk(relaxed = true) {
        every { getAppDataDirectory() } returns "/app/data"
        every { exists(any()) } answers { files.containsKey(firstArg()) || directories.contains(firstArg()) }
        every { createDirectory(any()) } answers { directories.add(firstArg()); true }
        coEvery { readBytes(any()) } answers { files[firstArg()] ?: ByteArray(0) }
        coEvery { writeBytes(any(), any()) } answers { files[firstArg()] = secondArg() }
        coEvery { delete(any()) } answers { files.remove(firstArg()) != null }
    }

    private val photoResizer: PhotoResizer = mockk(relaxed = true) {
        coEvery { resize(any(), any(), any(), any(), any()) } answers { firstArg<ByteArray>() }
    }

    private val llmClientFactory: LlmClientFactory = mockk(relaxed = true) {
        every { hasCurrentApiKey() } returns true
    }

    private val backgroundAnalysisService: BackgroundAnalysisService = mockk(relaxed = true)

    private val repository = InMemoryTrackedEntryRepository()

    private val processor = PhotoEntryProcessor(
        photoResizer = photoResizer,
        fileSystem = fileSystem,
        trackedEntryRepository = repository,
        backgroundAnalysisService = backgroundAnalysisService,
        llmClientFactory = llmClientFactory
    )

    @Test
    fun `process persists the photo and creates exactly one entry`() = runTest {
        val pendingPath = "/app/data/pending_photos/photo_1.jpg"
        files[pendingPath] = "image-bytes".toByteArray()

        val result = processor.process(pendingPath, userNotes = "lunch")

        assertTrue(result.entryId > 0)
        assertEquals(1, repository.all.size)
        assertEquals(result.entryId, repository.all.single().entryId)
        assertEquals("lunch", repository.all.single().userNotes)
    }

    @Test
    fun `process writes the normalized blob and preview to the photos directory`() = runTest {
        val pendingPath = "/app/data/pending_photos/photo_2.jpg"
        files[pendingPath] = "image-bytes".toByteArray()

        val result = processor.process(pendingPath, userNotes = "")

        val expectedBlob = "/app/data/photos/photo_2.jpg"
        val expectedPreview = "/app/data/photos/photo_2_preview.jpg"
        assertEquals(expectedBlob, repository.all.single().blobPath)
        assertTrue(files.containsKey(expectedBlob), "blob should be written")
        assertTrue(files.containsKey(expectedPreview), "preview should be written")
    }

    @Test
    fun `process is idempotent across repeated calls for the same file`() = runTest {
        val pendingPath = "/app/data/pending_photos/photo_3.jpg"
        files[pendingPath] = "image-bytes".toByteArray()

        val first = processor.process(pendingPath, userNotes = "first")
        val second = processor.process(pendingPath, userNotes = "second")

        assertEquals(first.entryId, second.entryId)
        assertEquals(1, repository.all.size, "repeated processing must not duplicate entries")
        assertEquals("first", repository.all.single().userNotes)
    }

    @Test
    fun `concurrent calls for the same file are coalesced into one entry`() = runTest {
        val pendingPath = "/app/data/pending_photos/photo_4.jpg"
        files[pendingPath] = "image-bytes".toByteArray()

        // Slow down the resizer so concurrent calls overlap.
        coEvery { photoResizer.resize(any(), any(), any(), any(), any()) } coAnswers {
            delay(100)
            firstArg<ByteArray>()
        }

        val results = listOf(
            async { processor.process(pendingPath, "a") },
            async { processor.process(pendingPath, "b") },
            async { processor.process(pendingPath, "c") }
        ).awaitAll()

        assertEquals(1, repository.all.size)
        assertTrue(results.map { it.entryId }.distinct().size == 1)
    }

    @Test
    fun `process resumes after simulated recreation by finding the existing entry`() = runTest {
        val pendingPath = "/app/data/pending_photos/photo_5.jpg"
        files[pendingPath] = "image-bytes".toByteArray()

        val first = processor.process(pendingPath, "notes")

        // Simulate a fresh process: a brand-new processor instance shares the
        // same repository, so the blob-path lookup must find the existing entry
        // instead of creating a duplicate.
        val freshProcessor = PhotoEntryProcessor(
            photoResizer = photoResizer,
            fileSystem = fileSystem,
            trackedEntryRepository = repository,
            backgroundAnalysisService = backgroundAnalysisService,
            llmClientFactory = llmClientFactory
        )

        val resumed = freshProcessor.process(pendingPath, "notes")
        assertEquals(first.entryId, resumed.entryId)
        assertEquals(1, repository.all.size)
    }

    @Test
    fun `process queues background analysis when an api key is configured`() = runTest {
        val pendingPath = "/app/data/pending_photos/photo_6.jpg"
        files[pendingPath] = "image-bytes".toByteArray()

        val result = processor.process(pendingPath, "dinner")

        assertFalse(result.apiKeyMissing)
        verify { backgroundAnalysisService.queueEntry(result.entryId, "dinner") }
    }

    @Test
    fun `process skips analysis and reports missing key when no api key is configured`() = runTest {
        every { llmClientFactory.hasCurrentApiKey() } returns false

        val pendingPath = "/app/data/pending_photos/photo_7.jpg"
        files[pendingPath] = "image-bytes".toByteArray()

        val result = processor.process(pendingPath, "dinner")

        assertTrue(result.apiKeyMissing)
        verify(exactly = 0) { backgroundAnalysisService.queueEntry(any(), any()) }
    }

    @Test
    fun `deriveBlobPath is deterministic from the pending file name`() {
        assertEquals(
            "/app/data/photos/photo_8.jpg",
            processor.deriveBlobPath("/app/data/pending_photos/photo_8.jpg")
        )
        assertEquals(
            "/app/data/photos/gallery_9.jpg",
            processor.deriveBlobPath("/app/data/pending_photos/gallery_9.png")
        )
    }

    @Test
    fun `fast path re-queues analysis when existing entry is still PENDING`() = runTest {
        val pendingPath = "/app/data/pending_photos/photo_9.jpg"
        files[pendingPath] = "image-bytes".toByteArray()

        // Pre-insert an entry with PENDING status (simulates crash between
        // insertEntry and queueEntry).
        val blobPath = processor.deriveBlobPath(pendingPath)
        val orphaned = TrackedEntry(
            entryId = 1L,
            entryType = EntryType.UNKNOWN,
            capturedAt = Clock.System.now(),
            blobPath = blobPath,
            processingStatus = ProcessingStatus.PENDING
        )
        repository.all += orphaned

        val result = processor.process(pendingPath, "notes")

        assertEquals(1L, result.entryId)
        assertFalse(result.apiKeyMissing)
        verify { backgroundAnalysisService.queueEntry(1L, "notes") }
    }

    @Test
    fun `fast path re-queues analysis when existing entry is FAILED`() = runTest {
        val pendingPath = "/app/data/pending_photos/photo_10.jpg"
        files[pendingPath] = "image-bytes".toByteArray()

        val blobPath = processor.deriveBlobPath(pendingPath)
        val orphaned = TrackedEntry(
            entryId = 2L,
            entryType = EntryType.UNKNOWN,
            capturedAt = Clock.System.now(),
            blobPath = blobPath,
            processingStatus = ProcessingStatus.FAILED
        )
        repository.all += orphaned

        val result = processor.process(pendingPath, "notes")

        assertEquals(2L, result.entryId)
        verify { backgroundAnalysisService.queueEntry(2L, "notes") }
    }

    @Test
    fun `fast path does not re-queue analysis when entry is COMPLETED`() = runTest {
        val pendingPath = "/app/data/pending_photos/photo_11.jpg"
        files[pendingPath] = "image-bytes".toByteArray()

        val blobPath = processor.deriveBlobPath(pendingPath)
        val completed = TrackedEntry(
            entryId = 3L,
            entryType = EntryType.UNKNOWN,
            capturedAt = Clock.System.now(),
            blobPath = blobPath,
            processingStatus = ProcessingStatus.COMPLETED
        )
        repository.all += completed

        val result = processor.process(pendingPath, "notes")

        assertEquals(3L, result.entryId)
        verify(exactly = 0) { backgroundAnalysisService.queueEntry(any(), any()) }
    }

    @Test
    fun `fast path does not re-queue when api key is missing regardless of status`() = runTest {
        every { llmClientFactory.hasCurrentApiKey() } returns false

        val pendingPath = "/app/data/pending_photos/photo_12.jpg"
        files[pendingPath] = "image-bytes".toByteArray()

        val blobPath = processor.deriveBlobPath(pendingPath)
        val orphaned = TrackedEntry(
            entryId = 4L,
            entryType = EntryType.UNKNOWN,
            capturedAt = Clock.System.now(),
            blobPath = blobPath,
            processingStatus = ProcessingStatus.PENDING
        )
        repository.all += orphaned

        val result = processor.process(pendingPath, "notes")

        assertEquals(4L, result.entryId)
        assertTrue(result.apiKeyMissing)
        verify(exactly = 0) { backgroundAnalysisService.queueEntry(any(), any()) }
    }

    private class InMemoryTrackedEntryRepository : TrackedEntryRepository {
        val all = mutableListOf<TrackedEntry>()
        private var nextId = 1L

        override suspend fun getAllEntries(): List<TrackedEntry> = all
        override suspend fun getRecentEntries(limit: Int, entryType: EntryType?): List<TrackedEntry> = all.take(limit)
        override fun observeAllEntries(): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntryById(id: Long): TrackedEntry? = all.find { it.entryId == id }
        override suspend fun getEntryByExternalId(externalId: String): TrackedEntry? = null
        override suspend fun getEntryByBlobPath(blobPath: String): TrackedEntry? =
            all.find { it.blobPath == blobPath }
        override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long): List<TrackedEntry> = emptyList()
        override suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry> = emptyList()
        override fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntriesByStatus(status: ProcessingStatus): List<TrackedEntry> = emptyList()
        override suspend fun getPendingEntries(): List<TrackedEntry> = emptyList()
        override suspend fun insertEntry(entry: TrackedEntry): Long {
            val id = nextId++
            val saved = entry.copy(entryId = id)
            all += saved
            return id
        }
        override suspend fun updateEntryStatus(id: Long, status: ProcessingStatus) {}
        override suspend fun updateEntryType(id: Long, entryType: EntryType) {}
        override suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int) {}
        override suspend fun updateUserNotes(id: Long, notes: String?) {}
        override suspend fun deleteEntry(id: Long) { all.removeAll { it.entryId == id } }
        override suspend fun upsertEntry(entry: TrackedEntry) {
            val existing = all.indexOfFirst { it.entryId == entry.entryId }
            if (existing >= 0) all[existing] = entry else { all += entry.copy(entryId = nextId++) }
        }
    }
}
