package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.domain.llm.LlmAnalysisResult
import com.wellnesswingman.domain.llm.LlmClient
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.LlmDiagnostics
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutritionLabelAnalyzerTest {

    private val llmClientFactory = mockk<LlmClientFactory>()
    private val llmClient = mockk<LlmClient>()

    @Test
    fun `has configured api key delegates to client factory`() {
        every { llmClientFactory.hasCurrentApiKey() } returns false
        val analyzer: NutritionLabelAnalyzing = NutritionLabelAnalyzer(llmClientFactory)

        assertFalse(analyzer.hasConfiguredApiKey())
    }

    @Test
    fun `analyze label image fails with clear message when api key is missing`() = runTest {
        every { llmClientFactory.hasCurrentApiKey() } returns false
        val analyzer = NutritionLabelAnalyzer(llmClientFactory)

        val error = assertFailsWith<IllegalStateException> {
            analyzer.analyzeLabelImage(byteArrayOf(1, 2, 3), "/tmp/label.jpg")
        }

        assertContains(error.message.orEmpty(), "Missing API Key")
        assertContains(error.message.orEmpty(), "Go to Settings")
    }

    @Test
    fun `analyze label image builds prompt parses json and preserves source path`() = runTest {
        val imageBytes = byteArrayOf(9, 8, 7)
        var capturedPrompt: String? = null
        var capturedSchema: String? = "sentinel"

        every { llmClientFactory.hasCurrentApiKey() } returns true
        every { llmClientFactory.createForCurrentProvider() } returns llmClient
        coEvery {
            llmClient.analyzeImage(
                imageBytes = imageBytes,
                prompt = any(),
                jsonSchema = any(),
                tools = any(),
                toolExecutor = any()
            )
        } answers {
            capturedPrompt = secondArg()
            capturedSchema = thirdArg()
            LlmAnalysisResult(
                content = """
                {
                  "primaryName": "Fairlife Core Power",
                  "servingSize": "1 bottle",
                  "nutrition": {
                    "totalCalories": 230,
                    "protein": 42,
                    "sodium": 240
                  },
                  "confidence": 0.97,
                  "warnings": ["glare on sodium"]
                }
                """.trimIndent(),
                diagnostics = LlmDiagnostics(totalTokens = 123)
            )
        }

        val extraction = NutritionLabelAnalyzer(llmClientFactory).analyzeLabelImage(
            imageBytes = imageBytes,
            sourceImagePath = "/tmp/label.jpg"
        )

        assertEquals("Fairlife Core Power", extraction.primaryName)
        assertEquals("1 bottle", extraction.servingSize)
        assertEquals(230.0, extraction.nutrition.totalCalories)
        assertEquals(42.0, extraction.nutrition.protein)
        assertEquals(240.0, extraction.nutrition.sodium)
        assertEquals(0.97, extraction.confidence)
        assertEquals(listOf("glare on sodium"), extraction.warnings)
        assertContains(extraction.rawJson.orEmpty(), "\"primaryName\": \"Fairlife Core Power\"")
        assertEquals("/tmp/label.jpg", extraction.sourceImagePath)
        assertNotNull(capturedPrompt)
        assertContains(capturedPrompt.orEmpty(), "\"primaryName\": \"string or null\"")
        assertContains(capturedPrompt.orEmpty(), "\"cholesterol\": number or null")
        assertContains(capturedPrompt.orEmpty(), "Return only valid JSON")
        assertNull(capturedSchema)
    }

    @Test
    fun `to profile normalizes names aliases and preserves raw json when available`() {
        val analyzer = NutritionLabelAnalyzer(llmClientFactory)
        val extraction = NutritionLabelExtraction(
            primaryName = "Returned Name",
            servingSize = "1 bar",
            nutrition = ExtractedNutrition(
                totalCalories = 190.0,
                protein = 21.0,
                carbohydrates = 22.0,
                fat = 7.0,
                fiber = 14.0,
                sugar = 1.0,
                sodium = 210.0,
                saturatedFat = 2.5,
                transFat = 0.0,
                cholesterol = 5.0
            ),
            confidence = 0.93,
            warnings = listOf("slight blur"),
            rawJson = """{"raw":true}""",
            sourceImagePath = "/tmp/quest.jpg"
        )

        val profile: NutritionalProfile = analyzer.toProfile(
            extraction = extraction,
            primaryName = "  Quest Protein Bar  ",
            aliases = listOf(" snack ", "", "snack", "quest bar ", "quest bar ")
        )

        assertEquals("Quest Protein Bar", profile.primaryName)
        assertEquals(listOf("snack", "quest bar"), profile.aliases)
        assertEquals("1 bar", profile.servingSize)
        assertEquals(190.0, profile.calories)
        assertEquals(21.0, profile.protein)
        assertEquals(22.0, profile.carbohydrates)
        assertEquals(7.0, profile.fat)
        assertEquals(14.0, profile.fiber)
        assertEquals(1.0, profile.sugar)
        assertEquals(210.0, profile.sodium)
        assertEquals(2.5, profile.saturatedFat)
        assertEquals(0.0, profile.transFat)
        assertEquals(5.0, profile.cholesterol)
        assertEquals("""{"raw":true}""", profile.rawJson)
        assertEquals("/tmp/quest.jpg", profile.sourceImagePath)
        assertTrue(profile.externalId.startsWith("nutrition-profile-"))
        assertEquals(profile.createdAt, profile.updatedAt)
    }

    @Test
    fun `to profile serializes extraction when raw json is absent`() {
        val analyzer = NutritionLabelAnalyzer(llmClientFactory)
        val extraction = NutritionLabelExtraction(
            servingSize = "2 cookies",
            nutrition = ExtractedNutrition(totalCalories = 160.0, fat = 8.0),
            confidence = 0.75,
            sourceImagePath = "/tmp/cookies.jpg"
        )

        val profile = analyzer.toProfile(
            extraction = extraction,
            primaryName = "Cookies",
            aliases = emptyList()
        )

        assertNotNull(profile.rawJson)
        assertContains(profile.rawJson.orEmpty(), "\"servingSize\":\"2 cookies\"")
        assertContains(profile.rawJson.orEmpty(), "\"totalCalories\":160.0")
        assertContains(profile.rawJson.orEmpty(), "\"sourceImagePath\":\"/tmp/cookies.jpg\"")
    }
}
