package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.WeightRecord
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * SQLDelight implementation of WeightHistoryRepository.
 */
class SqlDelightWeightHistoryRepository(
    private val database: WellnessWingmanDatabase
) : WeightHistoryRepository {

    private val queries = database.weightRecordQueries

    override suspend fun addWeightRecord(record: WeightRecord): Long = withContext(Dispatchers.IO) {
        queries.insertWeightRecord(
            externalId = record.externalId,
            recordedAt = record.recordedAt.toEpochMilliseconds(),
            weightValue = record.weightValue,
            weightUnit = record.weightUnit,
            source = record.source,
            relatedEntryId = record.relatedEntryId
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<WeightRecord> =
        withContext(Dispatchers.IO) {
            queries.getWeightRecordsForRange(
                startDate.toEpochMilliseconds(),
                endDate.toEpochMilliseconds()
            ).executeAsList().map { it.toWeightRecord() }
        }

    override suspend fun getLatestWeightRecord(): WeightRecord? = withContext(Dispatchers.IO) {
        queries.getLatestWeightRecord().executeAsOneOrNull()?.toWeightRecord()
    }

    override suspend fun getAllWeightRecords(): List<WeightRecord> = withContext(Dispatchers.IO) {
        queries.getAllWeightRecords().executeAsList().map { it.toWeightRecord() }
    }

    override suspend fun deleteWeightRecord(recordId: Long) = withContext(Dispatchers.IO) {
        queries.deleteWeightRecord(recordId)
    }

    override suspend fun upsertWeightRecord(record: WeightRecord) = withContext(Dispatchers.IO) {
        if (record.weightRecordId > 0) {
            queries.updateWeightRecord(
                externalId = record.externalId,
                recordedAt = record.recordedAt.toEpochMilliseconds(),
                weightValue = record.weightValue,
                weightUnit = record.weightUnit,
                source = record.source,
                relatedEntryId = record.relatedEntryId,
                weightRecordId = record.weightRecordId
            )
        } else {
            queries.insertWeightRecord(
                externalId = record.externalId,
                recordedAt = record.recordedAt.toEpochMilliseconds(),
                weightValue = record.weightValue,
                weightUnit = record.weightUnit,
                source = record.source,
                relatedEntryId = record.relatedEntryId
            )
        }
    }

    private fun com.wellnesswingman.db.WeightRecord.toWeightRecord(): WeightRecord {
        return WeightRecord(
            weightRecordId = weightRecordId,
            externalId = externalId,
            recordedAt = Instant.fromEpochMilliseconds(recordedAt),
            weightValue = weightValue,
            weightUnit = weightUnit,
            source = source,
            relatedEntryId = relatedEntryId
        )
    }
}
