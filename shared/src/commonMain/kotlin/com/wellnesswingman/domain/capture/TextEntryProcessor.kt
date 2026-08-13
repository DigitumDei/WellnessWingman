package com.wellnesswingman.domain.capture

import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.analysis.BackgroundAnalysisService
import com.wellnesswingman.domain.llm.LlmClientFactory
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
 * Result of turning a written description into a tracked entry.
 *
 * @param entryId the persisted entry id.
 * @param apiKeyMissing true when no LLM API key is configured, so the entry was saved but its
 *   analysis could not be queued and the UI should say so.
 */
data class TextEntryProcessorResult(
    val entryId: Long,
    val apiKeyMissing: Boolean
)

/**
 * App-scoped processor that turns a written description into a tracked entry.
 *
 * Exists for the case where the thing happened but the photo did not: a meal eaten out, a run
 * whose watch died. The entry is stored with no `blobPath`, which
 * [com.wellnesswingman.domain.analysis.AnalysisOrchestrator] already understands — it routes such
 * entries to `generateCompletion` instead of `analyzeImage`.
 *
 * Sibling to [PhotoEntryProcessor] rather than a mode inside it. Almost all of that class —
 * resizing, preview generation, blob-path dedup — is specific to having a file, and the one part
 * that would carry over, idempotency, cannot: it derives its key from the photo's filename, and a
 * description has no equivalent natural key.
 *
 * Runs in an application-owned scope so the work survives the screen being destroyed, matching
 * [PhotoEntryProcessor].
 */
class TextEntryProcessor(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val backgroundAnalysisService: BackgroundAnalysisService,
    private val llmClientFactory: LlmClientFactory,
    private val clock: Clock = Clock.System,
    /** Overridable so tests can supply a scope they control rather than racing a background one. */
    scope: CoroutineScope? = null
) {
    private val scope: CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val inFlightMutex = Mutex()
    private var inFlight: CompletableDeferred<TextEntryProcessorResult>? = null
    private var inFlightKey: String? = null

    /**
     * Creates an entry from [description] and queues its analysis.
     *
     * Concurrent calls carrying the same text are coalesced into one entry. Without a file to key
     * on there is no way to recognise a duplicate after the fact, so a double-tapped save would
     * otherwise create two entries that look identical and cost two analyses. The guard covers
     * the realistic case — the same text submitted twice while the first is still in flight — and
     * deliberately does not try to deduplicate across app restarts, where an identical
     * description hours later is far more likely to be a real second helping than a mistake.
     *
     * @param description what the user wrote or dictated. Blank is rejected: an entry with
     *   neither photo nor words has nothing to analyse.
     */
    suspend fun process(description: String): TextEntryProcessorResult {
        val text = description.trim()
        require(text.isNotEmpty()) { "A text entry needs a description" }

        val existing = inFlightMutex.withLock {
            if (inFlightKey == text) inFlight else null
        }
        if (existing != null) return existing.await()

        val deferred = inFlightMutex.withLock {
            if (inFlightKey == text) {
                inFlight
            } else {
                val fresh = CompletableDeferred<TextEntryProcessorResult>()
                inFlightKey = text
                inFlight = fresh
                scope.launch { runProcessing(text, fresh) }
                fresh
            }
        }

        return deferred?.await() ?: error("Failed to start processing the entry")
    }

    private suspend fun runProcessing(
        text: String,
        deferred: CompletableDeferred<TextEntryProcessorResult>
    ) {
        try {
            val entry = TrackedEntry(
                // The analysis decides what this actually is, exactly as it does for a photo.
                entryType = EntryType.UNKNOWN,
                capturedAt = clock.now(),
                userNotes = text,
                blobPath = null
            )

            val entryId = trackedEntryRepository.insertEntry(entry)

            val apiKeyMissing = !llmClientFactory.hasCurrentApiKey()
            if (apiKeyMissing) {
                // SKIPPED, not the default PENDING. EntryDetailScreen offers a re-analyse
                // control for SKIPPED and FAILED entries only, so leaving this PENDING would
                // strand the entry: once a key is configured there would be no way to analyse
                // it short of deleting and retyping it. Matches what AnalysisOrchestrator does
                // when it finds no key.
                trackedEntryRepository.updateEntryStatus(entryId, ProcessingStatus.SKIPPED)
            } else {
                backgroundAnalysisService.queueEntry(entryId, text)
            }

            deferred.complete(TextEntryProcessorResult(entryId, apiKeyMissing))
        } catch (e: Exception) {
            if (e is CancellationException) {
                deferred.cancel(e)
                throw e
            }
            Napier.e("Failed to create a text entry", e)
            deferred.completeExceptionally(e)
        } finally {
            inFlightMutex.withLock {
                if (inFlightKey == text) {
                    inFlightKey = null
                    inFlight = null
                }
            }
        }
    }
}
