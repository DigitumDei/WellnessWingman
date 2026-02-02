package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.repository.TrackedEntryRepository
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Service to recover entries that were left in Processing state due to app shutdown.
 * Should be called on app startup.
 */
interface StaleEntryRecoveryService {
    /**
     * Find and reset any entries stuck in "Processing" state.
     */
    suspend fun recoverStaleEntries()
}

/**
 * Default implementation of StaleEntryRecoveryService.
 */
class DefaultStaleEntryRecoveryService(
    private val trackedEntryRepository: TrackedEntryRepository
) : StaleEntryRecoveryService {

    override suspend fun recoverStaleEntries() {
        try {
            Napier.i("Checking for stale processing entries on app startup...")

            // Get today's date
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            // Get entries for today to check for stale Processing states
            val entries = trackedEntryRepository.getEntriesForDay(today)

            val staleEntries = entries.filter { it.processingStatus == ProcessingStatus.PROCESSING }

            if (staleEntries.isEmpty()) {
                Napier.i("No stale processing entries found.")
                return
            }

            Napier.w("Found ${staleEntries.size} stale processing entries. Resetting to Pending state.")

            for (entry in staleEntries) {
                Napier.i("Resetting entry ${entry.entryId} from Processing to Pending.")
                trackedEntryRepository.updateEntryStatus(entry.entryId, ProcessingStatus.PENDING)
            }

            Napier.i("Stale entry recovery completed. ${staleEntries.size} entries reset.")

        } catch (e: Exception) {
            Napier.e("Failed to recover stale processing entries.", e)
        }
    }
}
