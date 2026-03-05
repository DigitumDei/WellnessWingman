package com.wellnesswingman.data.model

import kotlinx.serialization.json.Json
import kotlin.test.*

class WeeklySummarySerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── WeightChangeSummary ──────────────────────────────────────────────────

    @Test
    fun `WeightChangeSummary round-trips with numeric start and end`() {
        val wcs = WeightChangeSummary(start = 80.2, end = 79.8, unit = "kg")
        val encoded = json.encodeToString(WeightChangeSummary.serializer(), wcs)
        val decoded = json.decodeFromString<WeightChangeSummary>(encoded)

        assertEquals(80.2, decoded.start)
        assertEquals(79.8, decoded.end)
        assertEquals("kg", decoded.unit)
    }

    @Test
    fun `WeightChangeSummary round-trips with null start and end`() {
        val wcs = WeightChangeSummary(start = null, end = null, unit = "lbs")
        val encoded = json.encodeToString(WeightChangeSummary.serializer(), wcs)
        val decoded = json.decodeFromString<WeightChangeSummary>(encoded)

        assertNull(decoded.start)
        assertNull(decoded.end)
        assertEquals("lbs", decoded.unit)
    }

    @Test
    fun `WeightChangeSummary uses kg as default unit`() {
        val wcs = WeightChangeSummary()
        assertEquals("kg", wcs.unit)
        assertNull(wcs.start)
        assertNull(wcs.end)
    }

    // ── WeeklySummaryPayload ─────────────────────────────────────────────────

    @Test
    fun `WeeklySummaryPayload round-trips with all new fields`() {
        val payload = WeeklySummaryPayload(
            weekStartDate = "2025-03-01",
            highlights = listOf("Great consistency", "Improved sleep"),
            recommendations = listOf("Add more protein", "Stay hydrated"),
            mealCount = 18,
            exerciseCount = 4,
            sleepCount = 6,
            otherCount = 2,
            totalEntries = 30,
            nutritionAverages = NutritionTotals(
                calories = 1850.0,
                protein = 80.0,
                carbs = 200.0,
                fat = 65.0,
                fiber = 25.0,
                sugar = 40.0,
                sodium = 1800.0
            ),
            nutritionTrend = "Consistent calories with increasing protein",
            weightChange = WeightChangeSummary(start = 70.5, end = 70.0, unit = "kg"),
            balanceSummary = "Well balanced week overall"
        )

        val encoded = json.encodeToString(WeeklySummaryPayload.serializer(), payload)
        val decoded = json.decodeFromString<WeeklySummaryPayload>(encoded)

        assertEquals(payload.weekStartDate, decoded.weekStartDate)
        assertEquals(payload.highlights, decoded.highlights)
        assertEquals(payload.recommendations, decoded.recommendations)
        assertEquals(payload.mealCount, decoded.mealCount)

        val nutrition = decoded.nutritionAverages
        assertNotNull(nutrition)
        assertEquals(1850.0, nutrition.calories)
        assertEquals(80.0, nutrition.protein)
        assertEquals(200.0, nutrition.carbs)
        assertEquals(65.0, nutrition.fat)
        assertEquals(25.0, nutrition.fiber)
        assertEquals(40.0, nutrition.sugar)
        assertEquals(1800.0, nutrition.sodium)

        assertEquals("Consistent calories with increasing protein", decoded.nutritionTrend)

        val wc = decoded.weightChange
        assertNotNull(wc)
        assertEquals(70.5, wc.start)
        assertEquals(70.0, wc.end)
        assertEquals("kg", wc.unit)

        assertEquals("Well balanced week overall", decoded.balanceSummary)
    }

    @Test
    fun `WeeklySummaryPayload round-trips with null optional new fields`() {
        val payload = WeeklySummaryPayload(
            weekStartDate = "2025-03-08",
            highlights = listOf("Good week"),
            recommendations = listOf("Keep going"),
            nutritionAverages = null,
            nutritionTrend = null,
            weightChange = null,
            balanceSummary = null
        )

        val encoded = json.encodeToString(WeeklySummaryPayload.serializer(), payload)
        val decoded = json.decodeFromString<WeeklySummaryPayload>(encoded)

        assertNull(decoded.nutritionAverages)
        assertNull(decoded.nutritionTrend)
        assertNull(decoded.weightChange)
        assertNull(decoded.balanceSummary)
    }

    @Test
    fun `WeeklySummaryPayload parses minimal JSON with defaults`() {
        val minimalJson = """{"weekStartDate":"2025-01-06"}"""
        val decoded = json.decodeFromString<WeeklySummaryPayload>(minimalJson)

        assertEquals("2025-01-06", decoded.weekStartDate)
        assertTrue(decoded.highlights.isEmpty())
        assertTrue(decoded.recommendations.isEmpty())
        assertEquals(0, decoded.mealCount)
        assertNull(decoded.nutritionAverages)
        assertNull(decoded.weightChange)
    }

    @Test
    fun `WeeklySummaryPayload has expected schema version`() {
        val payload = WeeklySummaryPayload(weekStartDate = "2025-03-01")
        assertEquals("1.1", payload.schemaVersion)
    }

    // ── DailySummaryPayload ──────────────────────────────────────────────────

    @Test
    fun `DailySummaryPayload round-trips with all fields`() {
        val payload = DailySummaryPayload(
            date = "2025-03-01",
            summary = "A productive day",
            highlights = listOf("High protein", "Good sleep"),
            recommendations = listOf("Drink more water"),
            nutritionTotals = NutritionTotals(
                calories = 2000.0, protein = 90.0, carbs = 220.0, fat = 70.0,
                fiber = 28.0, sugar = 35.0, sodium = 1500.0
            ),
            balance = NutritionalBalance(
                overall = "Balanced",
                macroBalance = "44C/35P/28F",
                timing = "Well-distributed meals",
                variety = "Good variety"
            ),
            mealCount = 3,
            exerciseCount = 1,
            sleepHours = 7.5
        )

        val encoded = json.encodeToString(DailySummaryPayload.serializer(), payload)
        val decoded = json.decodeFromString<DailySummaryPayload>(encoded)

        assertEquals("2025-03-01", decoded.date)
        assertEquals("A productive day", decoded.summary)
        assertEquals(listOf("High protein", "Good sleep"), decoded.highlights)
        assertEquals(listOf("Drink more water"), decoded.recommendations)
        assertEquals(2000.0, decoded.nutritionTotals?.calories)
        assertEquals(90.0, decoded.nutritionTotals?.protein)
        assertEquals("Balanced", decoded.balance?.overall)
        assertEquals("44C/35P/28F", decoded.balance?.macroBalance)
        assertEquals(7.5, decoded.sleepHours)
        assertEquals(3, decoded.mealCount)
        assertEquals(1, decoded.exerciseCount)
    }

    @Test
    fun `DailySummaryPayload has expected schema version`() {
        val payload = DailySummaryPayload(date = "2025-03-01", summary = "Test")
        assertEquals("1.0", payload.schemaVersion)
    }

    @Test
    fun `NutritionTotals default values are zero`() {
        val totals = NutritionTotals()
        assertEquals(0.0, totals.calories)
        assertEquals(0.0, totals.protein)
        assertEquals(0.0, totals.carbs)
        assertEquals(0.0, totals.fat)
        assertEquals(0.0, totals.fiber)
        assertEquals(0.0, totals.sugar)
        assertEquals(0.0, totals.sodium)
    }

    @Test
    fun `NutritionalBalance all fields nullable`() {
        val balance = NutritionalBalance()
        assertNull(balance.overall)
        assertNull(balance.macroBalance)
        assertNull(balance.timing)
        assertNull(balance.variety)
    }
}
