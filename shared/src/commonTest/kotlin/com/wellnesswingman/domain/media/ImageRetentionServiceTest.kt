package com.wellnesswingman.domain.media

import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.platform.FileSystemOperations
import com.wellnesswingman.platform.PhotoResizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageRetentionServiceTest {

    private val trackedEntryRepository: TrackedEntryRepository = mockk(relaxed = true)
    private val appSettingsRepository: AppSettingsRepository = mockk(relaxed = true)
    private val fileSystem: FileSystemOperations = mockk(relaxed = true)
    private val photoResizer: PhotoResizer = mockk(relaxed = true)

    private val service = ImageRetentionService(
        trackedEntryRepository = trackedEntryRepository,
        appSettingsRepository = appSettingsRepository,
        fileSystem = fileSystem,
        photoResizer = photoResizer
    )

    private fun createEntry(
        id: Long,
        status: ProcessingStatus,
        blobPath: String?,
        daysOld: Int
    ): TrackedEntry {
        val capturedAt = Clock.System.now().minus(daysOld, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
        return TrackedEntry(
            entryId = id,
            externalId = null,
            entryType = EntryType.UNKNOWN,
            capturedAt = capturedAt,
            capturedAtTimeZoneId = "UTC",
            capturedAtOffsetMinutes = 0,
            blobPath = blobPath,
            dataPayload = "{}",
            dataSchemaVersion = 1,
            processingStatus = status,
            userNotes = null
        )
    }

    @Test
    fun `downsizeOldImages downsizes eligible entries and updates DB`() = runTest {
        // Arrange: blobPath is stored as an absolute path in the DB
        val thresholdDays = 30
        every { appSettingsRepository.getImageRetentionThresholdDays() } returns thresholdDays

        val originalPath = "/appData/Entries/Meal/old.jpg"
        val previewPath = "/appData/Entries/Meal/old_preview.jpg"

        val oldEntry = createEntry(id = 1, status = ProcessingStatus.COMPLETED, blobPath = originalPath, daysOld = 31)
        val newEntry = createEntry(id = 2, status = ProcessingStatus.COMPLETED, blobPath = "/appData/Entries/Meal/new.jpg", daysOld = 5)

        coEvery { trackedEntryRepository.getEntriesByStatus(ProcessingStatus.COMPLETED) } returns listOf(oldEntry, newEntry)
        every { fileSystem.exists(originalPath) } returns true
        every { fileSystem.exists(previewPath) } returns true
        coEvery { fileSystem.delete(originalPath) } returns true

        // Act
        val downgradedCount = service.downsizeOldImages()

        // Assert
        assertEquals(1, downgradedCount)
        coVerify(exactly = 1) { fileSystem.delete(originalPath) }
        // DB update uses the absolute preview path
        coVerify(exactly = 1) {
            trackedEntryRepository.upsertEntry(match { it.entryId == 1L && it.blobPath == previewPath })
        }
    }

    @Test
    fun `downsizeOldImages generates preview if missing`() = runTest {
        // Arrange
        every { appSettingsRepository.getImageRetentionThresholdDays() } returns 30

        val originalPath = "/appData/Entries/Meal/old.jpg"
        val previewPath = "/appData/Entries/Meal/old_preview.jpg"

        val oldEntry = createEntry(id = 1, status = ProcessingStatus.COMPLETED, blobPath = originalPath, daysOld = 31)
        coEvery { trackedEntryRepository.getEntriesByStatus(ProcessingStatus.COMPLETED) } returns listOf(oldEntry)

        every { fileSystem.exists(originalPath) } returns true
        every { fileSystem.exists(previewPath) } returns false // Missing preview
        coEvery { fileSystem.readBytes(originalPath) } returns byteArrayOf(1, 2, 3)
        coEvery { photoResizer.resize(any(), any(), any(), any(), any()) } returns byteArrayOf(4, 5, 6)
        coEvery { fileSystem.delete(originalPath) } returns true

        // Act
        val downgradedCount = service.downsizeOldImages()

        // Assert
        assertEquals(1, downgradedCount)
        coVerify(exactly = 1) { photoResizer.resize(any(), 400, 400, 70, false) }
        coVerify(exactly = 1) { fileSystem.writeBytes(previewPath, any()) }
    }

    @Test
    fun `downsizeOldImages is idempotent and skips already downgraded entries`() = runTest {
        // Arrange: entry already points to the preview file
        every { appSettingsRepository.getImageRetentionThresholdDays() } returns 30

        val downgradedEntry = createEntry(
            id = 1,
            status = ProcessingStatus.COMPLETED,
            blobPath = "/appData/Entries/Meal/old_preview.jpg",
            daysOld = 31
        )
        coEvery { trackedEntryRepository.getEntriesByStatus(ProcessingStatus.COMPLETED) } returns listOf(downgradedEntry)

        // Act
        val downgradedCount = service.downsizeOldImages()

        // Assert
        assertEquals(0, downgradedCount)
        coVerify(exactly = 0) { fileSystem.delete(any()) }
        coVerify(exactly = 0) { trackedEntryRepository.upsertEntry(any()) }
    }

    @Test
    fun `downsizeOldImages does not update DB if file deletion fails`() = runTest {
        // Arrange: deletion fails — DB must remain unchanged so the entry is retried next run
        every { appSettingsRepository.getImageRetentionThresholdDays() } returns 30

        val originalPath = "/appData/Entries/Meal/old.jpg"
        val previewPath = "/appData/Entries/Meal/old_preview.jpg"

        val oldEntry = createEntry(id = 1, status = ProcessingStatus.COMPLETED, blobPath = originalPath, daysOld = 31)
        coEvery { trackedEntryRepository.getEntriesByStatus(ProcessingStatus.COMPLETED) } returns listOf(oldEntry)

        every { fileSystem.exists(originalPath) } returns true
        every { fileSystem.exists(previewPath) } returns true
        coEvery { fileSystem.delete(originalPath) } returns false

        // Act
        val downgradedCount = service.downsizeOldImages()

        // Assert
        assertEquals(0, downgradedCount)
        coVerify(exactly = 0) { trackedEntryRepository.upsertEntry(any()) }
    }
}
