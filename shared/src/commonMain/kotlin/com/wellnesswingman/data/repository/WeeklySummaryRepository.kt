package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.WeeklySummary
import kotlinx.datetime.LocalDate

/**
 * Repository interface for weekly summaries.
 */
interface WeeklySummaryRepository {
    suspend fun getAllSummaries(): List<WeeklySummary>
    suspend fun getSummaryById(id: Long): WeeklySummary?
    suspend fun getSummaryForWeek(weekStart: LocalDate): WeeklySummary?
    suspend fun getSummariesForDateRange(startDate: LocalDate, endDate: LocalDate): List<WeeklySummary>
    suspend fun getRecentSummaries(limit: Long): List<WeeklySummary>
    suspend fun insertSummary(summary: WeeklySummary): Long
    suspend fun updateSummary(summary: WeeklySummary)
    suspend fun updateSummaryByWeek(weekStart: LocalDate, summary: WeeklySummary)
    suspend fun deleteSummary(id: Long)
    suspend fun deleteSummaryByWeek(weekStart: LocalDate)
    suspend fun deleteOldSummaries(beforeDate: LocalDate)
}
