package com.wellnesswingman.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.db.WellnessWingmanDatabase
import com.wellnesswingman.util.DateTimeConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * SQLDelight implementation of TrackedEntryRepository.
 */
class SqlDelightTrackedEntryRepository(
    private val database: WellnessWingmanDatabase
) : TrackedEntryRepository {

    private val queries = database.trackedEntryQueries

    override suspend fun getAllEntries(): List<TrackedEntry> = withContext(Dispatchers.IO) {
        queries.getAllEntries().executeAsList().map { it.toTrackedEntry() }
    }

    override fun observeAllEntries(): Flow<List<TrackedEntry>> {
        return queries.getAllEntries()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toTrackedEntry() } }
    }

    override suspend fun getEntryById(id: Long): TrackedEntry? = withContext(Dispatchers.IO) {
        queries.getEntryById(id).executeAsOneOrNull()?.toTrackedEntry()
    }

    override suspend fun getEntryByExternalId(externalId: String): TrackedEntry? =
        withContext(Dispatchers.IO) {
            queries.getEntryByExternalId(externalId).executeAsOneOrNull()?.toTrackedEntry()
        }

    override suspend fun getEntriesForDay(
        startMillis: Long,
        endMillis: Long
    ): List<TrackedEntry> = withContext(Dispatchers.IO) {
        queries.getEntriesForDay(startMillis, endMillis)
            .executeAsList()
            .map { it.toTrackedEntry() }
    }

    override suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry> =
        withContext(Dispatchers.IO) {
            val (start, end) = DateTimeConverter.getUtcBoundsForLocalDay(
                date,
                TimeZone.currentSystemDefault()
            )
            queries.getEntriesForDay(
                start.toEpochMilliseconds(),
                end.toEpochMilliseconds()
            ).executeAsList().map { it.toTrackedEntry() }
        }

    override fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>> {
        val (start, end) = DateTimeConverter.getUtcBoundsForLocalDay(
            date,
            TimeZone.currentSystemDefault()
        )
        return queries.getEntriesForDay(
            start.toEpochMilliseconds(),
            end.toEpochMilliseconds()
        )
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toTrackedEntry() } }
    }

    override suspend fun getEntriesForWeek(
        startMillis: Long,
        endMillis: Long
    ): List<TrackedEntry> = withContext(Dispatchers.IO) {
        queries.getEntriesForWeek(startMillis, endMillis)
            .executeAsList()
            .map { it.toTrackedEntry() }
    }

    override suspend fun getEntriesForMonth(
        startMillis: Long,
        endMillis: Long
    ): List<TrackedEntry> = withContext(Dispatchers.IO) {
        queries.getEntriesForMonth(startMillis, endMillis)
            .executeAsList()
            .map { it.toTrackedEntry() }
    }

    override suspend fun getEntriesByStatus(status: ProcessingStatus): List<TrackedEntry> =
        withContext(Dispatchers.IO) {
            queries.getEntriesByStatus(status.toStorageString())
                .executeAsList()
                .map { it.toTrackedEntry() }
        }

    override suspend fun getPendingEntries(): List<TrackedEntry> = withContext(Dispatchers.IO) {
        queries.getPendingEntries().executeAsList().map { it.toTrackedEntry() }
    }

    override suspend fun insertEntry(entry: TrackedEntry): Long = withContext(Dispatchers.IO) {
        queries.insertEntry(
            externalId = entry.externalId,
            entryType = entry.entryType.toStorageString(),
            capturedAt = entry.capturedAt.toEpochMilliseconds(),
            capturedAtTimeZoneId = entry.capturedAtTimeZoneId,
            capturedAtOffsetMinutes = entry.capturedAtOffsetMinutes?.toLong(),
            blobPath = entry.blobPath,
            dataPayload = entry.dataPayload,
            dataSchemaVersion = entry.dataSchemaVersion.toLong(),
            processingStatus = entry.processingStatus.toStorageString(),
            userNotes = entry.userNotes
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun updateEntryStatus(id: Long, status: ProcessingStatus) =
        withContext(Dispatchers.IO) {
            queries.updateEntryStatus(status.toStorageString(), id)
        }

    override suspend fun updateEntryType(id: Long, entryType: EntryType) =
        withContext(Dispatchers.IO) {
            queries.updateEntryType(entryType.toStorageString(), id)
        }

    override suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int) =
        withContext(Dispatchers.IO) {
            queries.updateEntryPayload(payload, schemaVersion.toLong(), id)
        }

    override suspend fun updateUserNotes(id: Long, notes: String?) = withContext(Dispatchers.IO) {
        queries.updateUserNotes(notes, id)
    }

    override suspend fun deleteEntry(id: Long) = withContext(Dispatchers.IO) {
        queries.deleteEntry(id)
    }

    /**
     * Maps SQLDelight TrackedEntry to domain TrackedEntry.
     */
    private fun com.wellnesswingman.db.TrackedEntry.toTrackedEntry(): TrackedEntry {
        return TrackedEntry(
            entryId = entryId,
            externalId = externalId,
            entryType = EntryType.fromString(entryType),
            capturedAt = Instant.fromEpochMilliseconds(capturedAt),
            capturedAtTimeZoneId = capturedAtTimeZoneId,
            capturedAtOffsetMinutes = capturedAtOffsetMinutes?.toInt(),
            blobPath = blobPath,
            dataPayload = dataPayload,
            dataSchemaVersion = dataSchemaVersion.toInt(),
            processingStatus = ProcessingStatus.fromString(processingStatus),
            userNotes = userNotes
        )
    }
}
