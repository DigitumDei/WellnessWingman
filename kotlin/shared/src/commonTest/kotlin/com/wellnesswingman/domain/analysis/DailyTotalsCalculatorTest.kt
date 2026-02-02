package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.analysis.MealAnalysisResult
import com.wellnesswingman.data.model.analysis.NutritionEstimate
import kotlin.test.*

class DailyTotalsCalculatorTest {

    private lateinit var calculator: DailyTotalsCalculator

    @BeforeTest
    fun setup() {
        calculator = DailyTotalsCalculator()
    }

    @Test
    fun `calculate returns zero totals when list is empty`() {
        val result = calculator.calculate(emptyList())

        assertEquals(0.0, result.calories)
        assertEquals(0.0, result.protein)
        assertEquals(0.0, result.carbs)
        assertEquals(0.0, result.fat)
    }

    @Test
    fun `calculate sums nutrition values from multiple meals`() {
        val meals = listOf(
            MealAnalysisResult(
                nutrition = NutritionEstimate(
                    totalCalories = 500.0,
                    protein = 25.5,
                    carbohydrates = 60.0,
                    fat = 15.0
                )
            ),
            MealAnalysisResult(
                nutrition = NutritionEstimate(
                    totalCalories = 300.0,
                    protein = 20.0,
                    carbohydrates = 40.0,
                    fat = 10.0
                )
            )
        )

        val result = calculator.calculate(meals)

        assertEquals(800.0, result.calories)
        assertEquals(45.5, result.protein)
        assertEquals(100.0, result.carbs)
        assertEquals(25.0, result.fat)
    }

    @Test
    fun `calculate handles null nutrition gracefully`() {
        val meals = listOf(
            MealAnalysisResult(nutrition = null),
            MealAnalysisResult(
                nutrition = NutritionEstimate(totalCalories = 500.0)
            )
        )

        val result = calculator.calculate(meals)

        assertEquals(500.0, result.calories)
        assertEquals(0.0, result.protein)
        assertEquals(0.0, result.carbs)
        assertEquals(0.0, result.fat)
    }

    @Test
    fun `calculate handles null values in nutrition`() {
        val meals = listOf(
            MealAnalysisResult(
                nutrition = NutritionEstimate(
                    totalCalories = 500.0,
                    protein = null,
                    carbohydrates = null,
                    fat = null
                )
            )
        )

        val result = calculator.calculate(meals)

        assertEquals(500.0, result.calories)
        assertEquals(0.0, result.protein)
        assertEquals(0.0, result.carbs)
        assertEquals(0.0, result.fat)
    }

    @Test
    fun `calculate includes fiber, sugar, and sodium`() {
        val meals = listOf(
            MealAnalysisResult(
                nutrition = NutritionEstimate(
                    totalCalories = 333.0,
                    protein = 10.0,
                    carbohydrates = 20.0,
                    fat = 5.0,
                    fiber = 8.0,
                    sugar = 10.0,
                    sodium = 400.0
                )
            )
        )

        val result = calculator.calculate(meals)

        assertEquals(333.0, result.calories)
        assertEquals(10.0, result.protein)
        assertEquals(20.0, result.carbs)
        assertEquals(5.0, result.fat)
        assertEquals(8.0, result.fiber)
        assertEquals(10.0, result.sugar)
        assertEquals(400.0, result.sodium)
    }
}
