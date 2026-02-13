package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.analysis.UnifiedAnalysisResult
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.FileSystem
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

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
    private val llmClientFactory: LlmClientFactory,
    private val fileSystem: FileSystem
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val validator = AnalysisValidator()

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

            // Build the prompt - use unified prompt for auto-detection
            val prompt = buildUnifiedPrompt(entry, userProvidedDetails)

            // Analyze the entry
            val result = if (entry.blobPath != null) {
                try {
                    if (fileSystem.exists(entry.blobPath)) {
                        val imageBytes = fileSystem.readBytes(entry.blobPath)
                        llmClient.analyzeImage(imageBytes, prompt, null)
                    } else {
                        Napier.w("Image file not found: ${entry.blobPath}")
                        llmClient.generateCompletion(prompt, null)
                    }
                } catch (e: Exception) {
                    Napier.e("Failed to load image bytes", e)
                    llmClient.generateCompletion(prompt, null)
                }
            } else {
                llmClient.generateCompletion(prompt, null)
            }

            // Validate and extract detected entry type
            val validationResult = validator.validateUnifiedAnalysis(result.content)
            validator.logValidation(entry.entryId, validationResult)

            // Try to detect and update entry type from analysis
            try {
                val unifiedResult = json.decodeFromString<UnifiedAnalysisResult>(result.content)
                val detectedType = normalizeEntryType(unifiedResult.entryType)
                if (detectedType != null && entry.entryType == EntryType.UNKNOWN) {
                    Napier.i("Updating entry ${entry.entryId} type from UNKNOWN to $detectedType")
                    trackedEntryRepository.updateEntryType(entry.entryId, detectedType)
                }
            } catch (e: Exception) {
                Napier.w("Failed to parse unified analysis for type detection: ${e.message}")
            }

            // Create and save the analysis
            val analysis = EntryAnalysis(
                entryId = entry.entryId,
                providerId = llmClient.javaClass.simpleName
                    .let { if (it.contains("OpenAi")) "openai" else "gemini" },
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
     * Builds a unified analysis prompt that can detect entry type automatically.
     */
    private fun buildUnifiedPrompt(entry: TrackedEntry, userProvidedDetails: String?): String {
        val userContext = buildString {
            if (entry.userNotes?.isNotBlank() == true) {
                append("User notes: ${entry.userNotes}\n\n")
            }
            if (userProvidedDetails?.isNotBlank() == true) {
                append("Additional context: $userProvidedDetails\n\n")
            }
        }

        return """
You are a health and wellness analysis assistant with vision capabilities. Analyze the provided content (image and/or text) and determine the type of health-related entry, then provide detailed analysis.

ENTRY TYPE DETECTION:
First, determine what type of entry this is:
- "Meal" - Food, beverages, nutrition-related content
- "Exercise" - Physical activity, workouts, sports, fitness tracking screenshots
- "Sleep" - Sleep tracking data, sleep-related information
- "Other" - Health documents, lab results, medical information, or anything else health-related

IMPORTANT: You MUST return a valid JSON object with the following structure. Only populate the analysis field that matches the detected entry type.

$userContext

REQUIRED JSON RESPONSE FORMAT:
{
  "schemaVersion": "1.0",
  "entryType": "<Meal|Exercise|Sleep|Other>",
  "confidence": <0.0-1.0>,
  "mealAnalysis": {
    "schemaVersion": "1.0",
    "foodItems": [
      {"name": "Food name", "portionSize": "size estimate", "calories": <number>, "confidence": <0.0-1.0>}
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
      "positives": ["positive aspect"],
      "improvements": ["improvement area"],
      "recommendations": ["recommendation"]
    },
    "confidence": <0.0-1.0>,
    "warnings": []
  },
  "exerciseAnalysis": {
    "schemaVersion": "1.0",
    "activityType": "Activity name",
    "metrics": {
      "distance": <number or null>,
      "distanceUnit": "km or miles",
      "durationMinutes": <number>,
      "calories": <number>,
      "averageHeartRate": <number or null>,
      "steps": <number or null>
    },
    "insights": {
      "summary": "Brief summary",
      "positives": ["positive aspect"],
      "improvements": ["improvement area"],
      "recommendations": ["recommendation"]
    },
    "warnings": []
  },
  "sleepAnalysis": {
    "durationHours": <number>,
    "sleepScore": <0-100>,
    "qualitySummary": "Quality description",
    "environmentNotes": ["note"],
    "recommendations": ["recommendation"]
  },
  "otherAnalysis": {
    "summary": "Description of what this entry contains",
    "tags": ["relevant", "tags"],
    "recommendations": ["recommendation"]
  },
  "warnings": []
}

GUIDELINES:
1. Set the appropriate analysis field based on detected entry type; set others to null
2. Provide confidence scores (0.0-1.0) based on image clarity and certainty
3. For meals: Identify all visible food items and estimate portions carefully
4. For exercise: Extract metrics from fitness tracker screenshots or estimate from images
5. For sleep: Extract data from sleep tracker screenshots or provide estimates
6. Add warnings array if there are any issues with the analysis
7. Health score (0-10): 10 = excellent, 7-9 = good, 5-6 = moderate, below 5 = needs improvement

ONLY return the JSON object, no other text.
        """.trimIndent()
    }

    /**
     * Normalizes entry type string to EntryType enum.
     */
    private fun normalizeEntryType(value: String?): EntryType? {
        if (value.isNullOrBlank()) return null

        return when (value.lowercase().trim()) {
            "meal", "food" -> EntryType.MEAL
            "exercise", "workout", "activity" -> EntryType.EXERCISE
            "sleep" -> EntryType.SLEEP
            "other" -> EntryType.OTHER
            else -> null
        }
    }
}
