package com.wellnesswingman.domain.analysis

import com.wellnesswingman.data.model.analysis.CheckInFacets
import com.wellnesswingman.data.model.analysis.MealAnalysisResult
import com.wellnesswingman.data.model.analysis.MentionedFood
import com.wellnesswingman.data.model.analysis.NutritionEstimate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers merging food the user only mentioned in a check-in into the day's totals.
 *
 * The merge was a deliberate choice — a day should reflect what was actually eaten, not only what
 * was photographed — and it makes double counting a real failure mode, which is what most of
 * these tests are about.
 */
class DailyTotalsCalculatorCheckInTest {

    private val calculator = DailyTotalsCalculator()

    private fun meal(calories: Double, protein: Double = 0.0) = MealAnalysisResult(
        nutrition = NutritionEstimate(totalCalories = calories, protein = protein)
    )

    private fun mentioned(
        name: String,
        calories: Double,
        protein: Double = 0.0,
        alreadyLogged: Boolean = false
    ) = MentionedFood(
        name = name,
        nutrition = NutritionEstimate(totalCalories = calories, protein = protein),
        possiblyAlreadyLogged = alreadyLogged
    )

    @Test
    fun `mentioned food is added to photographed meals`() {
        val totals = calculator.calculate(
            listOf(meal(1840.0)),
            listOf(CheckInFacets(mentionedFood = listOf(mentioned("beer", 300.0), mentioned("chips", 220.0))))
        )

        assertEquals(2360.0, totals.calories)
    }

    @Test
    fun `food flagged as already logged is excluded from the total`() {
        val totals = calculator.calculate(
            listOf(meal(1840.0)),
            listOf(
                CheckInFacets(
                    mentionedFood = listOf(
                        mentioned("chicken salad", 450.0, alreadyLogged = true),
                        mentioned("beer", 300.0)
                    )
                )
            )
        )

        // The salad was photographed at lunch and talked about again in the evening; counting it
        // twice is the failure this flag exists to prevent.
        assertEquals(2140.0, totals.calories)
    }

    @Test
    fun `macros from mentioned food are merged too`() {
        val totals = calculator.calculate(
            listOf(meal(500.0, protein = 30.0)),
            listOf(CheckInFacets(mentionedFood = listOf(mentioned("protein shake", 200.0, protein = 25.0))))
        )

        assertEquals(55.0, totals.protein)
    }

    @Test
    fun `a day with only mentioned food still totals`() {
        val totals = calculator.calculate(
            emptyList(),
            listOf(CheckInFacets(mentionedFood = listOf(mentioned("toast", 180.0))))
        )

        // Nothing was photographed, but something was eaten and said out loud.
        assertEquals(180.0, totals.calories)
    }

    @Test
    fun `the mentioned share is tracked separately from the merged total`() {
        val totals = calculator.calculate(
            listOf(meal(1840.0)),
            listOf(CheckInFacets(mentionedFood = listOf(mentioned("beer", 300.0))))
        )

        // Merged for the headline figure, but a text estimate is softer than a photo one and
        // anything presenting these numbers should be able to say so.
        assertEquals(2140.0, totals.calories)
        assertEquals(300.0, totals.mentionedCalories)
        assertTrue(totals.hasMentionedFood)
    }

    @Test
    fun `an already-logged item does not count toward the mentioned share either`() {
        val totals = calculator.calculate(
            listOf(meal(1840.0)),
            listOf(CheckInFacets(mentionedFood = listOf(mentioned("salad", 450.0, alreadyLogged = true))))
        )

        assertEquals(0.0, totals.mentionedCalories)
        assertFalse(totals.hasMentionedFood)
    }

    @Test
    fun `mentioned food without a nutrition estimate contributes nothing`() {
        val totals = calculator.calculate(
            listOf(meal(1840.0)),
            listOf(CheckInFacets(mentionedFood = listOf(MentionedFood(name = "something vague"))))
        )

        // Better a total that stays honest than one padded with a number nobody produced.
        assertEquals(1840.0, totals.calories)
    }

    @Test
    fun `the single-argument overload still behaves as before`() {
        assertEquals(1840.0, calculator.calculate(listOf(meal(1840.0))).calories)
    }
}
