package com.wellnesswingman.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutritionalProfileTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun `nutritional profile round trips through serialization`() {
        val profile = NutritionalProfile(
            profileId = 12L,
            externalId = "fairlife-core-power",
            primaryName = "Fairlife Core Power",
            aliases = listOf("core power", "protein shake"),
            servingSize = "1 bottle",
            calories = 230.0,
            protein = 42.0,
            carbohydrates = 9.0,
            fat = 3.5,
            fiber = 1.0,
            sugar = 7.0,
            sodium = 240.0,
            saturatedFat = 1.5,
            transFat = 0.0,
            cholesterol = 20.0,
            rawJson = """{"source":"llm"}""",
            sourceImagePath = "/tmp/core-power.jpg",
            createdAt = Instant.parse("2026-03-30T10:00:00Z"),
            updatedAt = Instant.parse("2026-03-30T11:00:00Z")
        )

        val decoded = json.decodeFromString<NutritionalProfile>(json.encodeToString(profile))

        assertEquals(profile, decoded)
    }

    @Test
    fun `lookup result applies defaults when optional fields are omitted`() {
        val decoded = json.decodeFromString<NutritionalProfileLookupResult>(
            """
            {
              "profileId": 7,
              "primaryName": "Quest Protein Bar"
            }
            """.trimIndent()
        )

        assertEquals(7L, decoded.profileId)
        assertEquals("Quest Protein Bar", decoded.primaryName)
        assertEquals(emptyList(), decoded.aliases)
        assertNull(decoded.servingSize)
        assertNull(decoded.nutrition.totalCalories)
        assertEquals("exact", decoded.source)
    }

    @Test
    fun `lookup result includes nested nutrition payload when provided`() {
        val original = NutritionalProfileLookupResult(
            profileId = 8L,
            primaryName = "Quest Protein Bar",
            aliases = listOf("protein bar"),
            servingSize = "1 bar",
            nutrition = NutritionalProfileNutrition(
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
            source = "exact"
        )
        val encoded = json.encodeToString(original)

        // Verify key presence and structure without being brittle about Double formatting
        assertTrue(encoded.contains("\"totalCalories\":"))
        assertTrue(encoded.contains("\"protein\":"))
        assertTrue(encoded.contains("\"source\":\"exact\""))

        val decoded = json.decodeFromString<NutritionalProfileLookupResult>(encoded)
        
        assertEquals(8L, decoded.profileId)
        assertEquals("Quest Protein Bar", decoded.primaryName)
        assertEquals(listOf("protein bar"), decoded.aliases)
        assertEquals("1 bar", decoded.servingSize)
        assertEquals(190.0, decoded.nutrition.totalCalories)
        assertEquals(21.0, decoded.nutrition.protein)
        assertEquals(22.0, decoded.nutrition.carbohydrates)
        assertEquals(7.0, decoded.nutrition.fat)
        assertEquals(14.0, decoded.nutrition.fiber)
        assertEquals(1.0, decoded.nutrition.sugar)
        assertEquals(210.0, decoded.nutrition.sodium)
        assertEquals(2.5, decoded.nutrition.saturatedFat)
        assertEquals(0.0, decoded.nutrition.transFat)
        assertEquals(5.0, decoded.nutrition.cholesterol)
        assertEquals("exact", decoded.source)
        
        // Final sanity check for the whole object
        assertEquals(original, decoded)
    }
}
