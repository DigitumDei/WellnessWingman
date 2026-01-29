package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.llm.LlmClientFactory
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock

/**
 * Result of an analysis invocation.
 */
sealed class AnalysisInvocationResult {
    data class Success(val analysis: EntryAnalysis) : AnalysisInvocationResult()
    data class Error(val message: String) : AnalysisInvocationResult()
    data class Skipped(val reason: String) : AnalysisInvocationResult()

    companion object {
        fun error(message: String = "Analysis failed") = Error(message)
        fun skipped(reason: String) = Skipped(reason)
    }
}

/**
 * Orchestrates the analysis of tracked entries using LLM clients.
 */
class AnalysisOrchestrator(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val entryAnalysisRepository: EntryAnalysisRepository,
    private val llmClientFactory: LlmClientFactory
) {

    /**
     * Processes a tracked entry and generates its analysis.
     */
    suspend fun processEntry(
        entry: TrackedEntry,
        userProvidedDetails: String? = null
    ): AnalysisInvocationResult {
        try {
            // Update status to processing
            trackedEntryRepository.updateEntryStatus(entry.entryId, ProcessingStatus.PROCESSING)

            // Check if we have an API key configured
            if (!llmClientFactory.hasCurrentApiKey()) {
                Napier.w("No API key configured; skipping analysis for entry ${entry.entryId}")
                trackedEntryRepository.updateEntryStatus(entry.entryId, ProcessingStatus.SKIPPED)
                return AnalysisInvocationResult.skipped("No API key configured")
            }

            // Get the LLM client
            val llmClient = llmClientFactory.createForCurrentProvider()

            // Build the prompt based on entry type
            val prompt = buildPrompt(entry, userProvidedDetails)

            // Analyze the entry
            val result = if (entry.blobPath != null) {
                // TODO: Load image bytes from blob path
                // For now, just use text-only analysis
                llmClient.generateCompletion(prompt, getJsonSchema(entry.entryType))
            } else {
                llmClient.generateCompletion(prompt, getJsonSchema(entry.entryType))
            }

            // Create and save the analysis
            val analysis = EntryAnalysis(
                entryId = entry.entryId,
                providerId = llmClientFactory.create(llmClientFactory.createForCurrentProvider().javaClass.simpleName
                    .let { if (it.contains("OpenAi")) "openai" else "gemini" }),
                model = result.diagnostics.model,
                capturedAt = Clock.System.now(),
                insightsJson = result.content,
                schemaVersion = "1.0"
            )

            val analysisId = entryAnalysisRepository.insertAnalysis(analysis)

            // Update entry status
            trackedEntryRepository.updateEntryStatus(entry.entryId, ProcessingStatus.COMPLETED)

            Napier.i("Successfully analyzed entry ${entry.entryId}")

            return AnalysisInvocationResult.Success(analysis.copy(analysisId = analysisId))

        } catch (e: Exception) {
            Napier.e("Analysis failed for entry ${entry.entryId}", e)
            trackedEntryRepository.updateEntryStatus(entry.entryId, ProcessingStatus.FAILED)
            return AnalysisInvocationResult.error(e.message ?: "Unknown error")
        }
    }

    /**
     * Builds an analysis prompt for the given entry.
     */
    private fun buildPrompt(entry: TrackedEntry, userProvidedDetails: String?): String {
        val basePrompt = when (entry.entryType) {
            EntryType.MEAL -> buildMealPrompt(userProvidedDetails)
            EntryType.EXERCISE -> buildExercisePrompt(userProvidedDetails)
            EntryType.SLEEP -> buildSleepPrompt(userProvidedDetails)
            else -> "Analyze this entry and provide insights."
        }

        // Include user notes if available
        val userNotes = entry.userNotes
        return if (userNotes != null && userNotes.isNotBlank()) {
            "$basePrompt\n\nUser notes: $userNotes"
        } else {
            basePrompt
        }
    }

    /**
     * Builds a prompt for meal analysis.
     */
    private fun buildMealPrompt(userProvidedDetails: String?): String {
        return """
            Analyze this meal photo and provide detailed nutritional information.

            Return your response as a JSON object with the following structure:
            {
              "schemaVersion": "1.0",
              "foodItems": [
                {
                  "name": "Food item name",
                  "portionSize": "Estimated portion",
                  "calories": <number>,
                  "confidence": <0.0-1.0>
                }
              ],
              "nutrition": {
                "totalCalories": <number>,
                "protein": <grams>,
                "carbohydrates": <grams>,
                "fat": <grams>,
                "fiber": <grams>,
                "sugar": <grams>,
                "sodium": <milligrams>
              },
              "healthInsights": {
                "healthScore": <0-10>,
                "summary": "Brief health summary",
                "positives": ["Positive aspect 1", "Positive aspect 2"],
                "improvements": ["Area for improvement"],
                "recommendations": ["Recommendation 1"]
              },
              "confidence": <0.0-1.0>,
              "warnings": []
            }

            ${if (userProvidedDetails != null) "Additional context: $userProvidedDetails" else ""}
        """.trimIndent()
    }

    /**
     * Builds a prompt for exercise analysis.
     */
    private fun buildExercisePrompt(userProvidedDetails: String?): String {
        return """
            Analyze this exercise activity and provide detailed metrics.

            Return your response as a JSON object following the exercise schema.
            ${if (userProvidedDetails != null) "Additional context: $userProvidedDetails" else ""}
        """.trimIndent()
    }

    /**
     * Builds a prompt for sleep analysis.
     */
    private fun buildSleepPrompt(userProvidedDetails: String?): String {
        return """
            Analyze this sleep data and provide insights.

            Return your response as a JSON object following the sleep schema.
            ${if (userProvidedDetails != null) "Additional context: $userProvidedDetails" else ""}
        """.trimIndent()
    }

    /**
     * Gets the JSON schema for the given entry type.
     */
    private fun getJsonSchema(entryType: EntryType): String? {
        // For now, return null to let the LLM use its default format
        // TODO: Implement proper JSON schemas for each type
        return null
    }
}
