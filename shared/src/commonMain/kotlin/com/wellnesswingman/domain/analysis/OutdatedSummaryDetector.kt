package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Utility for detecting if a daily summary is outdated.
 * A summary is considered outdated if new entries have been added
 * after the summary was generated.
 */
class OutdatedSummaryDetector(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val dailySummaryRepository: DailySummaryRepository
) {

    /**
     * Checks if the summary for the given date is outdated.
     *
     * @param date The date to check
     * @param timeZone The timezone to use for date calculations
     * @return true if the summary is outdated (new entries added after generation)
     */
    suspend fun isSummaryOutdated(
        date: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        // Get the existing summary
        val summary = dailySummaryRepository.getSummaryForDate(date)
            ?: return false // No summary exists, not outdated (just missing)

        val generatedAt = summary.generatedAt
            ?: return true // No generation timestamp, consider outdated to be safe

        // Get entries for the day
        val (startMillis, endMillis) = DateTimeUtil.getDayBounds(date, timeZone)
        val entries = trackedEntryRepository.getEntriesForDay(startMillis, endMillis)

        // Filter to completed entries only (exclude daily summary entries)
        val completedEntries = entries
            .filter { it.entryType != EntryType.DAILY_SUMMARY }
            .filter { it.processingStatus == ProcessingStatus.COMPLETED }

        // Check if any entry was completed after the summary was generated
        return completedEntries.any { entry ->
            entry.capturedAt > generatedAt
        }
    }

    /**
     * Checks if a summary is outdated based on a DailySummary object and entries.
     *
     * @param summary The summary to check
     * @param entries The entries for the same day
     * @return true if the summary is outdated
     */
    fun isSummaryOutdated(
        summary: DailySummary,
        entries: List<TrackedEntry>
    ): Boolean {
        val generatedAt = summary.generatedAt
            ?: return true // No generation timestamp, consider outdated

        // Filter to completed entries only (exclude daily summary entries)
        val completedEntries = entries
            .filter { it.entryType != EntryType.DAILY_SUMMARY }
            .filter { it.processingStatus == ProcessingStatus.COMPLETED }

        // Check if any entry was completed after the summary was generated
        return completedEntries.any { entry ->
            entry.capturedAt > generatedAt
        }
    }

    /**
     * Gets the latest entry timestamp for a given date.
     *
     * @param date The date to check
     * @param timeZone The timezone to use
     * @return The latest entry timestamp, or null if no entries exist
     */
    suspend fun getLatestEntryTimestamp(
        date: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Instant? {
        val (startMillis, endMillis) = DateTimeUtil.getDayBounds(date, timeZone)
        val entries = trackedEntryRepository.getEntriesForDay(startMillis, endMillis)

        return entries
            .filter { it.entryType != EntryType.DAILY_SUMMARY }
            .filter { it.processingStatus == ProcessingStatus.COMPLETED }
            .maxByOrNull { it.capturedAt }
            ?.capturedAt
    }
}
