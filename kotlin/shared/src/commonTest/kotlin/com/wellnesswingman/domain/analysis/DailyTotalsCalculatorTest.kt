package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.analysis.NutritionEstimate
import kotlinx.datetime.LocalDate
import kotlin.test.*

class DailyTotalsCalculatorTest {

    private lateinit var calculator: DailyTotalsCalculator

    @BeforeTest
    fun setup() {
        calculator = DailyTotalsCalculator()
    }

    @Test
    fun `calculateTotals returns zero totals when summaryContent is null`() {
        val summary = DailySummary(
            summaryId = 1,
            date = LocalDate(2024, 1, 1),
            summaryContent = null,
            highlights = "Test",
            recommendations = "",
            generatedAt = kotlinx.datetime.Clock.System.now()
        )

        val result = calculator.calculateTotals(summary)

        assertEquals(0, result.totalCalories)
        assertEquals(0.0, result.totalProtein)
        assertEquals(0.0, result.totalCarbs)
        assertEquals(0.0, result.totalFat)
    }

    @Test
    fun `calculateTotals sums nutrition values from multiple meals`() {
        val summaryContent = """
        {
            "meals": [
                {
                    "nutrition": {
                        "calories": 500,
                        "protein": 25.5,
                        "carbs": 60.0,
                        "fat": 15.0
                    }
                },
                {
                    "nutrition": {
                        "calories": 300,
                        "protein": 20.0,
                        "carbs": 40.0,
                        "fat": 10.0
                    }
                }
            ]
        }
        """.trimIndent()

        val summary = DailySummary(
            summaryId = 1,
            date = LocalDate(2024, 1, 1),
            summaryContent = summaryContent,
            highlights = "Test",
            recommendations = "",
            generatedAt = kotlinx.datetime.Clock.System.now()
        )

        val result = calculator.calculateTotals(summary)

        assertEquals(800, result.totalCalories)
        assertEquals(45.5, result.totalProtein)
        assertEquals(100.0, result.totalCarbs)
        assertEquals(25.0, result.totalFat)
    }

    @Test
    fun `calculateTotals handles missing nutrition values gracefully`() {
        val summaryContent = """
        {
            "meals": [
                {
                    "nutrition": {
                        "calories": 500
                    }
                }
            ]
        }
        """.trimIndent()

        val summary = DailySummary(
            summaryId = 1,
            date = LocalDate(2024, 1, 1),
            summaryContent = summaryContent,
            highlights = "Test",
            recommendations = "",
            generatedAt = kotlinx.datetime.Clock.System.now()
        )

        val result = calculator.calculateTotals(summary)

        assertEquals(500, result.totalCalories)
        assertEquals(0.0, result.totalProtein)
        assertEquals(0.0, result.totalCarbs)
        assertEquals(0.0, result.totalFat)
    }

    @Test
    fun `calculateTotals handles invalid JSON gracefully`() {
        val summary = DailySummary(
            summaryId = 1,
            date = LocalDate(2024, 1, 1),
            summaryContent = "invalid json {",
            highlights = "Test",
            recommendations = "",
            generatedAt = kotlinx.datetime.Clock.System.now()
        )

        val result = calculator.calculateTotals(summary)

        assertEquals(0, result.totalCalories)
        assertEquals(0.0, result.totalProtein)
        assertEquals(0.0, result.totalCarbs)
        assertEquals(0.0, result.totalFat)
    }

    @Test
    fun `calculateTotals rounds decimal values correctly`() {
        val summaryContent = """
        {
            "meals": [
                {
                    "nutrition": {
                        "calories": 333,
                        "protein": 10.333,
                        "carbs": 20.666,
                        "fat": 5.999
                    }
                }
            ]
        }
        """.trimIndent()

        val summary = DailySummary(
            summaryId = 1,
            date = LocalDate(2024, 1, 1),
            summaryContent = summaryContent,
            highlights = "Test",
            recommendations = "",
            generatedAt = kotlinx.datetime.Clock.System.now()
        )

        val result = calculator.calculateTotals(summary)

        assertEquals(333, result.totalCalories)
        assertTrue(result.totalProtein in 10.3..10.4) // Allow for floating point precision
        assertTrue(result.totalCarbs in 20.6..20.7)
        assertTrue(result.totalFat in 5.9..6.0)
    }
}
