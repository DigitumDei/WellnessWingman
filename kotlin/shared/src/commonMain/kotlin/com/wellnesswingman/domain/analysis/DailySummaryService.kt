package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.DailySummaryResult
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.NutritionTotals
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.analysis.MealAnalysisResult
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.util.DateTimeUtil
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json

/**
 * Service for generating daily summaries from tracked entries.
 */
class DailySummaryService(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val entryAnalysisRepository: EntryAnalysisRepository,
    private val dailySummaryRepository: DailySummaryRepository,
    private val llmClientFactory: LlmClientFactory,
    private val dailyTotalsCalculator: DailyTotalsCalculator
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Generates a daily summary for the specified date.
     */
    suspend fun generateSummary(
        date: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): DailySummaryResult {
        try {
            Napier.d("Generating daily summary for $date")

            // Check if we have an API key
            if (!llmClientFactory.hasCurrentApiKey()) {
                Napier.w("No API key configured; skipping daily summary")
                return DailySummaryResult.Error("No API key configured")
            }

            // Get entries for the day
            val (startMillis, endMillis) = DateTimeUtil.getDayBounds(date, timeZone)
            val entries = trackedEntryRepository.getEntriesForDay(startMillis, endMillis)

            // Filter to completed entries only (exclude daily summary entries)
            val completedEntries = entries
                .filter { it.entryType != EntryType.DAILY_SUMMARY }
                .filter { it.processingStatus == ProcessingStatus.COMPLETED }
                .sortedBy { it.capturedAt }

            if (completedEntries.isEmpty()) {
                Napier.i("No completed entries found for $date")
                return DailySummaryResult.NoEntries
            }

            // Get analyses for the entries
            val analyses = completedEntries.mapNotNull { entry ->
                val analysis = entryAnalysisRepository.getLatestAnalysisForEntry(entry.entryId)
                if (analysis != null) {
                    try {
                        // Try to parse as MealAnalysisResult for nutrition calculation
                        if (entry.entryType == EntryType.MEAL) {
                            json.decodeFromString<MealAnalysisResult>(analysis.insightsJson)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Napier.w("Failed to parse analysis for entry ${entry.entryId}: ${e.message}")
                        null
                    }
                } else {
                    null
                }
            }

            // Calculate nutrition totals
            val nutritionTotals = dailyTotalsCalculator.calculate(analyses)

            // Count entries by type
            val mealCount = completedEntries.count { it.entryType == EntryType.MEAL }
            val exerciseCount = completedEntries.count { it.entryType == EntryType.EXERCISE }
            val sleepEntries = completedEntries.filter { it.entryType == EntryType.SLEEP }

            // Build prompt for LLM
            val prompt = buildSummaryPrompt(
                date = date,
                entries = completedEntries,
                nutritionTotals = nutritionTotals,
                mealCount = mealCount,
                exerciseCount = exerciseCount,
                sleepCount = sleepEntries.size
            )

            // Generate summary using LLM
            val llmClient = llmClientFactory.createForCurrentProvider()
            val result = llmClient.generateCompletion(prompt)

            // Parse the result
            // For now, just use the raw content as highlights
            val highlights = result.content
            val recommendations = extractRecommendations(result.content)

            // Save the summary
            val summary = DailySummary(
                summaryDate = date,
                highlights = highlights,
                recommendations = recommendations
            )

            val summaryId = dailySummaryRepository.insertSummary(summary)

            Napier.i("Successfully generated daily summary for $date")

            return DailySummaryResult.Success(
                summary = summary.copy(summaryId = summaryId),
                payload = com.wellnesswingman.data.model.DailySummaryPayload(
                    date = date.toString(),
                    summary = highlights,
                    highlights = listOf(highlights),
                    recommendations = listOf(recommendations),
                    nutritionTotals = nutritionTotals,
                    mealCount = mealCount,
                    exerciseCount = exerciseCount,
                    sleepHours = null // TODO: Calculate from sleep entries
                )
            )

        } catch (e: Exception) {
            Napier.e("Failed to generate daily summary for $date", e)
            return DailySummaryResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Regenerates a daily summary for a specific date.
     */
    suspend fun regenerateSummary(date: LocalDate): DailySummaryResult {
        // Delete existing summary if present
        dailySummaryRepository.deleteSummaryByDate(date)

        // Generate new summary
        return generateSummary(date)
    }

    /**
     * Builds a prompt for daily summary generation.
     */
    private fun buildSummaryPrompt(
        date: LocalDate,
        entries: List<TrackedEntry>,
        nutritionTotals: NutritionTotals,
        mealCount: Int,
        exerciseCount: Int,
        sleepCount: Int
    ): String {
        return """
            Generate a comprehensive daily health summary for ${date}.

            Activity Overview:
            - Total entries logged: ${entries.size}
            - Meals logged: $mealCount
            - Exercise sessions: $exerciseCount
            - Sleep entries: $sleepCount

            Nutrition Summary:
            - Total calories: ${nutritionTotals.calories.toInt()} kcal
            - Protein: ${nutritionTotals.protein.toInt()}g
            - Carbohydrates: ${nutritionTotals.carbs.toInt()}g
            - Fat: ${nutritionTotals.fat.toInt()}g
            - Fiber: ${nutritionTotals.fiber.toInt()}g

            Please provide:
            1. A brief summary (2-3 sentences) of the day's health activities
            2. Key highlights (positive aspects)
            3. Areas for improvement
            4. Specific recommendations for tomorrow

            Keep the tone positive and encouraging. Focus on progress and achievable goals.
        """.trimIndent()
    }

    /**
     * Extracts recommendations from LLM response.
     */
    private fun extractRecommendations(content: String): String {
        // Simple extraction - look for recommendation section
        val lines = content.lines()
        val recommendationIndex = lines.indexOfFirst {
            it.contains("recommendation", ignoreCase = true)
        }

        if (recommendationIndex >= 0 && recommendationIndex < lines.size - 1) {
            return lines.subList(recommendationIndex + 1, lines.size).joinToString("\n")
        }

        return "Continue your healthy habits!"
    }
}
