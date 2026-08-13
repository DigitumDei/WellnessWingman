package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.NutritionTotals
import com.wellnesswingman.data.model.analysis.CheckInFacets
import com.wellnesswingman.data.model.analysis.MealAnalysisResult

/**
 * Calculator for aggregating daily nutrition totals from meal analyses.
 */
class DailyTotalsCalculator {

    /**
     * Calculates nutrition totals from a list of meal analysis results.
     */
    fun calculate(analyses: List<MealAnalysisResult?>): NutritionTotals =
        calculate(analyses, emptyList())

    /**
     * Calculates nutrition totals from photographed meals plus food the user only mentioned in a
     * check-in.
     *
     * The two sources are added together deliberately: a day's total should reflect what was
     * actually eaten, not only what was photographed. They are not equally reliable, though —
     * a photo estimate has an image behind it, a text estimate has a sentence — so
     * [NutritionTotals.mentionedCalories] keeps the mentioned share visible for anything that
     * needs to weigh them differently.
     *
     * Items the extraction flagged as [com.wellnesswingman.data.model.analysis.MentionedFood.possiblyAlreadyLogged]
     * are skipped. Without that, a meal both photographed at lunch and talked about in the
     * evening check-in would land in the total twice.
     */
    fun calculate(
        analyses: List<MealAnalysisResult?>,
        checkInFacets: List<CheckInFacets>
    ): NutritionTotals {
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

        var mentionedCalories = 0.0
        var mentionedItemCount = 0

        checkInFacets.forEach { facets ->
            facets.countableFood.forEach { food ->
                // Counted whether or not it carries numbers: "just black coffee" is real
                // extracted data, and the nutrition card is gated on this rather than on
                // calories so a zero-calorie mention still surfaces.
                mentionedItemCount++

                food.nutrition?.let { nutrition ->
                    val itemCalories = nutrition.totalCalories ?: 0.0
                    calories += itemCalories
                    mentionedCalories += itemCalories
                    protein += nutrition.protein ?: 0.0
                    carbs += nutrition.carbohydrates ?: 0.0
                    fat += nutrition.fat ?: 0.0
                    fiber += nutrition.fiber ?: 0.0
                    sugar += nutrition.sugar ?: 0.0
                    sodium += nutrition.sodium ?: 0.0
                }
            }
        }

        return NutritionTotals(
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            fiber = fiber,
            sugar = sugar,
            sodium = sodium,
            mentionedCalories = mentionedCalories,
            mentionedItemCount = mentionedItemCount
        )
    }
}
