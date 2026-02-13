package com.wellnesswingman.data.model.analysis

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.test.*

class MealAnalysisResultTest {

    private val json = Json { prettyPrint = false }

    @Test
    fun `MealAnalysisResult can be serialized to JSON`() {
        val result = MealAnalysisResult(
            schemaVersion = "1.0",
            foodItems = listOf(
                FoodItem(
                    name = "Apple",
                    portionSize = "1 medium",
                    calories = 95.0
                )
            ),
            nutrition = NutritionEstimate(
                totalCalories = 95.0,
                protein = 0.5,
                carbohydrates = 25.0,
                fat = 0.3
            )
        )

        val jsonString = json.encodeToString(result)

        assertTrue(jsonString.contains("Apple"))
        assertTrue(jsonString.contains("95"))
        assertTrue(jsonString.contains("1 medium"))
    }

    @Test
    fun `MealAnalysisResult can be deserialized from JSON`() {
        val jsonString = """
        {
            "schemaVersion": "1.0",
            "foodItems": [
                {
                    "name": "Chicken Breast",
                    "portionSize": "150g",
                    "calories": 165.0
                }
            ],
            "nutrition": {
                "totalCalories": 165.0,
                "protein": 31.0,
                "carbohydrates": 0.0,
                "fat": 3.6
            }
        }
        """.trimIndent()

        val result = json.decodeFromString<MealAnalysisResult>(jsonString)

        assertEquals("1.0", result.schemaVersion)
        assertEquals(1, result.foodItems.size)
        assertEquals("Chicken Breast", result.foodItems[0].name)
        assertEquals(165.0, result.nutrition?.totalCalories)
        assertEquals(31.0, result.nutrition?.protein)
    }

    @Test
    fun `MealAnalysisResult with health insights serializes correctly`() {
        val result = MealAnalysisResult(
            schemaVersion = "1.0",
            healthInsights = HealthInsights(
                summary = "Balanced meal",
                recommendations = listOf("Add vegetables", "Drink water")
            )
        )

        val jsonString = json.encodeToString(result)

        assertTrue(jsonString.contains("Balanced meal"))
        assertTrue(jsonString.contains("Add vegetables"))
    }

    @Test
    fun `FoodItem with all optional fields serializes correctly`() {
        val foodItem = FoodItem(
            name = "Oatmeal",
            portionSize = "1 cup",
            calories = 150.0,
            confidence = 0.95
        )

        val jsonString = json.encodeToString(foodItem)

        assertTrue(jsonString.contains("Oatmeal"))
        assertTrue(jsonString.contains("1 cup"))
        assertTrue(jsonString.contains("150"))
    }

    @Test
    fun `NutritionEstimate with all fields serializes correctly`() {
        val nutrition = NutritionEstimate(
            totalCalories = 500.0,
            protein = 25.5,
            carbohydrates = 60.0,
            fat = 15.0,
            fiber = 8.0,
            sugar = 10.0,
            sodium = 400.0
        )

        val jsonString = json.encodeToString(nutrition)

        assertTrue(jsonString.contains("500"))
        assertTrue(jsonString.contains("25.5"))
        assertTrue(jsonString.contains("8.0"))
    }

    @Test
    fun `HealthInsights with all fields serializes correctly`() {
        val insights = HealthInsights(
            healthScore = 8.5,
            summary = "Healthy meal with good protein",
            positives = listOf("High protein", "Low sugar"),
            improvements = listOf("Add more vegetables"),
            recommendations = listOf("Include leafy greens", "Reduce sodium")
        )

        val jsonString = json.encodeToString(insights)

        assertTrue(jsonString.contains("8.5"))
        assertTrue(jsonString.contains("High protein"))
        assertTrue(jsonString.contains("Add more vegetables"))
    }

    @Test
    fun `MealAnalysisResult with empty lists is valid`() {
        val result = MealAnalysisResult(
            schemaVersion = "1.0",
            foodItems = emptyList()
        )

        assertNotNull(result)
        assertTrue(result.foodItems.isEmpty())
    }

    @Test
    fun `MealAnalysisResult roundtrip serialization preserves data`() {
        val original = MealAnalysisResult(
            schemaVersion = "1.0",
            foodItems = listOf(
                FoodItem("Rice", "1 cup", 200.0),
                FoodItem("Beans", "1/2 cup", 120.0)
            ),
            nutrition = NutritionEstimate(
                totalCalories = 320.0,
                protein = 15.0,
                carbohydrates = 58.0,
                fat = 2.0
            ),
            healthInsights = HealthInsights(
                summary = "Good fiber source",
                recommendations = listOf("Add protein")
            ),
            confidence = 0.92
        )

        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<MealAnalysisResult>(jsonString)

        assertEquals(original.schemaVersion, deserialized.schemaVersion)
        assertEquals(original.foodItems.size, deserialized.foodItems.size)
        assertEquals(original.nutrition?.totalCalories, deserialized.nutrition?.totalCalories)
        assertEquals(original.healthInsights?.summary, deserialized.healthInsights?.summary)
        assertEquals(original.confidence, deserialized.confidence)
    }
}
