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
                    quantity = "1 medium",
                    estimatedCalories = 95
                )
            ),
            nutrition = NutritionEstimate(
                calories = 95,
                protein = 0.5,
                carbs = 25.0,
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
                    "quantity": "150g",
                    "estimatedCalories": 165
                }
            ],
            "nutrition": {
                "calories": 165,
                "protein": 31.0,
                "carbs": 0.0,
                "fat": 3.6
            }
        }
        """.trimIndent()

        val result = json.decodeFromString<MealAnalysisResult>(jsonString)

        assertEquals("1.0", result.schemaVersion)
        assertEquals(1, result.foodItems.size)
        assertEquals("Chicken Breast", result.foodItems[0].name)
        assertEquals(165, result.nutrition?.calories)
        assertEquals(31.0, result.nutrition?.protein)
    }

    @Test
    fun `MealAnalysisResult with health insights serializes correctly`() {
        val result = MealAnalysisResult(
            schemaVersion = "1.0",
            healthInsights = HealthInsights(
                generalComments = "Balanced meal",
                suggestions = listOf("Add vegetables", "Drink water")
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
            quantity = "1 cup",
            estimatedCalories = 150,
            brands = listOf("Quaker", "Bob's Red Mill"),
            preparationMethod = "Cooked with milk",
            portionDetails = "Served warm"
        )

        val jsonString = json.encodeToString(foodItem)

        assertTrue(jsonString.contains("Oatmeal"))
        assertTrue(jsonString.contains("Quaker"))
        assertTrue(jsonString.contains("Cooked with milk"))
    }

    @Test
    fun `NutritionEstimate with all fields serializes correctly`() {
        val nutrition = NutritionEstimate(
            calories = 500,
            protein = 25.5,
            carbs = 60.0,
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
                FoodItem("Rice", "1 cup", 200),
                FoodItem("Beans", "1/2 cup", 120)
            ),
            nutrition = NutritionEstimate(
                calories = 320,
                protein = 15.0,
                carbs = 58.0,
                fat = 2.0
            ),
            healthInsights = HealthInsights(
                generalComments = "Good fiber source",
                suggestions = listOf("Add protein")
            )
        )

        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<MealAnalysisResult>(jsonString)

        assertEquals(original.schemaVersion, deserialized.schemaVersion)
        assertEquals(original.foodItems.size, deserialized.foodItems.size)
        assertEquals(original.nutrition?.calories, deserialized.nutrition?.calories)
        assertEquals(original.healthInsights?.generalComments, deserialized.healthInsights?.generalComments)
    }
}
