package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.DailySummaryEntryReference
import com.wellnesswingman.data.model.DailySummaryPayload
import com.wellnesswingman.data.model.DailySummaryResult
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.NutritionalBalance
import com.wellnesswingman.data.model.NutritionTotals
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.analysis.ExerciseAnalysisResult
import com.wellnesswingman.data.model.analysis.MealAnalysisResult
import com.wellnesswingman.data.model.analysis.OtherAnalysisResult
import com.wellnesswingman.data.model.analysis.SleepAnalysisResult
import com.wellnesswingman.data.model.analysis.UnifiedAnalysisResult
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.util.DateTimeUtil
import com.wellnesswingman.data.model.WeightRecord
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
    private val dailyTotalsCalculator: DailyTotalsCalculator,
    private val weightHistoryRepository: WeightHistoryRepository
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
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        userComments: String? = null
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

            // Get analyses for the entries and build entry references + detail lines
            val mealAnalyses = mutableListOf<MealAnalysisResult>()
            val entryReferences = mutableListOf<DailySummaryEntryReference>()
            val entryDetailLines = mutableListOf<String>()
            var totalSleepHours: Double? = null

            for (entry in completedEntries) {
                val analysis = entryAnalysisRepository.getLatestAnalysisForEntry(entry.entryId)
                var entrySummary: String? = null

                if (analysis != null) {
                    try {
                        // Try to parse as unified result first
                        val unifiedResult = json.decodeFromString<UnifiedAnalysisResult>(analysis.insightsJson)

                        when (entry.entryType) {
                            EntryType.MEAL -> {
                                unifiedResult.mealAnalysis?.let { meal ->
                                    mealAnalyses.add(meal)
                                    entrySummary = meal.healthInsights?.summary
                                    entryDetailLines.add(buildMealDetailLine(entry, meal))
                                }
                            }
                            EntryType.SLEEP -> {
                                unifiedResult.sleepAnalysis?.let { sleep ->
                                    totalSleepHours = (totalSleepHours ?: 0.0) + (sleep.durationHours ?: 0.0)
                                    entrySummary = sleep.qualitySummary
                                    entryDetailLines.add(buildSleepDetailLine(entry, sleep))
                                }
                            }
                            EntryType.EXERCISE -> {
                                unifiedResult.exerciseAnalysis?.let { exercise ->
                                    entrySummary = exercise.insights?.summary
                                    entryDetailLines.add(buildExerciseDetailLine(entry, exercise))
                                }
                            }
                            else -> {
                                unifiedResult.otherAnalysis?.let { other ->
                                    entrySummary = other.summary
                                    entryDetailLines.add(buildOtherDetailLine(entry, other))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Try legacy meal-only format
                        try {
                            if (entry.entryType == EntryType.MEAL) {
                                val mealResult = json.decodeFromString<MealAnalysisResult>(analysis.insightsJson)
                                mealAnalyses.add(mealResult)
                                entrySummary = mealResult.healthInsights?.summary
                                entryDetailLines.add(buildMealDetailLine(entry, mealResult))
                            }
                        } catch (e2: Exception) {
                            Napier.w("Failed to parse analysis for entry ${entry.entryId}: ${e.message}")
                        }
                    }
                }

                entryReferences.add(
                    DailySummaryEntryReference(
                        entryId = entry.entryId,
                        entryType = entry.entryType.toStorageString(),
                        capturedAt = entry.capturedAt,
                        summary = entrySummary
                    )
                )
            }

            // Calculate nutrition totals
            val nutritionTotals = dailyTotalsCalculator.calculate(mealAnalyses)

            // Count entries by type
            val mealCount = completedEntries.count { it.entryType == EntryType.MEAL }
            val exerciseCount = completedEntries.count { it.entryType == EntryType.EXERCISE }
            val sleepCount = completedEntries.count { it.entryType == EntryType.SLEEP }

            // Calculate balance metrics
            val balance = calculateBalance(nutritionTotals, mealCount, completedEntries)

            // Get weight records for the day
            val weightRecords = try {
                weightHistoryRepository.getWeightHistory(
                    Instant.fromEpochMilliseconds(startMillis),
                    Instant.fromEpochMilliseconds(endMillis)
                )
            } catch (e: Exception) {
                Napier.w("Failed to load weight records for summary: ${e.message}")
                emptyList()
            }

            // Build prompt for LLM
            val prompt = buildSummaryPrompt(
                date = date,
                entries = completedEntries,
                nutritionTotals = nutritionTotals,
                balance = balance,
                mealCount = mealCount,
                exerciseCount = exerciseCount,
                sleepCount = sleepCount,
                sleepHours = totalSleepHours,
                weightRecords = weightRecords,
                userComments = userComments,
                entryDetailLines = entryDetailLines
            )

            // Generate summary using LLM
            val llmClient = llmClientFactory.createForCurrentProvider()
            val result = llmClient.generateCompletion(prompt)

            // Parse the result
            val (highlights, recommendations) = parseHighlightsAndRecommendations(result.content)

            // Build payload
            val payload = DailySummaryPayload(
                date = date.toString(),
                summary = highlights.firstOrNull() ?: "Summary generated",
                highlights = highlights,
                recommendations = recommendations,
                nutritionTotals = nutritionTotals,
                balance = balance,
                entriesIncluded = entryReferences,
                mealCount = mealCount,
                exerciseCount = exerciseCount,
                sleepHours = totalSleepHours
            )

            val payloadJson = try {
                json.encodeToString(DailySummaryPayload.serializer(), payload)
            } catch (e: Exception) {
                Napier.w("Failed to serialize daily payload: ${e.message}")
                null
            }

            // Save the summary with generation timestamp
            val summary = DailySummary(
                summaryDate = date,
                highlights = highlights.joinToString("\n"),
                recommendations = recommendations.joinToString("\n"),
                generatedAt = Clock.System.now(),
                payloadJson = payloadJson
            )

            val summaryId = dailySummaryRepository.insertSummary(summary)

            Napier.i("Successfully generated daily summary for $date")

            return DailySummaryResult.Success(
                summary = summary.copy(summaryId = summaryId),
                payload = payload
            )

        } catch (e: Exception) {
            Napier.e("Failed to generate daily summary for $date", e)
            return DailySummaryResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Regenerates a daily summary for a specific date.
     */
    suspend fun regenerateSummary(date: LocalDate, userComments: String? = null): DailySummaryResult {
        // Delete existing summary if present
        dailySummaryRepository.deleteSummaryByDate(date)

        // Generate new summary
        return generateSummary(date, userComments = userComments)
    }

    /**
     * Calculates nutritional balance metrics.
     */
    private fun calculateBalance(
        totals: NutritionTotals,
        mealCount: Int,
        entries: List<TrackedEntry>
    ): NutritionalBalance {
        // Calculate macro percentages
        val totalMacroCalories = (totals.protein * 4) + (totals.carbs * 4) + (totals.fat * 9)
        val macroBalance = if (totalMacroCalories > 0) {
            val carbPercent = ((totals.carbs * 4 / totalMacroCalories) * 100).toInt()
            val proteinPercent = ((totals.protein * 4 / totalMacroCalories) * 100).toInt()
            val fatPercent = ((totals.fat * 9 / totalMacroCalories) * 100).toInt()
            "${carbPercent}C/${proteinPercent}P/${fatPercent}F"
        } else null

        // Assess overall balance
        val overall = when {
            totals.calories < 1200 -> "Low calorie intake"
            totals.calories > 3000 -> "High calorie intake"
            totals.protein < 50 -> "Low protein"
            totals.fiber < 20 -> "Low fiber"
            else -> "Balanced"
        }

        // Assess meal timing
        val mealEntries = entries.filter { it.entryType == EntryType.MEAL }.sortedBy { it.capturedAt }
        val timing = when {
            mealCount == 0 -> "No meals logged"
            mealCount == 1 -> "Only one meal logged"
            mealCount >= 3 -> "Well-distributed meals"
            else -> "Consider more consistent meal timing"
        }

        // Assess variety (basic - based on meal count)
        val variety = when {
            mealCount == 0 -> null
            mealCount >= 3 -> "Good meal variety"
            else -> "Consider adding more variety"
        }

        return NutritionalBalance(
            overall = overall,
            macroBalance = macroBalance,
            timing = timing,
            variety = variety
        )
    }

    private fun buildMealDetailLine(entry: TrackedEntry, meal: MealAnalysisResult): String {
        val sb = StringBuilder("  - Meal")
        if (meal.foodItems.isNotEmpty()) {
            sb.append(": ${meal.foodItems.joinToString(", ") { it.name }}")
        }
        meal.nutrition?.let { n ->
            val parts = listOfNotNull(
                n.totalCalories?.let { "${it.toInt()} kcal" },
                n.protein?.let { "${it.toInt()}g protein" },
                n.carbohydrates?.let { "${it.toInt()}g carbs" },
                n.fat?.let { "${it.toInt()}g fat" }
            )
            if (parts.isNotEmpty()) sb.append(" [${parts.joinToString(", ")}]")
        }
        meal.healthInsights?.summary?.let { sb.append("\n    Health: $it") }
        entry.userNotes?.takeIf { it.isNotBlank() }?.let { sb.append("\n    User note: $it") }
        return sb.toString()
    }

    private fun buildExerciseDetailLine(entry: TrackedEntry, exercise: ExerciseAnalysisResult): String {
        val sb = StringBuilder("  - Exercise")
        exercise.activityType?.let { sb.append(": $it") }
        val metrics = exercise.metrics
        val parts = listOfNotNull(
            metrics.durationMinutes?.let { "${it.toInt()} min" },
            metrics.distance?.let { d -> metrics.distanceUnit?.let { "$d $it" } ?: "$d" },
            metrics.calories?.let { "${it.toInt()} kcal burned" },
            metrics.averageHeartRate?.let { "${it.toInt()} bpm avg HR" }
        )
        if (parts.isNotEmpty()) sb.append(" [${parts.joinToString(", ")}]")
        exercise.insights?.summary?.let { sb.append("\n    Insights: $it") }
        entry.userNotes?.takeIf { it.isNotBlank() }?.let { sb.append("\n    User note: $it") }
        return sb.toString()
    }

    private fun buildSleepDetailLine(entry: TrackedEntry, sleep: SleepAnalysisResult): String {
        val sb = StringBuilder("  - Sleep")
        sleep.durationHours?.let { sb.append(": ${it.toInt()}h") }
        sleep.qualitySummary?.let { sb.append("\n    Quality: $it") }
        if (sleep.environmentNotes.isNotEmpty()) {
            sb.append("\n    Environment: ${sleep.environmentNotes.joinToString("; ")}")
        }
        entry.userNotes?.takeIf { it.isNotBlank() }?.let { sb.append("\n    User note: $it") }
        return sb.toString()
    }

    private fun buildOtherDetailLine(entry: TrackedEntry, other: OtherAnalysisResult): String {
        val sb = StringBuilder("  - Other")
        other.summary?.let { sb.append(": $it") }
        if (other.tags.isNotEmpty()) sb.append(" [${other.tags.joinToString(", ")}]")
        entry.userNotes?.takeIf { it.isNotBlank() }?.let { sb.append("\n    User note: $it") }
        return sb.toString()
    }

    /**
     * Builds a prompt for daily summary generation.
     */
    private fun buildSummaryPrompt(
        date: LocalDate,
        entries: List<TrackedEntry>,
        nutritionTotals: NutritionTotals,
        balance: NutritionalBalance,
        mealCount: Int,
        exerciseCount: Int,
        sleepCount: Int,
        sleepHours: Double?,
        weightRecords: List<WeightRecord> = emptyList(),
        userComments: String? = null,
        entryDetailLines: List<String> = emptyList()
    ): String {
        val detailedEntryLog = if (entryDetailLines.isNotEmpty()) {
            "\nDetailed Entry Log:\n${entryDetailLines.joinToString("\n")}"
        } else ""

        return """
Generate a daily health summary for $date. Return a JSON object with the following structure:

{
  "schemaVersion": "1.0",
  "totals": {
    "calories": ${nutritionTotals.calories.toInt()},
    "protein": ${nutritionTotals.protein.toInt()},
    "carbohydrates": ${nutritionTotals.carbs.toInt()},
    "fat": ${nutritionTotals.fat.toInt()},
    "fiber": ${nutritionTotals.fiber.toInt()},
    "sugar": ${nutritionTotals.sugar.toInt()},
    "sodium": ${nutritionTotals.sodium.toInt()}
  },
  "balance": {
    "overall": "${balance.overall ?: "Balanced"}",
    "macroBalance": "${balance.macroBalance ?: "N/A"}",
    "timing": "${balance.timing ?: "N/A"}",
    "variety": "${balance.variety ?: "N/A"}"
  },
  "insights": [
    "insight 1",
    "insight 2",
    "insight 3"
  ],
  "recommendations": [
    "recommendation 1",
    "recommendation 2"
  ]
}

Activity Overview:
- Total entries logged: ${entries.size}
- Meals logged: $mealCount
- Exercise sessions: $exerciseCount
- Sleep entries: $sleepCount${sleepHours?.let { "\n- Total sleep: ${it.toInt()} hours" } ?: ""}${
    if (weightRecords.isNotEmpty()) {
        "\n\nWeight Recorded:\n" + weightRecords.joinToString("\n") { record ->
            "- ${record.weightValue} ${record.weightUnit} (${record.source})"
        }
    } else ""
}

Nutrition Summary:
- Total calories: ${nutritionTotals.calories.toInt()} kcal
- Protein: ${nutritionTotals.protein.toInt()}g
- Carbohydrates: ${nutritionTotals.carbs.toInt()}g
- Fat: ${nutritionTotals.fat.toInt()}g
- Fiber: ${nutritionTotals.fiber.toInt()}g
$detailedEntryLog

${if (!userComments.isNullOrBlank()) "User's note about their day:\n$userComments\n\n" else ""}Guidelines:
1. Provide 2-4 key insights about the day's nutrition and activities
2. Provide 2-3 specific, actionable recommendations for tomorrow
3. Keep the tone positive and encouraging
4. Focus on progress and achievable goals
5. Incorporate individual entry notes from the user where relevant${if (!userComments.isNullOrBlank()) "\n6. Incorporate the user's note into your insights and recommendations where relevant" else ""}

Return ONLY the JSON object.
        """.trimIndent()
    }

    /**
     * Parses highlights and recommendations from LLM response.
     */
    private fun parseHighlightsAndRecommendations(content: String): Pair<List<String>, List<String>> {
        val cleanedContent = extractJsonObject(content) ?: content
        try {
            // Try to parse as JSON first
            val summaryJson = json.decodeFromString<DailySummaryPayload>(cleanedContent)
            return summaryJson.highlights to summaryJson.recommendations
        } catch (e: Exception) {
            try {
                val altJson = json.decodeFromString<DailySummaryPayloadAlt>(cleanedContent)
                return altJson.insights to altJson.recommendations
            } catch (e2: Exception) {
                // Fall back to text parsing
            }
            // Fall back to text parsing
            val lines = content.lines()
            val highlights = mutableListOf<String>()
            val recommendations = mutableListOf<String>()

            var inInsights = false
            var inRecommendations = false

            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.contains("insight", ignoreCase = true) -> {
                        inInsights = true
                        inRecommendations = false
                    }
                    trimmed.contains("recommendation", ignoreCase = true) -> {
                        inInsights = false
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
            if (recommendations.isEmpty()) recommendations.add("Continue your healthy habits!")

            return highlights to recommendations
        }
    }

    private fun extractJsonObject(content: String): String? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return content.substring(start, end + 1).trim()
    }

    @kotlinx.serialization.Serializable
    private data class DailySummaryPayloadAlt(
        @kotlinx.serialization.SerialName("insights")
        val insights: List<String> = emptyList(),
        @kotlinx.serialization.SerialName("recommendations")
        val recommendations: List<String> = emptyList()
    )
}
