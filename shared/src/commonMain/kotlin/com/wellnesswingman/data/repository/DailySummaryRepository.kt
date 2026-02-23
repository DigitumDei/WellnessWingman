package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.DailySummary
import kotlinx.datetime.LocalDate

/**
 * Repository interface for daily summaries.
 */
interface DailySummaryRepository {
    suspend fun getAllSummaries(): List<DailySummary>
    suspend fun getSummaryById(id: Long): DailySummary?
    suspend fun getSummaryByExternalId(externalId: String): DailySummary?
    suspend fun getSummaryForDate(date: LocalDate): DailySummary?
    suspend fun getSummariesForDateRange(startDate: LocalDate, endDate: LocalDate): List<DailySummary>
    suspend fun getRecentSummaries(limit: Long): List<DailySummary>
    suspend fun insertSummary(summary: DailySummary): Long
    suspend fun updateSummary(id: Long, highlights: String, recommendations: String)
    suspend fun updateSummaryByDate(date: LocalDate, highlights: String, recommendations: String)
    suspend fun deleteSummary(id: Long)
    suspend fun deleteSummaryByDate(date: LocalDate)
    suspend fun deleteOldSummaries(beforeDate: LocalDate)
    suspend fun upsertSummary(summary: DailySummary)
}
