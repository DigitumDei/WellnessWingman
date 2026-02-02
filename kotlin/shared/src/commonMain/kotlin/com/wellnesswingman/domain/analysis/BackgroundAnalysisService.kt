package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.events.StatusChangeNotifier
import com.wellnesswingman.platform.BackgroundExecutionService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service for queueing entries for background analysis.
 * Manages the analysis lifecycle and emits status change events.
 */
interface BackgroundAnalysisService {
    /**
     * Queue an entry for background analysis.
     *
     * @param entryId The ID of the entry to analyze
     * @param userProvidedDetails Optional user-provided context for analysis
     */
    fun queueEntry(entryId: Long, userProvidedDetails: String? = null)

    /**
     * Queue a correction analysis for an entry (re-analysis with user feedback).
     *
     * @param entryId The ID of the entry to re-analyze
     * @param correction The user's correction text
     */
    fun queueCorrection(entryId: Long, correction: String)
}

/**
 * Default implementation of BackgroundAnalysisService.
 */
class DefaultBackgroundAnalysisService(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val entryAnalysisRepository: EntryAnalysisRepository,
    private val analysisOrchestrator: AnalysisOrchestrator,
    private val backgroundExecutionService: BackgroundExecutionService,
    private val statusChangeNotifier: StatusChangeNotifier
) : BackgroundAnalysisService {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val ORIGINAL_NOTES_PREFIX = "Original user notes: "
        private const val CORRECTION_PREFIX = "\n\nCorrection: "
    }

    override fun queueEntry(entryId: Long, userProvidedDetails: String?) {
        serviceScope.launch {
            val taskName = "analyze-$entryId"
            backgroundExecutionService.startBackgroundTask(taskName)

            try {
                processEntry(entryId, userProvidedDetails)
            } finally {
                backgroundExecutionService.stopBackgroundTask(taskName)
            }
        }
    }

    override fun queueCorrection(entryId: Long, correction: String) {
        serviceScope.launch {
            val taskName = "correct-$entryId"
            backgroundExecutionService.startBackgroundTask(taskName)

            try {
                processCorrection(entryId, correction)
            } finally {
                backgroundExecutionService.stopBackgroundTask(taskName)
            }
        }
    }

    private suspend fun processEntry(entryId: Long, userProvidedDetails: String?) {
        try {
            updateStatus(entryId, ProcessingStatus.PROCESSING)

            val entry = trackedEntryRepository.getEntryById(entryId)
            if (entry == null) {
                Napier.w("Entry $entryId not found for analysis")
                return
            }

            // Use provided details, or fall back to persisted UserNotes from the entry
            val effectiveDetails = if (!userProvidedDetails.isNullOrBlank()) {
                userProvidedDetails
            } else {
                entry.userNotes
            }

            val result = analysisOrchestrator.processEntry(entry, effectiveDetails)

            val finalStatus = when (result) {
                is AnalysisInvocationResult.Success -> ProcessingStatus.COMPLETED
                is AnalysisInvocationResult.Skipped -> ProcessingStatus.SKIPPED
                is AnalysisInvocationResult.Error -> ProcessingStatus.FAILED
            }

            updateStatus(entryId, finalStatus)

        } catch (e: CancellationException) {
            Napier.i("Background analysis was cancelled for entry $entryId")
            updateStatus(entryId, ProcessingStatus.PENDING)
            throw e
        } catch (e: Exception) {
            Napier.e("Background analysis failed for entry $entryId", e)
            updateStatus(entryId, ProcessingStatus.FAILED)
        }
    }

    private suspend fun processCorrection(entryId: Long, correction: String) {
        try {
            updateStatus(entryId, ProcessingStatus.PROCESSING)

            val entry = trackedEntryRepository.getEntryById(entryId)
            if (entry == null) {
                Napier.w("Entry $entryId not found for correction analysis")
                return
            }

            val existingAnalysis = entryAnalysisRepository.getLatestAnalysisForEntry(entryId)
            if (existingAnalysis == null) {
                Napier.w("No existing analysis found for entry $entryId. Cannot apply correction.")
                updateStatus(entryId, ProcessingStatus.FAILED)
                return
            }

            // Combine original user notes with the correction for context
            val combinedContext = if (!entry.userNotes.isNullOrBlank()) {
                "$ORIGINAL_NOTES_PREFIX${entry.userNotes}$CORRECTION_PREFIX$correction"
            } else {
                correction
            }

            // Re-process with the combined context
            val result = analysisOrchestrator.processEntry(entry, combinedContext)

            val finalStatus = when (result) {
                is AnalysisInvocationResult.Success -> ProcessingStatus.COMPLETED
                is AnalysisInvocationResult.Skipped -> ProcessingStatus.SKIPPED
                is AnalysisInvocationResult.Error -> ProcessingStatus.FAILED
            }

            updateStatus(entryId, finalStatus)

        } catch (e: CancellationException) {
            Napier.i("Correction analysis was cancelled for entry $entryId")
            updateStatus(entryId, ProcessingStatus.PENDING)
            throw e
        } catch (e: Exception) {
            Napier.e("Correction analysis failed for entry $entryId", e)
            updateStatus(entryId, ProcessingStatus.FAILED)
        }
    }

    private suspend fun updateStatus(entryId: Long, status: ProcessingStatus) {
        trackedEntryRepository.updateEntryStatus(entryId, status)
        statusChangeNotifier.notifyStatusChange(entryId, status)
    }
}
