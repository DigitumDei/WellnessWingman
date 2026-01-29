package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.NutritionTotals
import com.wellnesswingman.data.model.analysis.MealAnalysisResult

/**
 * Calculator for aggregating daily nutrition totals from meal analyses.
 */
class DailyTotalsCalculator {

    /**
     * Calculates nutrition totals from a list of meal analysis results.
     */
    fun calculate(analyses: List<MealAnalysisResult?>): NutritionTotals {
        var calories = 0.0
        var protein = 0.0
        var carbs = 0.0
        var fat = 0.0
        var fiber = 0.0
        var sugar = 0.0
        var sodium = 0.0

        analyses.forEach { analysis ->
            analysis?.nutrition?.let { nutrition ->
                calories += nutrition.totalCalories ?: 0.0
                protein += nutrition.protein ?: 0.0
                carbs += nutrition.carbohydrates ?: 0.0
                fat += nutrition.fat ?: 0.0
                fiber += nutrition.fiber ?: 0.0
                sugar += nutrition.sugar ?: 0.0
                sodium += nutrition.sodium ?: 0.0
            }
        }

        return NutritionTotals(
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            fiber = fiber,
            sugar = sugar,
            sodium = sodium
        )
    }
}
