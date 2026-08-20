package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Repository interface for tracked entries.
 */
interface TrackedEntryRepository {
    suspend fun getAllEntries(): List<TrackedEntry>
    suspend fun getRecentEntries(limit: Int, entryType: EntryType? = null): List<TrackedEntry>
    fun observeAllEntries(): Flow<List<TrackedEntry>>
    suspend fun getEntryById(id: Long): TrackedEntry?
    suspend fun getEntryByExternalId(externalId: String): TrackedEntry?

    /**
     * Returns the entry whose blob path matches, or null. Used to make photo
     * entry creation idempotent across configuration changes and process death:
     * the derived blob path is deterministic, so a re-entrant or resumed
     * creation finds the already-persisted entry instead of duplicating it.
     *
     * Default returns null so test fakes that do not exercise this path keep
     * compiling without an override.
     */
    suspend fun getEntryByBlobPath(blobPath: String): TrackedEntry?
    suspend fun getEntriesForDay(startMillis: Long, endMillis: Long): List<TrackedEntry>
    suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry>
    fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>>

    /**
     * Returns entries captured in the half-open range
     * [startInclusive, endExclusive), ordered chronologically (capturedAt
     * ascending, then entryId ascending for deterministic ties).
     *
     * Default returns an empty list so test fakes that do not exercise this
     * path keep compiling without an override.
     */
    suspend fun getEntriesInRange(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<TrackedEntry> = emptyList()

    suspend fun getEntriesByStatus(status: ProcessingStatus): List<TrackedEntry>
    suspend fun getPendingEntries(): List<TrackedEntry>
    suspend fun insertEntry(entry: TrackedEntry): Long
    suspend fun updateEntryStatus(id: Long, status: ProcessingStatus)
    suspend fun updateEntryType(id: Long, entryType: EntryType)
    suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int)
    suspend fun updateUserNotes(id: Long, notes: String?)
    suspend fun deleteEntry(id: Long)
    suspend fun upsertEntry(entry: TrackedEntry)
}
