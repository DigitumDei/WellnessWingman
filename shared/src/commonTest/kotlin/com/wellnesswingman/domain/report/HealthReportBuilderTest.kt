package com.wellnesswingman.domain.report

import com.wellnesswingman.data.model.analysis.MentionedFood
import com.wellnesswingman.data.model.analysis.NutritionEstimate
import com.wellnesswingman.data.model.polar.PolarDailyActivity
import com.wellnesswingman.data.model.polar.PolarNightlyRecharge
import com.wellnesswingman.data.model.polar.PolarSleepResult
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthReportBuilderTest {

    private val builder = HealthReportBuilder()
    private val generatedAt = Instant.parse("2024-01-13T10:00:00Z")

    private val sampleData = HealthReportData(
        start = LocalDate(2024, 1, 10),
        end = LocalDate(2024, 1, 12),
        timeZoneId = "UTC",
        profile = ReportProfile(
            height = 180.0,
            heightUnit = "cm",
            goalsAndPreferences = "Lose weight"
        ),
        meals = listOf(
            ReportMealEntry(
                date = LocalDate(2024, 1, 11),
                localTime = LocalDateTime(2024, 1, 11, 12, 30),
                userNotes = "Logged lunch",
                foodItems = listOf(
                    ReportFoodItem("Rice bowl", "1 bowl", 450.0, ReportProvenance.EXACT_PROFILE)
                ),
                nutrition = ReportNutrition(totalCalories = 450.0, protein = 20.0),
                healthInsights = ReportHealthInsights(summary = "Balanced meal")
            )
        ),
        weightRecords = listOf(
            ReportWeightRecord(
                date = LocalDate(2024, 1, 11),
                recordedAt = LocalDateTime(2024, 1, 11, 7, 0),
                value = 80.0,
                unit = "kg",
                provenance = ReportProvenance.USER_ENTERED
            )
        )
    )

    private fun render(data: HealthReportData): String =
        builder.build(data, generatedAt).blocks.joinToString("\n") { block ->
            when (block) {
                is HealthReportBlock.Heading -> "H${block.level}: ${block.text}"
                is HealthReportBlock.Paragraph -> "P: ${block.text}"
                is HealthReportBlock.BulletList -> block.items.joinToString("\n") { "  - $it" }
                is HealthReportBlock.Table -> block.headers.joinToString(" | ") + "\n" +
                    block.rows.joinToString("\n") { row -> row.joinToString(" | ") }
                HealthReportBlock.Divider -> "---"
            }
        }

    @Test
    fun `identical snapshots produce identical documents`() {
        val first = builder.build(sampleData, generatedAt)
        val second = builder.build(sampleData, generatedAt)

        assertEquals(first, second)
        assertEquals(render(sampleData), render(sampleData))
    }

    @Test
    fun `document is chronological by local day and time within each section`() {
        val data = sampleData.copy(
            meals = listOf(
                ReportMealEntry(
                    date = LocalDate(2024, 1, 12),
                    localTime = LocalDateTime(2024, 1, 12, 8, 0),
                    userNotes = "Meal C"
                ),
                ReportMealEntry(
                    date = LocalDate(2024, 1, 10),
                    localTime = LocalDateTime(2024, 1, 10, 8, 0),
                    userNotes = "Meal A"
                ),
                ReportMealEntry(
                    date = LocalDate(2024, 1, 11),
                    localTime = LocalDateTime(2024, 1, 11, 19, 0),
                    userNotes = "Meal B"
                )
            ),
            sleep = listOf(
                ReportSleepEntry(
                    date = LocalDate(2024, 1, 11),
                    localTime = LocalDateTime(2024, 1, 11, 22, 0),
                    userNotes = "Sleep B"
                ),
                ReportSleepEntry(
                    date = LocalDate(2024, 1, 10),
                    localTime = LocalDateTime(2024, 1, 10, 22, 0),
                    userNotes = "Sleep A"
                )
            ),
            weightRecords = emptyList()
        )

        val text = render(data)

        // Section-scoped: slice the meals section and assert order there,
        // independent of the range header that mentions 2024-01-10..2024-01-12.
        val mealsSection = sectionText(text, "Meals and nutrition", "Sleep and recovery")
        val mealA = mealsSection.indexOf("Note: Meal A")
        val mealB = mealsSection.indexOf("Note: Meal B")
        val mealC = mealsSection.indexOf("Note: Meal C")
        assertTrue(mealA in 0 until mealB)
        assertTrue(mealB in 0 until mealC)

        val sleepSection = sectionText(text, "Sleep and recovery", "")
        val sleepA = sleepSection.indexOf("note: Sleep A")
        val sleepB = sleepSection.indexOf("note: Sleep B")
        assertTrue(sleepA in 0 until sleepB)
    }

    @Test
    fun `check-ins and weight are ordered chronologically`() {
        val data = sampleData.copy(
            meals = emptyList(),
            weightRecords = listOf(
                ReportWeightRecord(
                    date = LocalDate(2024, 1, 12),
                    recordedAt = LocalDateTime(2024, 1, 12, 9, 0),
                    value = 80.2,
                    unit = "kg",
                    provenance = ReportProvenance.USER_ENTERED
                ),
                ReportWeightRecord(
                    date = LocalDate(2024, 1, 10),
                    recordedAt = LocalDateTime(2024, 1, 10, 9, 0),
                    value = 80.0,
                    unit = "kg",
                    provenance = ReportProvenance.USER_ENTERED
                )
            ),
            checkIns = listOf(
                ReportCheckIn(
                    date = LocalDate(2024, 1, 12),
                    slot = "Morning",
                    responseText = "Check-in B",
                    inputSource = "Typed"
                ),
                ReportCheckIn(
                    date = LocalDate(2024, 1, 10),
                    slot = "Evening",
                    responseText = "Check-in A",
                    inputSource = "Typed"
                )
            )
        )

        val text = render(data)

        val weightSection = sectionText(text, "Weight history", "")
        assertTrue(weightSection.indexOf("80 kg") in 0 until weightSection.indexOf("80.2 kg"))

        val checkInSection = sectionText(text, "Daily check-ins", "Weight history")
        assertTrue(checkInSection.indexOf("Check-in A") in 0 until checkInSection.indexOf("Check-in B"))
    }

    private fun sectionText(text: String, from: String, to: String): String {
        val start = text.indexOf(from)
        val end = if (to.isEmpty()) text.length else text.indexOf(to, start)
        return text.substring(start, end)
    }

    @Test
    fun `exercise entry without analysis labels time user-entered and no metrics`() {
        val data = sampleData.copy(
            meals = emptyList(),
            weightRecords = emptyList(),
            exercise = listOf(
                ReportExerciseEntry(
                    date = LocalDate(2024, 1, 11),
                    localTime = LocalDateTime(2024, 1, 11, 18, 0),
                    userNotes = "Ran in the park",
                    activityType = null,
                    metrics = null,
                    insights = null
                )
            )
        )

        val text = render(data)

        assertTrue(text.contains("Exercise at 18:00 (user-entered)"))
        assertTrue(text.contains("note: Ran in the park (user-entered)"))
        assertTrue(!text.contains("Metrics (AI-estimated)"))
        assertTrue(!text.contains("Summary:"))
    }

    @Test
    fun `sleep entry without analysis labels time user-entered and no analysis values`() {
        val data = sampleData.copy(
            meals = emptyList(),
            weightRecords = emptyList(),
            sleep = listOf(
                ReportSleepEntry(
                    date = LocalDate(2024, 1, 11),
                    localTime = LocalDateTime(2024, 1, 11, 22, 0),
                    userNotes = "Slept badly",
                    analysis = null
                )
            )
        )

        val text = render(data)

        assertTrue(text.contains("Sleep at 22:00 (user-entered)"))
        assertTrue(text.contains("note: Slept badly (user-entered)"))
        assertTrue(!text.contains("Analysis (AI-estimated)"))
        assertTrue(!text.contains("Quality:"))
    }

    @Test
    fun `exercise activity type is labelled AI-estimated`() {
        val data = sampleData.copy(
            meals = emptyList(),
            weightRecords = emptyList(),
            exercise = listOf(
                ReportExerciseEntry(
                    date = LocalDate(2024, 1, 11),
                    localTime = LocalDateTime(2024, 1, 11, 18, 0),
                    userNotes = "Ran in the park",
                    activityType = "Running",
                    metrics = null,
                    insights = null
                )
            )
        )

        val text = render(data)

        assertTrue(text.contains("Exercise at 18:00 (user-entered) (Running, AI-estimated)"))
        assertTrue(text.contains("note: Ran in the park (user-entered)"))
    }

    @Test
    fun `empty profile passed directly to the builder is omitted`() {
        val data = sampleData.copy(
            profile = ReportProfile(),
            meals = emptyList(),
            weightRecords = emptyList()
        )

        val text = render(data)

        assertTrue(!text.contains("Profile and goals"))
    }

    @Test
    fun `profile with any single value is rendered`() {
        val data = sampleData.copy(
            profile = ReportProfile(sex = "Female"),
            meals = emptyList(),
            weightRecords = emptyList()
        )

        val text = render(data)

        assertTrue(text.contains("Profile and goals"))
        assertTrue(text.contains("Sex: Female"))
    }

    @Test
    fun `blank nutrition estimate renders no nutrition bullet`() {
        val data = sampleData.copy(
            meals = listOf(
                ReportMealEntry(
                    date = LocalDate(2024, 1, 11),
                    localTime = LocalDateTime(2024, 1, 11, 12, 30),
                    userNotes = null,
                    foodItems = emptyList(),
                    nutrition = ReportNutrition(),
                    healthInsights = null
                )
            ),
            weightRecords = emptyList()
        )

        val text = render(data)

        assertTrue(!text.contains("Nutrition: "))
    }

    @Test
    fun `measurements carry units and provenance labels`() {
        val text = render(sampleData)

        assertTrue(text.contains("450 kcal"))
        assertTrue(text.contains("20 g protein"))
        assertTrue(text.contains("exact nutritional profile"))
        assertTrue(text.contains("user-entered"))
        assertTrue(text.contains("80 kg"))
        assertTrue(text.contains("180 cm"))
    }

    @Test
    fun `AI-estimated values are labelled`() {
        val data = sampleData.copy(
            meals = listOf(
                ReportMealEntry(
                    date = LocalDate(2024, 1, 11),
                    localTime = LocalDateTime(2024, 1, 11, 12, 30),
                    userNotes = null,
                    foodItems = listOf(
                        ReportFoodItem("Pasta", null, 500.0, ReportProvenance.AI_ESTIMATED)
                    ),
                    nutrition = null
                )
            ),
            weightRecords = emptyList()
        )

        val text = render(data)
        assertTrue(text.contains("AI-estimated"))
        assertTrue(text.contains("500 kcal"))
    }

    @Test
    fun `empty sections are omitted entirely`() {
        val data = sampleData.copy(weightRecords = emptyList(), meals = emptyList())

        val text = render(data)
        assertTrue(!text.contains("Weight history"))
        assertTrue(!text.contains("Meals and nutrition"))
        assertTrue(text.contains("Profile and goals"))
    }

    @Test
    fun `empty range renders an explanatory paragraph without fabricated zeros`() {
        val empty = HealthReportData(
            start = LocalDate(2024, 1, 10),
            end = LocalDate(2024, 1, 12),
            timeZoneId = "UTC"
        )

        val text = render(empty)

        assertTrue(text.contains("No data was found"))
        assertTrue(!text.contains("0 kcal"))
        assertTrue(!text.contains("Weight history"))
    }

    @Test
    fun `check-in food and factors render with provenance`() {
        val data = sampleData.copy(
            meals = emptyList(),
            weightRecords = emptyList(),
            checkIns = listOf(
                ReportCheckIn(
                    date = LocalDate(2024, 1, 11),
                    slot = "Morning",
                    responseText = "Rough night",
                    inputSource = "Typed",
                    mentionedFood = listOf(
                        MentionedFood(
                            name = "Banana",
                            portionSize = "one",
                            nutrition = NutritionEstimate(totalCalories = 105.0)
                        )
                    ),
                    goodFactors = emptyList(),
                    badFactors = emptyList()
                )
            )
        )

        val text = render(data)

        assertTrue(text.contains("2024-01-11 — Morning check-in (user-entered)"))
        assertTrue(text.contains("Rough night"))
        assertTrue(text.contains("Mentioned food: Banana (one), 105 kcal (AI-estimated)"))
    }

    @Test
    fun `redaction inventory holds for a full document`() {
        val text = render(sampleData)

        // Internal identifiers and paths never appear.
        assertTrue(!text.contains("entryId"))
        assertTrue(!text.contains("externalId"))
        assertTrue(!text.contains("analysisId"))
        assertTrue(!text.contains("blobPath"))
        assertTrue(!text.contains("providerId"))
        assertTrue(!text.contains("dataPayload"))
        assertTrue(!text.contains("insightsJson"))
        assertTrue(!text.contains("rawJson"))
        assertTrue(!text.contains("conversationExternalId"))
    }

    @Test
    fun `polar day renders measured values with units`() {
        val data = sampleData.copy(
            meals = emptyList(),
            weightRecords = emptyList(),
            polarDays = listOf(
                ReportPolarDay(
                    date = LocalDate(2024, 1, 11),
                    activity = PolarDailyActivity(
                        date = "2024-01-11",
                        totalSteps = 8_000,
                        stepSampleStartTime = "00:00",
                        stepSampleIntervalMs = 60_000,
                        stepSamples = emptyList()
                    ),
                    sleep = PolarSleepResult(
                        date = "2024-01-11", sleepStart = "00:00", sleepEnd = "07:00",
                        durationSeconds = 25_200, deepSleepSeconds = 6_000, remSleepSeconds = 6_000,
                        lightSleepSeconds = 12_000, awakeSeconds = 1_200, efficiencyPercent = 95.0,
                        continuityIndex = 90.0, interruptionCount = 1, longInterruptionCount = 0,
                        sleepScore = 85.0, remScore = 80.0, deepSleepScore = 85.0, scoreRate = 4
                    ),
                    trainingSessions = emptyList(),
                    nightlyRecharge = PolarNightlyRecharge(
                        date = "2024-01-11", ansStatus = 62.0, ansRate = 3, recoveryIndicator = 62,
                        recoveryIndicatorSubLevel = 2, hrvRmssd = 55, hrvMeanRri = 950,
                        baselineRmssd = 50, baselineRmssdSd = 5, baselineRri = 940, baselineRriSd = 30
                    )
                )
            )
        )

        val text = render(data)

        assertTrue(text.contains("Steps: 8000"))
        assertTrue(text.contains("Sleep: 7 hours"))
        assertTrue(text.contains("score 85/100"))
        assertTrue(text.contains("HRV RMSSD 55 ms"))
        assertTrue(text.contains("Polar-measured"))
    }

    @Test
    fun `weekly summary renders entry counts and comments`() {
        val data = sampleData.copy(
            meals = emptyList(),
            weightRecords = emptyList(),
            weeklySummaries = listOf(
                ReportWeeklySummary(
                    weekStartDate = LocalDate(2024, 1, 8),
                    highlights = "Good week",
                    recommendations = "Drink more water",
                    mealCount = 5,
                    exerciseCount = 3,
                    sleepCount = 7,
                    otherCount = 1,
                    totalEntries = 16,
                    userComments = "Felt great"
                )
            )
        )

        val text = render(data)

        assertTrue(text.contains("Week of 2024-01-08"))
        assertTrue(text.contains("Entries: 16 total (5 meals, 3 exercise, 7 sleep, 1 other)"))
        assertTrue(text.contains("Highlights: Good week (AI-estimated)"))
        assertTrue(text.contains("User comments: Felt great (user-entered)"))
    }
}
