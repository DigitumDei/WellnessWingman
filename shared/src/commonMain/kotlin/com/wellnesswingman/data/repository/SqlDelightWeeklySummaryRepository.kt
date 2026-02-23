package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.WeeklySummary
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * SQLDelight implementation of WeeklySummaryRepository.
 */
class SqlDelightWeeklySummaryRepository(
    private val database: WellnessWingmanDatabase
) : WeeklySummaryRepository {

    private val queries = database.weeklySummaryQueries

    override suspend fun getAllSummaries(): List<WeeklySummary> = withContext(Dispatchers.IO) {
        queries.getAllSummaries().executeAsList().map { it.toWeeklySummary() }
    }

    override suspend fun getSummaryById(id: Long): WeeklySummary? = withContext(Dispatchers.IO) {
        queries.getSummaryById(id).executeAsOneOrNull()?.toWeeklySummary()
    }

    override suspend fun getSummaryForWeek(weekStart: LocalDate): WeeklySummary? =
        withContext(Dispatchers.IO) {
            queries.getSummaryForWeek(weekStart.toEpochDays().toLong())
                .executeAsOneOrNull()?.toWeeklySummary()
        }

    override suspend fun getSummariesForDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WeeklySummary> = withContext(Dispatchers.IO) {
        queries.getSummariesForDateRange(
            startDate.toEpochDays().toLong(),
            endDate.toEpochDays().toLong()
        ).executeAsList().map { it.toWeeklySummary() }
    }

    override suspend fun getRecentSummaries(limit: Long): List<WeeklySummary> =
        withContext(Dispatchers.IO) {
            queries.getRecentSummaries(limit).executeAsList().map { it.toWeeklySummary() }
        }

    override suspend fun insertSummary(summary: WeeklySummary): Long = withContext(Dispatchers.IO) {
        queries.insertSummary(
            weekStartDate = summary.weekStartDate.toEpochDays().toLong(),
            highlights = summary.highlights,
            recommendations = summary.recommendations,
            mealCount = summary.mealCount.toLong(),
            exerciseCount = summary.exerciseCount.toLong(),
            sleepCount = summary.sleepCount.toLong(),
            otherCount = summary.otherCount.toLong(),
            totalEntries = summary.totalEntries.toLong(),
            generatedAt = summary.generatedAt?.toEpochMilliseconds()
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun updateSummary(summary: WeeklySummary) =
        withContext(Dispatchers.IO) {
            queries.updateSummary(
                highlights = summary.highlights,
                recommendations = summary.recommendations,
                mealCount = summary.mealCount.toLong(),
                exerciseCount = summary.exerciseCount.toLong(),
                sleepCount = summary.sleepCount.toLong(),
                otherCount = summary.otherCount.toLong(),
                totalEntries = summary.totalEntries.toLong(),
                generatedAt = summary.generatedAt?.toEpochMilliseconds(),
                summaryId = summary.summaryId
            )
        }

    override suspend fun updateSummaryByWeek(
        weekStart: LocalDate,
        summary: WeeklySummary
    ) = withContext(Dispatchers.IO) {
        queries.updateSummaryByWeek(
            highlights = summary.highlights,
            recommendations = summary.recommendations,
            mealCount = summary.mealCount.toLong(),
            exerciseCount = summary.exerciseCount.toLong(),
            sleepCount = summary.sleepCount.toLong(),
            otherCount = summary.otherCount.toLong(),
            totalEntries = summary.totalEntries.toLong(),
            generatedAt = summary.generatedAt?.toEpochMilliseconds(),
            weekStartDate = weekStart.toEpochDays().toLong()
        )
    }

    override suspend fun deleteSummary(id: Long) = withContext(Dispatchers.IO) {
        queries.deleteSummary(id)
    }

    override suspend fun deleteSummaryByWeek(weekStart: LocalDate) = withContext(Dispatchers.IO) {
        queries.deleteSummaryByWeek(weekStart.toEpochDays().toLong())
    }

    override suspend fun deleteOldSummaries(beforeDate: LocalDate) = withContext(Dispatchers.IO) {
        queries.deleteOldSummaries(beforeDate.toEpochDays().toLong())
    }

    /**
     * Maps SQLDelight WeeklySummary to domain WeeklySummary.
     */
    private fun com.wellnesswingman.db.WeeklySummary.toWeeklySummary(): WeeklySummary {
        return WeeklySummary(
            summaryId = summaryId,
            weekStartDate = LocalDate.fromEpochDays(weekStartDate.toInt()),
            highlights = highlights,
            recommendations = recommendations,
            mealCount = mealCount.toInt(),
            exerciseCount = exerciseCount.toInt(),
            sleepCount = sleepCount.toInt(),
            otherCount = otherCount.toInt(),
            totalEntries = totalEntries.toInt(),
            generatedAt = generatedAt?.let { Instant.fromEpochMilliseconds(it) }
        )
    }
}
