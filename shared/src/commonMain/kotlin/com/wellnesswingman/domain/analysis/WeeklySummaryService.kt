package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.WeeklySummary
import com.wellnesswingman.data.model.WeeklySummaryPayload
import com.wellnesswingman.data.model.WeeklySummaryResult
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeeklySummaryRepository
import com.wellnesswingman.domain.llm.LlmClientFactory
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json

/**
 * Service for generating weekly summaries from tracked entries.
 */
class WeeklySummaryService(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val weeklySummaryRepository: WeeklySummaryRepository,
    private val dailySummaryRepository: DailySummaryRepository,
    private val llmClientFactory: LlmClientFactory
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Gets an existing summary for the week if available.
     */
    suspend fun getSummaryForWeek(weekStart: LocalDate): WeeklySummary? {
        return weeklySummaryRepository.getSummaryForWeek(weekStart)
    }

    /**
     * Generates a weekly summary for the specified week.
     */
    suspend fun generateSummary(
        weekStart: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): WeeklySummaryResult {
        try {
            Napier.d("Generating weekly summary for week starting $weekStart")

            // Check for existing summary - return cached if exists
            val existingSummary = weeklySummaryRepository.getSummaryForWeek(weekStart)
            if (existingSummary != null) {
                Napier.d("Returning cached weekly summary for $weekStart")
                val highlightsList = existingSummary.highlights.lines().filter { it.isNotBlank() }
                val recommendationsList = existingSummary.recommendations.lines().filter { it.isNotBlank() }
                return WeeklySummaryResult.Success(
                    summary = existingSummary,
                    highlightsList = highlightsList,
                    recommendationsList = recommendationsList
                )
            }

            // Check if we have an API key
            if (!llmClientFactory.hasCurrentApiKey()) {
                Napier.w("No API key configured; skipping weekly summary")
                return WeeklySummaryResult.Error("No API key configured")
            }

            // Calculate week bounds
            val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
            val startInstant = weekStart.atStartOfDayIn(timeZone)
            val endInstant = weekEnd.atTime(23, 59, 59).toInstant(timeZone)

            // Get entries for the week
            val entries = trackedEntryRepository.getEntriesForDay(
                startInstant.toEpochMilliseconds(),
                endInstant.toEpochMilliseconds()
            )

            // Filter to completed entries only (exclude daily summary entries)
            val completedEntries = entries
                .filter { it.entryType != EntryType.DAILY_SUMMARY }
                .filter { it.processingStatus == ProcessingStatus.COMPLETED }
                .sortedBy { it.capturedAt }

            if (completedEntries.isEmpty()) {
                Napier.i("No completed entries found for week starting $weekStart")
                return WeeklySummaryResult.NoEntries
            }

            // Count entries by type
            val mealCount = completedEntries.count { it.entryType == EntryType.MEAL }
            val exerciseCount = completedEntries.count { it.entryType == EntryType.EXERCISE }
            val sleepCount = completedEntries.count { it.entryType == EntryType.SLEEP }
            val otherCount = completedEntries.count {
                it.entryType != EntryType.MEAL &&
                it.entryType != EntryType.EXERCISE &&
                it.entryType != EntryType.SLEEP &&
                it.entryType != EntryType.DAILY_SUMMARY
            }
            val totalEntries = completedEntries.size

            // Get daily summaries for context
            val dailySummaries = dailySummaryRepository.getSummariesForDateRange(weekStart, weekEnd)

            // Build prompt for LLM
            val prompt = buildWeeklySummaryPrompt(
                weekStart = weekStart,
                weekEnd = weekEnd,
                mealCount = mealCount,
                exerciseCount = exerciseCount,
                sleepCount = sleepCount,
                otherCount = otherCount,
                totalEntries = totalEntries,
                dailySummaries = dailySummaries
            )

            // Generate summary using LLM
            val llmClient = llmClientFactory.createForCurrentProvider()
            val result = llmClient.generateCompletion(prompt)

            // Parse the result
            val (highlights, recommendations) = parseHighlightsAndRecommendations(result.content)

            // Save the summary with generation timestamp
            val summary = WeeklySummary(
                weekStartDate = weekStart,
                highlights = highlights.joinToString("\n"),
                recommendations = recommendations.joinToString("\n"),
                mealCount = mealCount,
                exerciseCount = exerciseCount,
                sleepCount = sleepCount,
                otherCount = otherCount,
                totalEntries = totalEntries,
                generatedAt = Clock.System.now()
            )

            val summaryId = weeklySummaryRepository.insertSummary(summary)

            Napier.i("Successfully generated weekly summary for week starting $weekStart")

            return WeeklySummaryResult.Success(
                summary = summary.copy(summaryId = summaryId),
                highlightsList = highlights,
                recommendationsList = recommendations
            )

        } catch (e: Exception) {
            Napier.e("Failed to generate weekly summary for week starting $weekStart", e)
            return WeeklySummaryResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Regenerates a weekly summary for a specific week.
     */
    suspend fun regenerateSummary(weekStart: LocalDate): WeeklySummaryResult {
        // Delete existing summary if present
        weeklySummaryRepository.deleteSummaryByWeek(weekStart)

        // Generate new summary
        return generateSummary(weekStart)
    }

    /**
     * Builds a prompt for weekly summary generation.
     */
    private fun buildWeeklySummaryPrompt(
        weekStart: LocalDate,
        weekEnd: LocalDate,
        mealCount: Int,
        exerciseCount: Int,
        sleepCount: Int,
        otherCount: Int,
        totalEntries: Int,
        dailySummaries: List<DailySummary>
    ): String {
        val dailySummaryContext = if (dailySummaries.isNotEmpty()) {
            val summaryText = dailySummaries.mapNotNull { summary ->
                val highlights = summary.highlights.takeIf { it.isNotBlank() }
                if (highlights != null) {
                    "${summary.summaryDate}: $highlights"
                } else null
            }.joinToString("\n")

            if (summaryText.isNotBlank()) {
                "\nDaily Summary Highlights:\n$summaryText"
            } else ""
        } else ""

        return """
Generate a weekly health summary for the week of $weekStart to $weekEnd. Return a JSON object with the following structure:

{
  "schemaVersion": "1.0",
  "weekStartDate": "$weekStart",
  "highlights": [
    "highlight 1",
    "highlight 2",
    "highlight 3"
  ],
  "recommendations": [
    "recommendation 1",
    "recommendation 2"
  ],
  "mealCount": $mealCount,
  "exerciseCount": $exerciseCount,
  "sleepCount": $sleepCount,
  "otherCount": $otherCount,
  "totalEntries": $totalEntries
}

Weekly Activity Overview:
- Total entries logged: $totalEntries
- Meals logged: $mealCount
- Exercise sessions: $exerciseCount
- Sleep entries: $sleepCount
- Other entries: $otherCount
$dailySummaryContext

Guidelines:
1. Provide 2-4 key highlights summarizing the week's health activities and patterns
2. Provide 2-3 specific, actionable recommendations for the upcoming week
3. Keep the tone positive and encouraging
4. Focus on weekly patterns, consistency, and achievable goals
5. If there are daily summaries, use them to identify trends across the week

Return ONLY the JSON object.
        """.trimIndent()
    }

    /**
     * Parses highlights and recommendations from LLM response.
     */
    private fun parseHighlightsAndRecommendations(content: String): Pair<List<String>, List<String>> {
        try {
            // Try to parse as JSON first
            val summaryJson = json.decodeFromString<WeeklySummaryPayload>(content)
            return summaryJson.highlights to summaryJson.recommendations
        } catch (e: Exception) {
            // Fall back to text parsing
            val lines = content.lines()
            val highlights = mutableListOf<String>()
            val recommendations = mutableListOf<String>()

            var inHighlights = false
            var inRecommendations = false

            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.contains("highlight", ignoreCase = true) ||
                    trimmed.contains("insight", ignoreCase = true) -> {
                        inHighlights = true
                        inRecommendations = false
                    }
                    trimmed.contains("recommendation", ignoreCase = true) -> {
                        inHighlights = false
                        inRecommendations = true
                    }
                    trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.matches(Regex("^\\d+\\..*")) -> {
                        val text = trimmed.removePrefix("-").removePrefix("•").replaceFirst(Regex("^\\d+\\.\\s*"), "").trim()
                        if (text.isNotBlank()) {
                            if (inRecommendations) recommendations.add(text)
                            else highlights.add(text)
                        }
                    }
                }
            }

            if (highlights.isEmpty()) highlights.add(content.take(200))
            if (recommendations.isEmpty()) recommendations.add("Keep tracking your health activities!")

            return highlights to recommendations
        }
    }
}
