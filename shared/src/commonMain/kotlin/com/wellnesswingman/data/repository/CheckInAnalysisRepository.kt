package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.CheckInSlot
import kotlinx.datetime.LocalDate

/**
 * Stores what was extracted from check-in free text.
 *
 * Everything here is derived from `DailyCheckIn.responseText` and can be regenerated, so callers
 * are free to delete and re-run rather than migrate.
 */
interface CheckInAnalysisRepository {

    suspend fun getAllAnalyses(): List<CheckInAnalysis>

    suspend fun getAnalysis(date: LocalDate, slot: CheckInSlot): CheckInAnalysis?

    /** Both slots for a day, so a day screen totals mentioned food in a single read. */
    suspend fun getAnalysesForDate(date: LocalDate): List<CheckInAnalysis>

    suspend fun getAnalysesForDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<CheckInAnalysis>

    suspend fun getAnalysisByExternalId(externalId: String): CheckInAnalysis?

    /** Inserts or replaces the analysis for its day and slot. */
    suspend fun saveAnalysis(analysis: CheckInAnalysis): Long

    suspend fun deleteAnalysis(date: LocalDate, slot: CheckInSlot)

    suspend fun deleteOldAnalyses(beforeDate: LocalDate)

    /** Preserves the primary key, for import. */
    suspend fun upsertAnalysis(analysis: CheckInAnalysis)
}
