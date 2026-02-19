package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.WeightRecord
import kotlinx.datetime.Instant

/**
 * Repository interface for weight history records.
 */
interface WeightHistoryRepository {
    suspend fun addWeightRecord(record: WeightRecord): Long
    suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<WeightRecord>
    suspend fun getLatestWeightRecord(): WeightRecord?
    suspend fun getAllWeightRecords(): List<WeightRecord>
    suspend fun deleteWeightRecord(recordId: Long)
    suspend fun upsertWeightRecord(record: WeightRecord)
}
