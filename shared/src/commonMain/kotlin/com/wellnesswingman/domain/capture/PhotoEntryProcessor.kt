package com.wellnesswingman.domain.capture

import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.analysis.BackgroundAnalysisService
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.FileSystemOperations
import com.wellnesswingman.platform.PhotoResizer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Result of processing an in-progress photo capture into a tracked entry.
 *
 * @param entryId The persisted entry id.
 * @param apiKeyMissing True when no LLM API key is configured, meaning analysis
 *                      could not be queued and the UI should warn the user.
 */
data class PhotoEntryProcessorResult(
    val entryId: Long,
    val apiKeyMissing: Boolean
)

/**
 * App-scoped processor that turns a persisted photo file into a tracked entry.
 *
 * The work runs in an application-owned [CoroutineScope] (a [SupervisorJob] on
 * [Dispatchers.Default]) so it survives Android configuration changes (activity
 * recreation) even though the originating ScreenModel scope is cancelled on
 * recreation. Creation is idempotent: the final blob path is derived
 * deterministically from the pending photo file name, so a re-entrant or
 * resumed call (after rotation or process death) finds the already-persisted
 * entry instead of duplicating it. Concurrent calls for the same file are
 * coalesced via an in-flight [CompletableDeferred] map.
 */
class PhotoEntryProcessor(
    private val photoResizer: PhotoResizer,
    private val fileSystem: FileSystemOperations,
    private val trackedEntryRepository: TrackedEntryRepository,
    private val backgroundAnalysisService: BackgroundAnalysisService,
    private val llmClientFactory: LlmClientFactory
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inFlight = mutableMapOf<String, CompletableDeferred<PhotoEntryProcessorResult>>()
    private val inFlightMutex = Mutex()

    /**
     * Processes the photo at [photoFilePath] into a tracked entry.
     *
     * Safe to call repeatedly for the same path: returns the existing entry id
     * if one was already created (or is being created) for that path.
     */
    suspend fun process(photoFilePath: String, userNotes: String): PhotoEntryProcessorResult {
        val blobPath = deriveBlobPath(photoFilePath)

        // Fast path: an entry for this blob already exists (e.g. resumed after
        // process death where the in-flight deferred is gone). Re-queue analysis
        // when the entry is still PENDING or FAILED and an API key is available,
        // so we don't leave orphaned entries that were created before analysis
        // could be queued but never assigned.
        trackedEntryRepository.getEntryByBlobPath(blobPath)?.let { entry ->
            val apiKeyMissing = !llmClientFactory.hasCurrentApiKey()
            if (!apiKeyMissing && (entry.processingStatus == ProcessingStatus.PENDING
                        || entry.processingStatus == ProcessingStatus.FAILED)) {
                backgroundAnalysisService.queueEntry(entry.entryId, userNotes)
            }
            return PhotoEntryProcessorResult(entry.entryId, apiKeyMissing)
        }

        // Coalesce concurrent/re-entrant calls for the same file.
        val deferred = inFlightMutex.withLock {
            inFlight.get(blobPath) ?: run {
                val d = CompletableDeferred<PhotoEntryProcessorResult>()
                inFlight[blobPath] = d
                scope.launch { runProcessing(photoFilePath, blobPath, userNotes, d) }
                d
            }
        }
        return deferred.await()
    }

    private suspend fun runProcessing(
        photoFilePath: String,
        blobPath: String,
        userNotes: String,
        deferred: CompletableDeferred<PhotoEntryProcessorResult>
    ) {
        try {
            // Re-check after acquiring the in-flight slot, in case a parallel
            // call completed an insert between the fast path and the lock.
            // Also re-queue analysis for stale entries.
            trackedEntryRepository.getEntryByBlobPath(blobPath)?.let { entry ->
                val apiKeyMissing = !llmClientFactory.hasCurrentApiKey()
                if (!apiKeyMissing && (entry.processingStatus == ProcessingStatus.PENDING
                            || entry.processingStatus == ProcessingStatus.FAILED)) {
                    backgroundAnalysisService.queueEntry(entry.entryId, userNotes)
                }
                deferred.complete(
                    PhotoEntryProcessorResult(entry.entryId, apiKeyMissing)
                )
                return
            }

            val bytes = fileSystem.readBytes(photoFilePath)
            // Normalize to JPEG and bake in EXIF orientation.
            val jpegBytes = photoResizer.resize(
                photoBytes = bytes,
                maxWidth = 4096,
                maxHeight = 4096,
                quality = 95
            )

            val photosDir = "${fileSystem.getAppDataDirectory()}/photos"
            fileSystem.createDirectory(photosDir)
            fileSystem.writeBytes(blobPath, jpegBytes)
            generatePreview(jpegBytes, blobPath)

            val entry = TrackedEntry(
                entryType = EntryType.UNKNOWN,
                capturedAt = Clock.System.now(),
                userNotes = userNotes.ifBlank { null },
                blobPath = blobPath
            )
            val entryId = trackedEntryRepository.insertEntry(entry)

            val apiKeyMissing = !llmClientFactory.hasCurrentApiKey()
            if (!apiKeyMissing) {
                backgroundAnalysisService.queueEntry(entryId, userNotes)
            }
            deferred.complete(PhotoEntryProcessorResult(entryId, apiKeyMissing))
        } catch (e: Exception) {
            if (e is CancellationException) {
                deferred.cancel(e)
                throw e
            }
            Napier.e("Failed to process photo entry from $photoFilePath", e)
            deferred.completeExceptionally(e)
        } finally {
            inFlightMutex.withLock { inFlight.remove(blobPath) }
        }
    }

    private suspend fun generatePreview(photoBytes: ByteArray, blobPath: String) {
        try {
            val previewPath = previewPathFor(blobPath)
            val previewBytes = photoResizer.resize(
                photoBytes = photoBytes,
                maxWidth = 400,
                maxHeight = 400,
                quality = 70,
                cropHeight = true
            )
            fileSystem.writeBytes(previewPath, previewBytes)
            Napier.d("Generated preview at $previewPath (${previewBytes.size} bytes)")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.w("Failed to generate preview for $blobPath", e)
        }
    }

    /**
     * Derives the deterministic tracked-entry blob path from the pending photo
     * file path. Two calls for the same pending file always yield the same
     * blob path, which is what makes [process] idempotent across recreation.
     */
    fun deriveBlobPath(photoFilePath: String): String {
        val photosDir = "${fileSystem.getAppDataDirectory()}/photos"
        val name = photoFilePath.substringAfterLast('/').substringBeforeLast('.')
        return "$photosDir/$name.jpg"
    }

    companion object {
        /**
         * Returns the preview/thumbnail path for a given blob path. Mirrors the
         * convention used by [com.wellnesswingman.ui.screens.photo.PhotoReviewViewModel]
         * so previews are shared across the app.
         */
        fun previewPathFor(blobPath: String): String {
            val lastDot = blobPath.lastIndexOf('.')
            return if (lastDot > 0) {
                "${blobPath.substring(0, lastDot)}_preview${blobPath.substring(lastDot)}"
            } else {
                "${blobPath}_preview"
            }
        }
    }
}
