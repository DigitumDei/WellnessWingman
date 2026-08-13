package com.wellnesswingman.data.model.analysis

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers parsing model output that ignores the numeric types the schema asked for.
 *
 * The schema is sent with every request but is not reliably enforced, so a model asked for
 * `"confidence": 0.4` will sometimes answer `"confidence": "medium"`. Before this, one such field
 * aborted the whole object and the extraction was recorded as failed, discarding everything the
 * model got right.
 */
class LenientNumbersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Test
    fun `the payload that failed on 2026-08-12 now parses`() {
        // Reduced from a real morning check-in: "confidence": "medium" where a number was asked
        // for, which threw and lost the food and factors alongside it.
        val payload = """
            {
              "mentionedFood": [
                {
                  "name": "toast",
                  "portionSize": "two slices",
                  "nutrition": { "totalCalories": 180 },
                  "confidence": "medium",
                  "possiblyAlreadyLogged": false
                }
              ],
              "factors": [
                {
                  "description": "slept badly",
                  "valence": "Bad",
                  "origin": "Internal",
                  "confidence": "high"
                }
              ],
              "confidence": "medium"
            }
        """.trimIndent()

        val facets = json.decodeFromString(CheckInFacets.serializer(), payload)

        assertEquals(1, facets.mentionedFood.size)
        assertEquals(1, facets.factors.size)
        assertEquals(0.5, facets.confidence)
        assertEquals(0.85, facets.factors.single().confidence)
    }

    @Test
    fun `confidence words map onto the range they imply`() {
        fun confidenceOf(raw: String): Double = json.decodeFromString(
            CheckInFactor.serializer(),
            """{"description":"x","valence":"Bad","origin":"Internal","confidence":$raw}"""
        ).confidence

        assertEquals(0.95, confidenceOf("\"very high\""))
        assertEquals(0.85, confidenceOf("\"High\""))
        assertEquals(0.5, confidenceOf("\"moderate\""))
        assertEquals(0.25, confidenceOf("\"low\""))
        assertEquals(0.1, confidenceOf("\"very low\""))
        assertEquals(0.0, confidenceOf("\"unknown\""))
    }

    @Test
    fun `numeric strings and percentages are read as numbers`() {
        fun confidenceOf(raw: String): Double = json.decodeFromString(
            CheckInFactor.serializer(),
            """{"description":"x","valence":"Bad","origin":"Internal","confidence":$raw}"""
        ).confidence

        assertEquals(0.4, confidenceOf("\"0.4\""))
        assertEquals(0.8, confidenceOf("\"80%\""))
        assertEquals(0.7, confidenceOf("0.7"))
    }

    @Test
    fun `a confidence outside the range is clamped`() {
        val factor = json.decodeFromString(
            CheckInFactor.serializer(),
            """{"description":"x","valence":"Bad","origin":"Internal","confidence":4.2}"""
        )

        assertEquals(1.0, factor.confidence)
    }

    @Test
    fun `an unreadable confidence becomes zero rather than failing`() {
        val factor = json.decodeFromString(
            CheckInFactor.serializer(),
            """{"description":"x","valence":"Bad","origin":"Internal","confidence":"no idea"}"""
        )

        // Losing one confidence is survivable; losing the factor it belonged to is not.
        assertEquals(0.0, factor.confidence)
        assertEquals("x", factor.description)
    }

    @Test
    fun `nutrition values carrying units are read as numbers`() {
        val nutrition = json.decodeFromString(
            NutritionEstimate.serializer(),
            """{"totalCalories":"300 kcal","protein":"24g","sodium":"~450 mg","fat":"about 12"}"""
        )

        assertEquals(300.0, nutrition.totalCalories)
        assertEquals(24.0, nutrition.protein)
        assertEquals(450.0, nutrition.sodium)
        assertEquals(12.0, nutrition.fat)
    }

    @Test
    fun `thousands separators do not truncate the number`() {
        val nutrition = json.decodeFromString(
            NutritionEstimate.serializer(),
            """{"totalCalories":"1,200 kcal","sodium":"2_400 mg"}"""
        )

        // Stopping at the comma yielded 1.0 — not a rejected field but a confidently wrong one,
        // and a 1,200-calorie day silently becoming 1 calorie is the worst kind of wrong.
        assertEquals(1200.0, nutrition.totalCalories)
        assertEquals(2400.0, nutrition.sodium)
    }

    @Test
    fun `a space still separates two different numbers`() {
        val nutrition = json.decodeFromString(
            NutritionEstimate.serializer(),
            """{"totalCalories":"2 beers 300 kcal"}"""
        )

        // A plain space is deliberately not treated as grouping: reading this as 2300 would be
        // worse than taking the first number.
        assertEquals(2.0, nutrition.totalCalories)
    }

    @Test
    fun `an unreadable nutrition value becomes null rather than zero`() {
        val nutrition = json.decodeFromString(
            NutritionEstimate.serializer(),
            """{"totalCalories":"unknown","protein":250}"""
        )

        // Zero would quietly claim the food had no calories, which is a worse lie than silence.
        assertNull(nutrition.totalCalories)
        assertEquals(250.0, nutrition.protein)
    }

    @Test
    fun `nulls still read as null`() {
        val nutrition = json.decodeFromString(
            NutritionEstimate.serializer(),
            """{"totalCalories":null,"protein":null}"""
        )

        assertNull(nutrition.totalCalories)
        assertNull(nutrition.protein)
    }

    @Test
    fun `well-formed numeric output is unaffected`() {
        val facets = json.decodeFromString(
            CheckInFacets.serializer(),
            """
            {
              "mentionedFood": [
                {"name":"beer","nutrition":{"totalCalories":150,"carbohydrates":13},"confidence":0.6}
              ],
              "factors": [],
              "confidence": 0.75
            }
            """.trimIndent()
        )

        assertEquals(0.75, facets.confidence)
        val food = facets.mentionedFood.single()
        assertEquals(0.6, food.confidence)
        assertEquals(150.0, food.nutrition?.totalCalories)
    }

    @Test
    fun `a health score above one is not flattened by clamping`() {
        val insights = json.decodeFromString(
            HealthInsights.serializer(),
            """{"healthScore":"7 out of 10"}"""
        )

        // healthScore runs 0-10, so it must not go through the confidence serializer.
        assertEquals(7.0, insights.healthScore)
    }

    @Test
    fun `round-tripping a parsed object produces numbers again`() {
        val facets = json.decodeFromString(
            CheckInFacets.serializer(),
            """{"mentionedFood":[],"factors":[],"confidence":"medium"}"""
        )

        val encoded = json.encodeToString(CheckInFacets.serializer(), facets)

        // What gets stored is the normalised number, so a later read is plain data.
        assertTrue(encoded.contains("\"confidence\":0.5"))
        assertNotNull(json.decodeFromString(CheckInFacets.serializer(), encoded))
    }
}
