package com.wellnesswingman.domain.analysis

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NutritionLabelExtractionTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `decodes optional primary name from extraction json`() {
        val extraction = json.decodeFromString<NutritionLabelExtraction>(
            """
            {
              "primaryName": "Fairlife Core Power",
              "servingSize": "1 bottle",
              "nutrition": {
                "totalCalories": 230,
                "protein": 42
              },
              "confidence": 0.97,
              "warnings": []
            }
            """.trimIndent()
        )

        assertEquals("Fairlife Core Power", extraction.primaryName)
        assertEquals("1 bottle", extraction.servingSize)
        assertEquals(230.0, extraction.nutrition.totalCalories)
        assertEquals(42.0, extraction.nutrition.protein)
    }

    @Test
    fun `primary name remains optional in extraction json`() {
        val extraction = json.decodeFromString<NutritionLabelExtraction>(
            """
            {
              "servingSize": "1 bar",
              "nutrition": {
                "totalCalories": 190
              },
              "confidence": 0.88,
              "warnings": []
            }
            """.trimIndent()
        )

        assertNull(extraction.primaryName)
        assertEquals("1 bar", extraction.servingSize)
        assertEquals(190.0, extraction.nutrition.totalCalories)
    }
}
