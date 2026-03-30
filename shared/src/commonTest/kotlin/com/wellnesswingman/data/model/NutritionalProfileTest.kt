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

        val decoded = json.decodeFromString<NutritionalProfileLookupResult>(encoded)
        assertEquals(original.profileId, decoded.profileId)
        assertEquals(original.primaryName, decoded.primaryName)
        assertEquals(original.nutrition.totalCalories, decoded.nutrition.totalCalories)
        assertEquals(original.nutrition.protein, decoded.nutrition.protein)
        assertEquals(original.source, decoded.source)
        
        // Final sanity check for the whole object
        assertEquals(original, decoded)
    }
}
