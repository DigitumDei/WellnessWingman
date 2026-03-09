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
        // Arrange
        val thresholdDays = 30
        every { appSettingsRepository.getImageRetentionThresholdDays() } returns thresholdDays
        every { fileSystem.getAppDataDirectory() } returns "/appData"
        
        val oldEntry = createEntry(id = 1, status = ProcessingStatus.COMPLETED, blobPath = "Entries/Meal/old.jpg", daysOld = 31)
        val newEntry = createEntry(id = 2, status = ProcessingStatus.COMPLETED, blobPath = "Entries/Meal/new.jpg", daysOld = 5)
        
        coEvery { trackedEntryRepository.getEntriesByStatus(ProcessingStatus.COMPLETED) } returns listOf(oldEntry, newEntry)

        val originalPath = "/appData/Entries/Meal/old.jpg"
        val previewPath = "/appData/Entries/Meal/old_preview.jpg"
        
        every { fileSystem.exists(originalPath) } returns true
        every { fileSystem.exists(previewPath) } returns true
        coEvery { fileSystem.delete(originalPath) } returns true

        // Act
        val downgradedCount = service.downsizeOldImages()

        // Assert
        assertEquals(1, downgradedCount)
        
        // Verify delete was called for the old image
        coVerify(exactly = 1) { fileSystem.delete(originalPath) }
        
        // Verify upsert was called with the updated blobPath pointing to preview
        coVerify(exactly = 1) { 
            trackedEntryRepository.upsertEntry(match { it.entryId == 1L && it.blobPath == "Entries/Meal/old_preview.jpg" }) 
        }
    }

    @Test
    fun `downsizeOldImages generates preview if missing`() = runTest {
        // Arrange
        every { appSettingsRepository.getImageRetentionThresholdDays() } returns 30
        every { fileSystem.getAppDataDirectory() } returns "/appData"
        
        val oldEntry = createEntry(id = 1, status = ProcessingStatus.COMPLETED, blobPath = "Entries/Meal/old.jpg", daysOld = 31)
        coEvery { trackedEntryRepository.getEntriesByStatus(ProcessingStatus.COMPLETED) } returns listOf(oldEntry)

        val originalPath = "/appData/Entries/Meal/old.jpg"
        val previewPath = "/appData/Entries/Meal/old_preview.jpg"
        
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
        // Arrange
        every { appSettingsRepository.getImageRetentionThresholdDays() } returns 30
        every { fileSystem.getAppDataDirectory() } returns "/appData"
        
        // This entry's blobPath already points to a preview file
        val downgradedEntry = createEntry(id = 1, status = ProcessingStatus.COMPLETED, blobPath = "Entries/Meal/old_preview.jpg", daysOld = 31)
        coEvery { trackedEntryRepository.getEntriesByStatus(ProcessingStatus.COMPLETED) } returns listOf(downgradedEntry)

        // Act
        val downgradedCount = service.downsizeOldImages()

        // Assert
        assertEquals(0, downgradedCount)
        coVerify(exactly = 0) { fileSystem.delete(any()) }
        coVerify(exactly = 0) { trackedEntryRepository.upsertEntry(any()) }
    }
}
