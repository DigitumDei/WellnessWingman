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
    fun observeAllEntries(): Flow<List<TrackedEntry>>
    suspend fun getEntryById(id: Long): TrackedEntry?
    suspend fun getEntryByExternalId(externalId: String): TrackedEntry?
    suspend fun getEntriesForDay(startMillis: Long, endMillis: Long): List<TrackedEntry>
    suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry>
    fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>>
    suspend fun getEntriesForWeek(startMillis: Long, endMillis: Long): List<TrackedEntry>
    suspend fun getEntriesForMonth(startMillis: Long, endMillis: Long): List<TrackedEntry>
    suspend fun getEntriesByStatus(status: ProcessingStatus): List<TrackedEntry>
    suspend fun getPendingEntries(): List<TrackedEntry>
    suspend fun insertEntry(entry: TrackedEntry): Long
    suspend fun updateEntryStatus(id: Long, status: ProcessingStatus)
    suspend fun updateEntryType(id: Long, entryType: EntryType)
    suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int)
    suspend fun updateUserNotes(id: Long, notes: String?)
    suspend fun deleteEntry(id: Long)
}
