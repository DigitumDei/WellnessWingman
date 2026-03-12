package com.wellnesswingman.domain.media

import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.platform.FileSystemOperations
import com.wellnesswingman.platform.PhotoResizer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/**
 * Background service that downsizes old images to reclaim storage space.
 */
class ImageRetentionService(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val fileSystem: FileSystemOperations,
    private val photoResizer: PhotoResizer
) {
    suspend fun downsizeOldImages(): Int = withContext(Dispatchers.IO) {
        val thresholdDays = appSettingsRepository.getImageRetentionThresholdDays()
        val thresholdTime = Clock.System.now().minus(thresholdDays, DateTimeUnit.DAY, TimeZone.currentSystemDefault())

        val entries = trackedEntryRepository.getEntriesByStatus(ProcessingStatus.COMPLETED)
        var downgradedCount = 0

        for (entry in entries) {
            // blobPath is stored as an absolute path
            val originalAbsolutePath = entry.blobPath ?: continue

            // Skip if already downgraded: check that _preview appears immediately before the extension
            val extensionIdx = originalAbsolutePath.lastIndexOf('.')
            val isPreview = if (extensionIdx > 0) {
                originalAbsolutePath.substring(0, extensionIdx).endsWith("_preview")
            } else {
                originalAbsolutePath.endsWith("_preview")
            }
            if (isPreview) continue

            if (entry.capturedAt < thresholdTime) {
                try {
                    if (!fileSystem.exists(originalAbsolutePath)) {
                        Napier.w("Original blob not found for entry ${entry.entryId}: $originalAbsolutePath")
                        continue
                    }

                    val previewAbsolutePath = getPreviewPath(originalAbsolutePath)

                    // Ensure preview exists, regenerate if missing
                    if (!fileSystem.exists(previewAbsolutePath)) {
                        Napier.i("Regenerating missing preview for entry ${entry.entryId}")
                        val originalBytes = fileSystem.readBytes(originalAbsolutePath)
                        val previewBytes = photoResizer.resize(
                            photoBytes = originalBytes,
                            maxWidth = 400,
                            maxHeight = 400,
                            quality = 70
                        )
                        fileSystem.writeBytes(previewAbsolutePath, previewBytes)
                    }

                    // Delete the original full-size blob first, then update the DB.
                    // This order ensures that if deletion fails the entry keeps its original
                    // path and will be retried on the next run.
                    val deleted = fileSystem.delete(originalAbsolutePath)
                    if (deleted) {
                        trackedEntryRepository.upsertEntry(
                            entry.copy(blobPath = previewAbsolutePath)
                        )
                        downgradedCount++
                        Napier.i("Successfully downgraded image for entry ${entry.entryId}")
                    } else {
                        Napier.e("Failed to delete original blob for entry ${entry.entryId}")
                    }
                } catch (e: Exception) {
                    Napier.e("Error downsizing image for entry ${entry.entryId}", e)
                }
            }
        }

        if (downgradedCount > 0) {
            Napier.i("Image retention job completed: downgraded $downgradedCount entries")
        } else {
            Napier.d("Image retention job completed: no entries eligible for downsizing")
        }

        return@withContext downgradedCount
    }

    private fun getPreviewPath(blobPath: String): String {
        return if (blobPath.contains(".")) {
            val lastDot = blobPath.lastIndexOf('.')
            "${blobPath.substring(0, lastDot)}_preview${blobPath.substring(lastDot)}"
        } else {
            "${blobPath}_preview"
        }
    }
}
