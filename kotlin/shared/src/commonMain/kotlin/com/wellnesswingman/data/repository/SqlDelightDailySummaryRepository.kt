package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * SQLDelight implementation of DailySummaryRepository.
 */
class SqlDelightDailySummaryRepository(
    private val database: WellnessWingmanDatabase
) : DailySummaryRepository {

    private val queries = database.dailySummaryQueries

    override suspend fun getAllSummaries(): List<DailySummary> = withContext(Dispatchers.IO) {
        queries.getAllSummaries().executeAsList().map { it.toDailySummary() }
    }

    override suspend fun getSummaryById(id: Long): DailySummary? = withContext(Dispatchers.IO) {
        queries.getSummaryById(id).executeAsOneOrNull()?.toDailySummary()
    }

    override suspend fun getSummaryByExternalId(externalId: String): DailySummary? =
        withContext(Dispatchers.IO) {
            queries.getSummaryByExternalId(externalId).executeAsOneOrNull()?.toDailySummary()
        }

    override suspend fun getSummaryForDate(date: LocalDate): DailySummary? =
        withContext(Dispatchers.IO) {
            queries.getSummaryForDate(date.toEpochDays().toLong())
                .executeAsOneOrNull()?.toDailySummary()
        }

    override suspend fun getSummariesForDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailySummary> = withContext(Dispatchers.IO) {
        queries.getSummariesForDateRange(
            startDate.toEpochDays().toLong(),
            endDate.toEpochDays().toLong()
        ).executeAsList().map { it.toDailySummary() }
    }

    override suspend fun getRecentSummaries(limit: Long): List<DailySummary> =
        withContext(Dispatchers.IO) {
            queries.getRecentSummaries(limit).executeAsList().map { it.toDailySummary() }
        }

    override suspend fun insertSummary(summary: DailySummary): Long = withContext(Dispatchers.IO) {
        queries.insertSummary(
            externalId = summary.externalId,
            summaryDate = summary.summaryDate.toEpochDays().toLong(),
            highlights = summary.highlights,
            recommendations = summary.recommendations,
            generatedAt = summary.generatedAt?.toEpochMilliseconds()
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun updateSummary(id: Long, highlights: String, recommendations: String) =
        withContext(Dispatchers.IO) {
            queries.updateSummary(highlights, recommendations, null, id)
        }

    override suspend fun updateSummaryByDate(
        date: LocalDate,
        highlights: String,
        recommendations: String
    ) = withContext(Dispatchers.IO) {
        queries.updateSummaryByDate(highlights, recommendations, null, date.toEpochDays().toLong())
    }

    override suspend fun deleteSummary(id: Long) = withContext(Dispatchers.IO) {
        queries.deleteSummary(id)
    }

    override suspend fun deleteSummaryByDate(date: LocalDate) = withContext(Dispatchers.IO) {
        queries.deleteSummaryByDate(date.toEpochDays().toLong())
    }

    override suspend fun deleteOldSummaries(beforeDate: LocalDate) = withContext(Dispatchers.IO) {
        queries.deleteOldSummaries(beforeDate.toEpochDays().toLong())
    }

    /**
     * Maps SQLDelight DailySummary to domain DailySummary.
     */
    private fun com.wellnesswingman.db.DailySummary.toDailySummary(): DailySummary {
        return DailySummary(
            summaryId = summaryId,
            externalId = externalId,
            summaryDate = LocalDate.fromEpochDays(summaryDate.toInt()),
            highlights = highlights,
            recommendations = recommendations,
            generatedAt = generatedAt?.let { Instant.fromEpochMilliseconds(it) }
        )
    }
}
