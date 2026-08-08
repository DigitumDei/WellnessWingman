package com.wellnesswingman.domain.llm

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeeklySummary
import com.wellnesswingman.data.model.WeightRecord
import com.wellnesswingman.data.model.llm.ToolCall
import com.wellnesswingman.data.model.polar.PolarDailyActivity
import com.wellnesswingman.data.model.polar.PolarMetricFamily
import com.wellnesswingman.data.model.polar.PolarNightlyRecharge
import com.wellnesswingman.data.model.polar.PolarSleepResult
import com.wellnesswingman.data.model.polar.PolarSyncCheckpoint
import com.wellnesswingman.data.model.polar.PolarTrainingSession
import com.wellnesswingman.data.model.polar.PolarUserProfile
import com.wellnesswingman.data.model.polar.StoredPolarActivity
import com.wellnesswingman.data.model.polar.StoredPolarNightlyRecharge
import com.wellnesswingman.data.model.polar.StoredPolarSleepResult
import com.wellnesswingman.data.model.polar.StoredPolarTrainingSession
import com.wellnesswingman.data.model.polar.StoredPolarUserProfile
import com.wellnesswingman.data.model.llm.ToolDefinition
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.data.repository.NutritionalProfileRepository
import com.wellnesswingman.data.repository.PolarSyncRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeeklySummaryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import com.wellnesswingman.domain.polar.PolarInsightService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToolRegistryTest {

    // region -- Date-ranged access --

    private val fixedToday = LocalDate(2026, 8, 8)
    private val fixedNow = LocalDateTime(2026, 8, 8, 12, 0).toInstant(TimeZone.UTC)
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedNow
    }

    private fun instantOn(year: Int, month: Int, day: Int, hour: Int = 12): Instant =
        LocalDateTime(year, month, day, hour, 0).toInstant(TimeZone.UTC)

    private fun mealEntry(id: Long, at: Instant, calories: Double, note: String) =
        TrackedEntry(
            entryId = id,
            entryType = EntryType.MEAL,
            capturedAt = at,
            processingStatus = ProcessingStatus.COMPLETED,
            userNotes = note
        )

    private fun mealAnalysis(entryId: Long, at: Instant, calories: Double) = EntryAnalysis(
        entryId = entryId,
        capturedAt = at,
        insightsJson = """{"mealAnalysis":{"nutrition":{"totalCalories":$calories,"protein":30.0}}}"""
    )

    /**
     * Forty meals across six weeks: far more than the ten-entry reach of get_recent_entries,
     * which is exactly the situation where "what did I eat two weeks ago" used to be unanswerable.
     */
    private fun sixWeeksOfMeals(): Pair<List<TrackedEntry>, Map<Long, EntryAnalysis>> {
        val entries = mutableListOf<TrackedEntry>()
        val analyses = mutableMapOf<Long, EntryAnalysis>()
        var id = 1L

        for (dayOffset in 0 until 42) {
            val date = LocalDate(2026, 8, 8).minus(dayOffset, DateTimeUnit.DAY)
            val at = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 12, 0)
                .toInstant(TimeZone.UTC)
            entries += mealEntry(id, at, 500.0, "meal on $date")
            analyses[id] = mealAnalysis(id, at, 500.0)
            id++
        }
        return entries to analyses
    }

    private fun rangedRegistry(
        entries: List<TrackedEntry> = emptyList(),
        analyses: Map<Long, EntryAnalysis> = emptyMap(),
        dailySummaries: List<DailySummary> = emptyList()
    ) = ToolRegistry(
        trackedEntryRepository = FakeRangedTrackedEntryRepository(entries),
        entryAnalysisRepository = FakeEntryAnalysisRepository(analyses),
        weightHistoryRepository = FakeWeightHistoryRepository(),
        appSettingsRepository = FakeAppSettingsRepository(),
        nutritionalProfileRepository = FakeNutritionalProfileRepository(),
        dailySummaryRepository = FakeDailySummaryRepository(dailySummaries),
        clock = fixedClock,
        timeZoneProvider = { TimeZone.UTC }
    )

    private suspend fun ToolRegistry.call(
        name: String,
        arguments: JsonObject = buildJsonObject { }
    ): JsonObject {
        val result = execute(ToolCall(id = "call-1", name = name, arguments = arguments))
        assertFalse(result.isError, "Tool $name errored: ${result.content}")
        return assertIs<JsonObject>(result.content)
    }

    @Test
    fun `get_entries reaches a focused period two weeks back`() = runTest {
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(entries, analyses)

        val content = registry.call("get_entries", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-26"))
        })

        assertEquals(7, content["totalMatching"]?.jsonPrimitive?.int)
        val returned = assertIs<JsonArray>(content["entries"])
        assertEquals(7, returned.size)
        // Every returned entry falls inside the requested week, and none leak in from elsewhere.
        returned.forEach { element ->
            val capturedAt = assertIs<JsonObject>(element)["capturedAt"]!!.jsonPrimitive.content
            assertTrue(
                capturedAt >= "2026-07-20" && capturedAt < "2026-07-27",
                "Entry outside the requested window: $capturedAt"
            )
        }
    }

    @Test
    fun `get_recent_entries cannot reach that same period`() = runTest {
        // The limitation this work exists to remove, pinned so it cannot quietly return.
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(entries, analyses)

        val content = registry.call("get_recent_entries", buildJsonObject {
            put("limit", JsonPrimitive(10))
        })

        val returned = assertIs<JsonArray>(content["entries"])
        val oldest = returned.minOf { assertIs<JsonObject>(it)["capturedAt"]!!.jsonPrimitive.content }
        assertTrue(
            oldest > "2026-07-27",
            "Recent-entries reach should stop well short of two weeks back, got $oldest"
        )
    }

    @Test
    fun `get_entries pages through a large range and says there is more`() = runTest {
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(entries, analyses)

        val firstPage = registry.call("get_entries", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-01"))
            put("endDate", JsonPrimitive("2026-08-08"))
            put("limit", JsonPrimitive(10))
        })

        assertEquals(39, firstPage["totalMatching"]?.jsonPrimitive?.int)
        assertEquals(10, firstPage["returned"]?.jsonPrimitive?.int)
        assertTrue(firstPage["truncated"]!!.jsonPrimitive.boolean, "Must admit it truncated")
        assertEquals(10, firstPage["nextOffset"]?.jsonPrimitive?.int)

        val secondPage = registry.call("get_entries", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-01"))
            put("endDate", JsonPrimitive("2026-08-08"))
            put("limit", JsonPrimitive(10))
            put("offset", JsonPrimitive(10))
        })

        assertEquals(10, secondPage["returned"]?.jsonPrimitive?.int)
        // Pages must not overlap.
        val firstIds = assertIs<JsonArray>(firstPage["entries"])
            .map { assertIs<JsonObject>(it)["entryId"]!!.jsonPrimitive.int }
        val secondIds = assertIs<JsonArray>(secondPage["entries"])
            .map { assertIs<JsonObject>(it)["entryId"]!!.jsonPrimitive.int }
        assertTrue(firstIds.intersect(secondIds.toSet()).isEmpty())
    }

    @Test
    fun `a complete result is not marked truncated`() = runTest {
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(entries, analyses)

        val content = registry.call("get_entries", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-26"))
        })

        assertFalse(content["truncated"]!!.jsonPrimitive.boolean)
        assertEquals(JsonNull, content["nextOffset"])
    }

    @Test
    fun `get_daily_overview summarises each day in the window`() = runTest {
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(
            entries,
            analyses,
            dailySummaries = listOf(
                DailySummary(
                    summaryDate = LocalDate(2026, 7, 22),
                    highlights = "Solid day",
                    recommendations = "Keep going"
                )
            )
        )

        val content = registry.call("get_daily_overview", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-26"))
        })

        val days = assertIs<JsonArray>(content["days"])
        assertEquals(7, days.size, "Every day in the window is listed, including empty ones")

        val day22 = days.map { assertIs<JsonObject>(it) }
            .single { it["date"]!!.jsonPrimitive.content == "2026-07-22" }
        assertEquals(1, day22["totalEntries"]?.jsonPrimitive?.int)
        assertEquals(500.0, day22["calories"]?.jsonPrimitive?.double)
        assertTrue(day22["hasDailySummary"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `a day with no data omits nutrition rather than reporting zero`() = runTest {
        val registry = rangedRegistry(
            entries = listOf(mealEntry(1L, instantOn(2026, 7, 20), 500.0, "lunch")),
            analyses = mapOf(1L to mealAnalysis(1L, instantOn(2026, 7, 20), 500.0))
        )

        val content = registry.call("get_daily_overview", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-21"))
        })

        val days = assertIs<JsonArray>(content["days"]).map { assertIs<JsonObject>(it) }
        val emptyDay = days.single { it["date"]!!.jsonPrimitive.content == "2026-07-21" }

        // "No calories recorded" and "ate zero calories" are different claims.
        assertEquals(null, emptyDay["calories"])
        assertEquals(0, emptyDay["totalEntries"]?.jsonPrimitive?.int)
    }

    @Test
    fun `get_nutrition_totals aggregates a period without returning every meal`() = runTest {
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(entries, analyses)

        val content = registry.call("get_nutrition_totals", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-26"))
        })

        assertEquals(7, content["mealsCounted"]?.jsonPrimitive?.int)
        val totals = assertIs<JsonObject>(content["totals"])
        assertEquals(3500.0, totals["calories"]?.jsonPrimitive?.double)

        val average = assertIs<JsonObject>(content["dailyAverage"])
        assertEquals(500.0, average["calories"]?.jsonPrimitive?.double)
        // The point of the tool: no per-meal records in the payload.
        assertEquals(null, content["entries"])
    }

    @Test
    fun `get_nutrition_totals can break a period down per day`() = runTest {
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(entries, analyses)

        val content = registry.call("get_nutrition_totals", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-26"))
            put("groupBy", JsonPrimitive("day"))
        })

        val buckets = assertIs<JsonArray>(content["buckets"])
        assertEquals(7, buckets.size)
        assertEquals(
            500.0,
            assertIs<JsonObject>(assertIs<JsonObject>(buckets.first())["totals"])["calories"]
                ?.jsonPrimitive?.double
        )
    }

    @Test
    fun `an over-wide range is clamped and the result says so`() = runTest {
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(entries, analyses)

        val content = registry.call("get_entries", buildJsonObject {
            put("startDate", JsonPrimitive("2020-01-01"))
            put("endDate", JsonPrimitive("2026-08-08"))
        })

        val range = assertIs<JsonObject>(content["range"])
        assertTrue(range["clamped"]!!.jsonPrimitive.boolean)
        assertEquals(ToolRegistry.MAX_DETAIL_DAYS, range["days"]?.jsonPrimitive?.int)
    }

    @Test
    fun `an unparseable date is rejected rather than silently defaulted`() = runTest {
        val registry = rangedRegistry()

        val result = registry.execute(
            ToolCall(
                id = "call-1",
                name = "get_entries",
                arguments = buildJsonObject { put("startDate", JsonPrimitive("two weeks ago")) }
            )
        )

        assertTrue(result.isError, "A date the tool cannot parse must not resolve to a default window")
    }

    @Test
    fun `get_data_availability reports today and the span of stored data`() = runTest {
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(entries, analyses)

        val content = registry.call("get_data_availability")

        assertEquals("2026-08-08", content["today"]?.jsonPrimitive?.content)
        assertEquals(TimeZone.UTC.id, content["timeZone"]?.jsonPrimitive?.content)
        val entriesInfo = assertIs<JsonObject>(content["entries"])
        assertEquals(42, entriesInfo["total"]?.jsonPrimitive?.int)
        assertEquals("2026-06-28", entriesInfo["earliestDate"]?.jsonPrimitive?.content)
        assertEquals("2026-08-08", entriesInfo["latestDate"]?.jsonPrimitive?.content)
    }

    @Test
    fun `tools whose data source is absent are not advertised`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeRangedTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
            clock = fixedClock,
            timeZoneProvider = { TimeZone.UTC }
        )

        val names = registry.definitions().map { it.name }

        // An unavailable capability should be invisible, not advertised then broken.
        assertFalse(names.contains("get_daily_summaries"))
        assertFalse(names.contains("get_weekly_summaries"))
        assertFalse(names.contains("get_polar_context"))
        assertTrue(names.contains("get_entries"))
        assertTrue(names.contains("get_daily_overview"))
    }

    @Test
    fun `entry list is compact unless full analysis is asked for`() = runTest {
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(entries, analyses)

        val compact = registry.call("get_entries", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-20"))
        })
        val compactEntry = assertIs<JsonObject>(assertIs<JsonArray>(compact["entries"]).single())
        assertEquals(null, compactEntry["latestInsightsJson"])

        val detailed = registry.call("get_entries", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-20"))
            put("includeAnalysis", JsonPrimitive(true))
        })
        val detailedEntry = assertIs<JsonObject>(assertIs<JsonArray>(detailed["entries"]).single())
        assertTrue(detailedEntry.containsKey("latestInsightsJson"))
    }

    @Test
    fun `get_entry_details returns the full analysis for chosen entries`() = runTest {
        val at = instantOn(2026, 7, 22)
        val registry = rangedRegistry(
            entries = listOf(mealEntry(7L, at, 500.0, "risotto")),
            analyses = mapOf(7L to mealAnalysis(7L, at, 500.0))
        )

        val content = registry.call("get_entry_details", buildJsonObject {
            put("entryIds", JsonArray(listOf(JsonPrimitive(7))))
        })

        assertEquals(1, content["requested"]?.jsonPrimitive?.int)
        assertFalse(content["truncated"]!!.jsonPrimitive.boolean)
        val entry = assertIs<JsonObject>(assertIs<JsonArray>(content["entries"]).single())
        assertEquals(7, entry["entryId"]?.jsonPrimitive?.int)
        assertTrue(entry.containsKey("latestInsightsJson"))
    }

    @Test
    fun `get_entry_details skips ids that do not exist`() = runTest {
        val at = instantOn(2026, 7, 22)
        val registry = rangedRegistry(
            entries = listOf(mealEntry(7L, at, 500.0, "risotto")),
            analyses = mapOf(7L to mealAnalysis(7L, at, 500.0))
        )

        val content = registry.call("get_entry_details", buildJsonObject {
            put("entryIds", JsonArray(listOf(JsonPrimitive(7), JsonPrimitive(999))))
        })

        assertEquals(2, content["requested"]?.jsonPrimitive?.int)
        assertEquals(1, assertIs<JsonArray>(content["entries"]).size)
    }

    @Test
    fun `get_entry_details requires at least one id`() = runTest {
        val registry = rangedRegistry()

        val result = registry.execute(
            ToolCall(
                id = "call-1",
                name = "get_entry_details",
                arguments = buildJsonObject { put("entryIds", JsonArray(emptyList())) }
            )
        )

        assertTrue(result.isError)
    }

    @Test
    fun `get_entry_details caps how many entries one call can pull`() = runTest {
        val entries = (1L..30L).map { mealEntry(it, instantOn(2026, 7, 22), 500.0, "meal $it") }
        val registry = rangedRegistry(entries)

        val content = registry.call("get_entry_details", buildJsonObject {
            put("entryIds", JsonArray(entries.map { JsonPrimitive(it.entryId) }))
        })

        assertEquals(30, content["requested"]?.jsonPrimitive?.int)
        assertTrue(content["truncated"]!!.jsonPrimitive.boolean)
        assertEquals(
            ToolRegistry.MAX_ENTRY_DETAILS,
            assertIs<JsonArray>(content["entries"]).size
        )
    }

    @Test
    fun `get_daily_summaries returns summaries and user comments in range`() = runTest {
        val registry = rangedRegistry(
            dailySummaries = listOf(
                DailySummary(
                    summaryDate = LocalDate(2026, 7, 22),
                    highlights = "Balanced day",
                    recommendations = "More fibre",
                    userComments = "Felt sluggish"
                ),
                DailySummary(
                    summaryDate = LocalDate(2026, 6, 1),
                    highlights = "Out of range",
                    recommendations = "Ignore me"
                )
            )
        )

        val content = registry.call("get_daily_summaries", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-26"))
        })

        assertEquals(1, content["count"]?.jsonPrimitive?.int)
        val summary = assertIs<JsonObject>(assertIs<JsonArray>(content["summaries"]).single())
        assertEquals("2026-07-22", summary["date"]?.jsonPrimitive?.content)
        assertEquals("Balanced day", summary["highlights"]?.jsonPrimitive?.content)
        assertEquals("Felt sluggish", summary["userComments"]?.jsonPrimitive?.content)
    }

    @Test
    fun `get_weekly_summaries returns weeks overlapping the range`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeRangedTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
            weeklySummaryRepository = FakeWeeklySummaryRepository(
                listOf(
                    WeeklySummary(
                        weekStartDate = LocalDate(2026, 7, 20),
                        highlights = "Consistent week",
                        recommendations = "Keep it up",
                        mealCount = 18,
                        exerciseCount = 3,
                        totalEntries = 21
                    )
                )
            ),
            clock = fixedClock,
            timeZoneProvider = { TimeZone.UTC }
        )

        val content = registry.call("get_weekly_summaries", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-01"))
            put("endDate", JsonPrimitive("2026-08-01"))
        })

        assertEquals(1, content["count"]?.jsonPrimitive?.int)
        val week = assertIs<JsonObject>(assertIs<JsonArray>(content["summaries"]).single())
        assertEquals("2026-07-20", week["weekStartDate"]?.jsonPrimitive?.content)
        assertEquals(18, week["mealCount"]?.jsonPrimitive?.int)
    }

    @Test
    fun `get_polar_context returns measured wearable days`() = runTest {
        val date = LocalDate(2026, 7, 22)
        val registry = ToolRegistry(
            trackedEntryRepository = FakeRangedTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(polarConnected = true),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
            polarInsightService = PolarInsightService(
                FakePolarSyncRepository(
                    activities = listOf(
                        StoredPolarActivity(
                            1, "activity:2026-07-22", "Polar", date, null, fixedNow,
                            PolarDailyActivity("2026-07-22", 9120, "00:00:00", 61000, listOf(9120))
                        )
                    )
                )
            ),
            clock = fixedClock,
            timeZoneProvider = { TimeZone.UTC }
        )

        val content = registry.call("get_polar_context", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-26"))
        })

        assertTrue(content["polarConnected"]!!.jsonPrimitive.boolean)
        assertEquals(1, content["daysWithData"]?.jsonPrimitive?.int)
        val day = assertIs<JsonObject>(assertIs<JsonArray>(content["days"]).single())
        assertEquals("2026-07-22", day["date"]?.jsonPrimitive?.content)
        assertTrue(
            assertIs<JsonArray>(day["details"]).toString().contains("9120"),
            "Expected the measured step count in the detail lines"
        )
    }

    @Test
    fun `get_polar_context says so plainly when Polar is not connected`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeRangedTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(polarConnected = false),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
            polarInsightService = PolarInsightService(FakePolarSyncRepository()),
            clock = fixedClock,
            timeZoneProvider = { TimeZone.UTC }
        )

        val content = registry.call("get_polar_context", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-26"))
        })

        // Not connected is different from connected-with-no-data, and must not read as
        // "you did nothing that week".
        assertFalse(content["polarConnected"]!!.jsonPrimitive.boolean)
        assertEquals(0, assertIs<JsonArray>(content["days"]).size)
    }

    @Test
    fun `get_weight_history accepts an explicit date range`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeRangedTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(
                listOf(
                    WeightRecord(
                        weightRecordId = 1L,
                        weightValue = 80.5,
                        weightUnit = "kg",
                        source = "manual",
                        recordedAt = instantOn(2026, 7, 22)
                    )
                )
            ),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(),
            clock = fixedClock,
            timeZoneProvider = { TimeZone.UTC }
        )

        val content = registry.call("get_weight_history", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-20"))
            put("endDate", JsonPrimitive("2026-07-26"))
        })

        val range = assertIs<JsonObject>(content["range"])
        assertEquals("2026-07-20", range["startDate"]?.jsonPrimitive?.content)
        assertEquals("2026-07-26", range["endDate"]?.jsonPrimitive?.content)
        assertEquals(1, content["count"]?.jsonPrimitive?.int)
        // The relative-days shape is not used when a range is given.
        assertEquals(null, content["days"])
    }

    @Test
    fun `get_nutrition_totals can group a period by week`() = runTest {
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(entries, analyses)

        val content = registry.call("get_nutrition_totals", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-13"))
            put("endDate", JsonPrimitive("2026-07-26"))
            put("groupBy", JsonPrimitive("week"))
        })

        val buckets = assertIs<JsonArray>(content["buckets"])
        assertEquals(2, buckets.size)
        buckets.forEach { bucket ->
            assertTrue(assertIs<JsonObject>(bucket).containsKey("weekStartDate"))
        }
    }

    @Test
    fun `reversed dates are exchanged rather than returning nothing`() = runTest {
        val (entries, analyses) = sixWeeksOfMeals()
        val registry = rangedRegistry(entries, analyses)

        val content = registry.call("get_entries", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-26"))
            put("endDate", JsonPrimitive("2026-07-20"))
        })

        val range = assertIs<JsonObject>(content["range"])
        assertTrue(range["swapped"]!!.jsonPrimitive.boolean)
        assertEquals(7, content["totalMatching"]?.jsonPrimitive?.int)
    }

    @Test
    fun `entry type filter narrows a ranged query`() = runTest {
        val at = instantOn(2026, 7, 22)
        val registry = rangedRegistry(
            entries = listOf(
                mealEntry(1L, at, 500.0, "lunch"),
                TrackedEntry(
                    entryId = 2L,
                    entryType = EntryType.EXERCISE,
                    capturedAt = at,
                    processingStatus = ProcessingStatus.COMPLETED,
                    userNotes = "run"
                )
            )
        )

        val content = registry.call("get_entries", buildJsonObject {
            put("startDate", JsonPrimitive("2026-07-22"))
            put("endDate", JsonPrimitive("2026-07-22"))
            put("entryTypes", JsonArray(listOf(JsonPrimitive("Exercise"))))
        })

        assertEquals(1, content["totalMatching"]?.jsonPrimitive?.int)
        val entry = assertIs<JsonObject>(assertIs<JsonArray>(content["entries"]).single())
        assertEquals("Exercise", entry["entryType"]?.jsonPrimitive?.content)
    }

    // endregion

    @Test
    fun `built in recent entries tool returns latest entry with analysis`() = runTest {
        val now = Clock.System.now()
        val entryAnalysisRepository = FakeEntryAnalysisRepository(
            mapOf(
                2L to EntryAnalysis(
                    entryId = 2L,
                    capturedAt = now,
                    insightsJson = """{"summary":"Tempo run"}"""
                )
            )
        )
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(
                listOf(
                    TrackedEntry(
                        entryId = 1L,
                        entryType = EntryType.MEAL,
                        capturedAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 3_600_000L),
                        processingStatus = ProcessingStatus.COMPLETED,
                        userNotes = "salad"
                    ),
                    TrackedEntry(
                        entryId = 2L,
                        entryType = EntryType.EXERCISE,
                        capturedAt = now,
                        processingStatus = ProcessingStatus.COMPLETED,
                        userNotes = "run",
                        dataPayload = """{"duration":45}"""
                    )
                )
            ),
            entryAnalysisRepository = entryAnalysisRepository,
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(
            ToolCall(
                name = "get_recent_entries",
                arguments = buildJsonObject {
                    put("limit", JsonPrimitive(1))
                }
            )
        )

        assertFalse(result.isError)
        val payload = result.content as JsonObject
        val entries = payload["entries"]!!.toString()
        assertTrue(entries.contains("\"entryId\":2"))
        assertTrue(entries.contains("Tempo run"))
        assertTrue(entries.contains("\"dataPayload\":{\"duration\":45}"))
        assertTrue(entries.contains("\"latestInsightsJson\":{\"summary\":\"Tempo run\"}"))
        assertEquals(1, entryAnalysisRepository.getAllAnalysesCalls)
        assertEquals(0, entryAnalysisRepository.getLatestAnalysisCalls)
        // Ten without the optional summary and Polar sources; see
        // `tools whose data source is absent are not advertised`.
        assertEquals(10, registry.definitions().size)
    }

    @Test
    fun `built in recent entries tool ignores invalid entry type filter`() = runTest {
        val now = Clock.System.now()
        val trackedEntryRepository = FakeTrackedEntryRepository(
            listOf(
                TrackedEntry(
                    entryId = 1L,
                    entryType = EntryType.MEAL,
                    capturedAt = now,
                    processingStatus = ProcessingStatus.COMPLETED
                ),
                TrackedEntry(
                    entryId = 2L,
                    entryType = EntryType.EXERCISE,
                    capturedAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 1_000L),
                    processingStatus = ProcessingStatus.COMPLETED
                )
            )
        )
        val registry = ToolRegistry(
            trackedEntryRepository = trackedEntryRepository,
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(
            ToolCall(
                name = "get_recent_entries",
                arguments = buildJsonObject {
                    put("entryType", JsonPrimitive("NotARealType"))
                }
            )
        )

        val payload = assertIs<JsonObject>(result.content)
        assertFalse(result.isError)
        assertEquals(null, trackedEntryRepository.lastRequestedEntryType)
        assertTrue(payload["entries"].toString().contains("\"entryId\":1"))
        assertTrue(payload["entries"].toString().contains("\"entryId\":2"))
    }

    @Test
    fun `execute rethrows cancellation exceptions`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        registry.register(
            definition = ToolDefinition(
                name = "cancel_tool",
                description = "Throws cancellation.",
                parametersSchema = buildJsonObject { put("type", JsonPrimitive("object")) }
            )
        ) {
            throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> {
            registry.execute(ToolCall(name = "cancel_tool"))
        }
    }

    @Test
    fun `unknown tool returns error result`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(ToolCall(name = "missing_tool"))

        assertTrue(result.isError)
        assertEquals("missing_tool", result.name)
        assertTrue(result.content.toString().contains("not registered"))
    }

    @Test
    fun `built in user profile tool returns structured fields`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(ToolCall(name = "get_user_profile"))

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        assertEquals("male", payload["sex"]?.toString()?.trim('"'))
        assertEquals("1990-01-01", payload["dateOfBirth"]?.toString()?.trim('"'))
        assertEquals("moderate", payload["activityLevel"]?.toString()?.trim('"'))
    }

    @Test
    fun `built in weight history tool returns bounded records`() = runTest {
        val now = Clock.System.now()
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(
                records = listOf(
                    WeightRecord(
                        weightRecordId = 1L,
                        weightValue = 80.5,
                        weightUnit = "kg",
                        source = "manual",
                        recordedAt = now
                    )
                )
            ),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(
            ToolCall(
                name = "get_weight_history",
                arguments = buildJsonObject {
                    put("days", JsonPrimitive(365))
                }
            )
        )

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        assertEquals("90", payload["days"]?.toString())
        assertTrue(payload["records"].toString().contains("80.5"))
    }

    @Test
    fun `custom registration dispatches handler`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        registry.register(
            definition = ToolDefinition(
                name = "echo_tool",
                description = "Returns the provided text.",
                parametersSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                }
            )
        ) { call ->
            com.wellnesswingman.data.model.llm.ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("echo", call.arguments["text"] ?: JsonPrimitive(""))
                }
            )
        }

        val result = registry.execute(
            ToolCall(
                name = "echo_tool",
                arguments = buildJsonObject {
                    put("text", JsonPrimitive("hello"))
                }
            )
        )

        assertFalse(result.isError)
        assertEquals("""{"echo":"hello"}""", result.content.toString())
    }

    @Test
    fun `list nutritional profiles tool returns names and aliases without nutrition`() = runTest {
        val now = Clock.System.now()
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(
                listOf(
                    NutritionalProfile(
                        profileId = 7L,
                        externalId = "quest-bar",
                        primaryName = "Quest Protein Bar",
                        aliases = listOf("protein bar", "quest bar"),
                        servingSize = "1 bar",
                        measurementSize = "1 tbsp = 15 g",
                        calories = 190.0,
                        protein = 21.0,
                        carbohydrates = 22.0,
                        fat = 7.0,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            )
        )

        val result = registry.execute(
            ToolCall(
                name = "list_nutritional_profiles"
            )
        )

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        val profiles = assertIs<JsonArray>(payload["profiles"])
        assertEquals(1, profiles.size)
        assertTrue(profiles.toString().contains("Quest Protein Bar"))
        assertTrue(profiles.toString().contains("protein bar"))
        assertFalse(profiles.toString().contains("totalCalories"))
    }

    @Test
    fun `list nutritional profiles tool returns empty list when no profiles exist`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(ToolCall(name = "list_nutritional_profiles"))

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        assertEquals("[]", payload["profiles"]?.toString())
    }

    @Test
    fun `get nutritional profiles tool returns exact profiles for requested ids`() = runTest {
        val now = Clock.System.now()
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository(
                listOf(
                    NutritionalProfile(
                        profileId = 7L,
                        externalId = "quest-bar",
                        primaryName = "Quest Protein Bar",
                        aliases = listOf("protein bar", "quest bar"),
                        servingSize = "1 bar",
                        measurementSize = "1 tbsp = 15 g",
                        calories = 190.0,
                        protein = 21.0,
                        carbohydrates = 22.0,
                        fat = 7.0,
                        createdAt = now,
                        updatedAt = now
                    ),
                    NutritionalProfile(
                        profileId = 9L,
                        externalId = "fairlife-shake",
                        primaryName = "Fairlife Core Power",
                        aliases = listOf("protein shake"),
                        servingSize = "1 bottle",
                        calories = 170.0,
                        protein = 26.0,
                        carbohydrates = 8.0,
                        fat = 4.0,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            )
        )

        val result = registry.execute(
            ToolCall(
                name = "get_nutritional_profiles",
                arguments = buildJsonObject {
                    put(
                        "profileIds",
                        JsonArray(
                            listOf(
                                JsonPrimitive(9),
                                JsonPrimitive(7),
                                JsonPrimitive(999)
                            )
                        )
                    )
                }
            )
        )

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        assertEquals("[9,7,999]", payload["profileIds"]?.toString())
        val profiles = assertIs<JsonArray>(payload["profiles"])
        assertEquals(2, profiles.size)
        assertTrue(profiles.toString().contains("Fairlife Core Power"))
        assertTrue(profiles.toString().contains("Quest Protein Bar"))
        assertTrue(profiles.toString().contains("\"source\":\"exact\""))
        assertTrue(profiles.toString().contains("\"measurementSize\":\"1 tbsp = 15 g\""))
    }

    @Test
    fun `get nutritional profiles tool requires profile ids`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(
            ToolCall(
                name = "get_nutritional_profiles"
            )
        )

        assertTrue(result.isError)
        assertEquals("\"profileIds is required\"", result.content.toString())
    }

    @Test
    fun `get nutritional profiles tool returns empty profiles when ids do not exist`() = runTest {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val result = registry.execute(
            ToolCall(
                name = "get_nutritional_profiles",
                arguments = buildJsonObject {
                    put("profileIds", JsonArray(listOf(JsonPrimitive(9999999999L))))
                }
            )
        )

        assertFalse(result.isError)
        val payload = assertIs<JsonObject>(result.content)
        assertEquals("[9999999999]", payload["profileIds"]?.toString())
        assertEquals("[]", payload["profiles"]?.toString())
    }

    @Test
    fun `get nutritional profiles schema marks profile ids as required`() {
        val registry = ToolRegistry(
            trackedEntryRepository = FakeTrackedEntryRepository(),
            entryAnalysisRepository = FakeEntryAnalysisRepository(),
            weightHistoryRepository = FakeWeightHistoryRepository(),
            appSettingsRepository = FakeAppSettingsRepository(),
            nutritionalProfileRepository = FakeNutritionalProfileRepository()
        )

        val definition = registry.definitions().first { it.name == "get_nutritional_profiles" }
        val required = assertIs<JsonArray>(assertIs<JsonObject>(definition.parametersSchema)["required"])
        assertEquals("[\"profileIds\"]", required.toString())
    }

    /**
     * Unlike [FakeTrackedEntryRepository], this honours the millisecond bounds. A range test
     * against a fake that ignores its range would pass no matter what the tool did.
     */
    private class FakeRangedTrackedEntryRepository(
        private val entries: List<TrackedEntry> = emptyList()
    ) : TrackedEntryRepository {
        override suspend fun getAllEntries(): List<TrackedEntry> = entries
        override suspend fun getRecentEntries(limit: Int, entryType: EntryType?): List<TrackedEntry> =
            entries
                .filter { entryType == null || it.entryType == entryType }
                .sortedByDescending { it.capturedAt }
                .take(limit)
        override fun observeAllEntries(): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntryById(id: Long): TrackedEntry? = entries.find { it.entryId == id }
        override suspend fun getEntryByExternalId(externalId: String): TrackedEntry? = null
        override suspend fun getEntryByBlobPath(blobPath: String): TrackedEntry? = null
        override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long): List<TrackedEntry> =
            entries.filter {
                val at = it.capturedAt.toEpochMilliseconds()
                at >= startMillis && at < endMillis
            }.sortedByDescending { it.capturedAt }
        override suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry> = entries
        override fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntriesForWeek(startMillis: Long, endMillis: Long): List<TrackedEntry> =
            getEntriesForDay(startMillis, endMillis)
        override suspend fun getEntriesForMonth(startMillis: Long, endMillis: Long): List<TrackedEntry> =
            getEntriesForDay(startMillis, endMillis)
        override suspend fun getEntriesByStatus(status: ProcessingStatus): List<TrackedEntry> =
            entries.filter { it.processingStatus == status }
        override suspend fun getPendingEntries(): List<TrackedEntry> =
            entries.filter { it.processingStatus == ProcessingStatus.PENDING }
        override suspend fun insertEntry(entry: TrackedEntry): Long = 0L
        override suspend fun updateEntryStatus(id: Long, status: ProcessingStatus) {}
        override suspend fun updateEntryType(id: Long, entryType: EntryType) {}
        override suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int) {}
        override suspend fun updateUserNotes(id: Long, notes: String?) {}
        override suspend fun deleteEntry(id: Long) {}
        override suspend fun upsertEntry(entry: TrackedEntry) {}
    }

    private class FakeDailySummaryRepository(
        private val summaries: List<DailySummary> = emptyList()
    ) : DailySummaryRepository {
        override suspend fun getAllSummaries(): List<DailySummary> = summaries
        override suspend fun getSummaryById(id: Long): DailySummary? = null
        override suspend fun getSummaryByExternalId(externalId: String): DailySummary? = null
        override suspend fun getSummaryForDate(date: LocalDate): DailySummary? =
            summaries.find { it.summaryDate == date }
        override suspend fun getSummariesForDateRange(
            startDate: LocalDate,
            endDate: LocalDate
        ): List<DailySummary> =
            summaries.filter { it.summaryDate >= startDate && it.summaryDate <= endDate }
        override suspend fun getRecentSummaries(limit: Long): List<DailySummary> = summaries
        override suspend fun insertSummary(summary: DailySummary): Long = 1L
        override suspend fun updateSummary(id: Long, highlights: String, recommendations: String) {}
        override suspend fun updateSummaryByDate(date: LocalDate, highlights: String, recommendations: String) {}
        override suspend fun updateUserComments(date: LocalDate, comments: String?) {}
        override suspend fun deleteSummary(id: Long) {}
        override suspend fun deleteSummaryByDate(date: LocalDate) {}
        override suspend fun deleteOldSummaries(beforeDate: LocalDate) {}
        override suspend fun upsertSummary(summary: DailySummary) {}
    }

    private class FakeTrackedEntryRepository(
        private val entries: List<TrackedEntry> = emptyList()
    ) : TrackedEntryRepository {
        var lastRequestedEntryType: EntryType? = null

        override suspend fun getAllEntries(): List<TrackedEntry> = entries
        override suspend fun getRecentEntries(limit: Int, entryType: EntryType?): List<TrackedEntry> =
            entries
                .also { lastRequestedEntryType = entryType }
                .filter { entryType == null || it.entryType == entryType }
                .sortedByDescending { it.capturedAt }
                .take(limit)
        override fun observeAllEntries(): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntryById(id: Long): TrackedEntry? = entries.find { it.entryId == id }
        override suspend fun getEntryByExternalId(externalId: String): TrackedEntry? = null
        override suspend fun getEntryByBlobPath(blobPath: String): TrackedEntry? = null
        override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long): List<TrackedEntry> = entries
        override suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry> = entries
        override fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>> = emptyFlow()
        override suspend fun getEntriesForWeek(startMillis: Long, endMillis: Long): List<TrackedEntry> = entries
        override suspend fun getEntriesForMonth(startMillis: Long, endMillis: Long): List<TrackedEntry> = entries
        override suspend fun getEntriesByStatus(status: ProcessingStatus): List<TrackedEntry> = entries.filter { it.processingStatus == status }
        override suspend fun getPendingEntries(): List<TrackedEntry> = entries.filter { it.processingStatus == ProcessingStatus.PENDING }
        override suspend fun insertEntry(entry: TrackedEntry): Long = 0L
        override suspend fun updateEntryStatus(id: Long, status: ProcessingStatus) {}
        override suspend fun updateEntryType(id: Long, entryType: EntryType) {}
        override suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int) {}
        override suspend fun updateUserNotes(id: Long, notes: String?) {}
        override suspend fun deleteEntry(id: Long) {}
        override suspend fun upsertEntry(entry: TrackedEntry) {}
    }

    private class FakeEntryAnalysisRepository(
        private val analyses: Map<Long, EntryAnalysis> = emptyMap()
    ) : EntryAnalysisRepository {
        var getAllAnalysesCalls: Int = 0
        var getLatestAnalysisCalls: Int = 0

        override suspend fun getLatestAnalysisForEntry(entryId: Long): EntryAnalysis? {
            getLatestAnalysisCalls += 1
            return analyses[entryId]
        }

        override suspend fun getAllAnalyses(): List<EntryAnalysis> {
            getAllAnalysesCalls += 1
            return analyses.values.toList()
        }
        override suspend fun getAnalysisById(id: Long): EntryAnalysis? = null
        override suspend fun getAnalysisByExternalId(externalId: String): EntryAnalysis? = null
        override suspend fun getAnalysesForEntry(entryId: Long): List<EntryAnalysis> = listOfNotNull(analyses[entryId])
        override suspend fun insertAnalysis(analysis: EntryAnalysis): Long = 0L
        override suspend fun updateAnalysis(id: Long, insightsJson: String, schemaVersion: String) {}
        override suspend fun deleteAnalysis(id: Long) {}
        override suspend fun deleteAnalysesForEntry(entryId: Long) {}
        override suspend fun upsertAnalysis(analysis: EntryAnalysis) {}
    }

    private class FakeWeightHistoryRepository(
        private val records: List<WeightRecord> = emptyList()
    ) : WeightHistoryRepository {
        override suspend fun addWeightRecord(record: WeightRecord): Long = 0L
        override suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<WeightRecord> = records
        override suspend fun getLatestWeightRecord(): WeightRecord? = records.lastOrNull()
        override suspend fun getAllWeightRecords(): List<WeightRecord> = records
        override suspend fun deleteWeightRecord(recordId: Long) {}
        override suspend fun nullifyRelatedEntryId(entryId: Long) {}
        override suspend fun upsertWeightRecord(record: WeightRecord) {}
    }

    private class FakeNutritionalProfileRepository(
        private val profiles: List<NutritionalProfile> = emptyList()
    ) : NutritionalProfileRepository {
        override fun getAllAsFlow(): Flow<List<NutritionalProfile>> = flowOf(profiles)
        override suspend fun getAll(): List<NutritionalProfile> = profiles
        override suspend fun getById(profileId: Long): NutritionalProfile? = profiles.find { it.profileId == profileId }
        override suspend fun getByExternalId(externalId: String): NutritionalProfile? = profiles.find { it.externalId == externalId }
        override suspend fun searchByName(query: String, limit: Int): List<NutritionalProfile> {
            val normalized = query.lowercase()
            return profiles.filter {
                it.primaryName.lowercase().contains(normalized) ||
                    it.aliases.any { alias -> alias.lowercase().contains(normalized) }
            }.take(limit)
        }
        override suspend fun insert(profile: NutritionalProfile): Long = profile.profileId
        override suspend fun update(profile: NutritionalProfile) {}
        override suspend fun delete(profileId: Long) {}
        override suspend fun upsert(profile: NutritionalProfile) {}
    }

    private class FakeWeeklySummaryRepository(
        private val summaries: List<WeeklySummary> = emptyList()
    ) : WeeklySummaryRepository {
        override suspend fun getAllSummaries(): List<WeeklySummary> = summaries
        override suspend fun getSummaryById(id: Long): WeeklySummary? = null
        override suspend fun getSummaryForWeek(weekStart: LocalDate): WeeklySummary? =
            summaries.find { it.weekStartDate == weekStart }
        override suspend fun getSummariesForDateRange(
            startDate: LocalDate,
            endDate: LocalDate
        ): List<WeeklySummary> =
            summaries.filter { it.weekStartDate >= startDate && it.weekStartDate <= endDate }
        override suspend fun getRecentSummaries(limit: Long): List<WeeklySummary> = summaries
        override suspend fun insertSummary(summary: WeeklySummary): Long = 1L
        override suspend fun updateSummary(summary: WeeklySummary) {}
        override suspend fun updateSummaryByWeek(weekStart: LocalDate, summary: WeeklySummary) {}
        override suspend fun updateUserComments(weekStart: LocalDate, comments: String?) {}
        override suspend fun deleteSummary(id: Long) {}
        override suspend fun deleteSummaryByWeek(weekStart: LocalDate) {}
        override suspend fun deleteOldSummaries(beforeDate: LocalDate) {}
    }

    private class FakePolarSyncRepository(
        private val activities: List<StoredPolarActivity> = emptyList(),
        private val sleepResults: List<StoredPolarSleepResult> = emptyList()
    ) : PolarSyncRepository {
        override suspend fun upsertActivities(activities: List<PolarDailyActivity>, syncedAt: Instant) = 0
        override suspend fun getActivities(
            startDate: LocalDate,
            endDateExclusive: LocalDate
        ): List<StoredPolarActivity> =
            activities.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
        override suspend fun upsertSleepResults(results: List<PolarSleepResult>, syncedAt: Instant) = 0
        override suspend fun getSleepResults(
            startDate: LocalDate,
            endDateExclusive: LocalDate
        ): List<StoredPolarSleepResult> =
            sleepResults.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
        override suspend fun upsertTrainingSessions(sessions: List<PolarTrainingSession>, syncedAt: Instant) = 0
        override suspend fun getTrainingSessions(
            startDate: LocalDate,
            endDateExclusive: LocalDate
        ): List<StoredPolarTrainingSession> = emptyList()
        override suspend fun upsertNightlyRecharge(results: List<PolarNightlyRecharge>, syncedAt: Instant) = 0
        override suspend fun getNightlyRecharge(
            startDate: LocalDate,
            endDateExclusive: LocalDate
        ): List<StoredPolarNightlyRecharge> = emptyList()
        override suspend fun upsertUserProfile(userId: String, profile: PolarUserProfile, syncedAt: Instant) {}
        override suspend fun getUserProfile(userId: String): StoredPolarUserProfile? = null
        override suspend fun getCheckpoint(metricFamily: PolarMetricFamily): PolarSyncCheckpoint? = null
        override suspend fun getAllCheckpoints(): List<PolarSyncCheckpoint> = emptyList()
        override suspend fun updateCheckpoint(checkpoint: PolarSyncCheckpoint) {}
        override suspend fun clearCheckpoint(metricFamily: PolarMetricFamily) {}
        override suspend fun clearAll() {}
    }

    private class FakeAppSettingsRepository(
        private val polarConnected: Boolean = false
    ) : AppSettingsRepository {
        override fun getApiKey(provider: LlmProvider): String? = null
        override fun setApiKey(provider: LlmProvider, apiKey: String) {}
        override fun removeApiKey(provider: LlmProvider) {}
        override fun getSelectedProvider(): LlmProvider = LlmProvider.GEMINI
        override fun setSelectedProvider(provider: LlmProvider) {}
        override fun getModel(provider: LlmProvider): String? = null
        override fun setModel(provider: LlmProvider, model: String) {}
        override fun clear() {}
        override fun getHeight(): Double? = 180.0
        override fun setHeight(height: Double) {}
        override fun getHeightUnit(): String = "cm"
        override fun setHeightUnit(unit: String) {}
        override fun getSex(): String? = "male"
        override fun setSex(sex: String) {}
        override fun getCurrentWeight(): Double? = 80.0
        override fun setCurrentWeight(weight: Double) {}
        override fun getWeightUnit(): String = "kg"
        override fun setWeightUnit(unit: String) {}
        override fun getDateOfBirth(): String? = "1990-01-01"
        override fun setDateOfBirth(dob: String) {}
        override fun getActivityLevel(): String? = "moderate"
        override fun setActivityLevel(level: String) {}
        override fun clearHeight() {}
        override fun clearCurrentWeight() {}
        override fun clearProfileData() {}
        override fun getImageRetentionThresholdDays(): Int = 30
        override fun setImageRetentionThresholdDays(days: Int) {}
        override fun isMorningCheckInEnabled(): Boolean = false
        override fun setMorningCheckInEnabled(enabled: Boolean) {}
        override fun getMorningCheckInTime(): String = "07:00"
        override fun setMorningCheckInTime(time: String) {}
        override fun isEveningCheckInEnabled(): Boolean = false
        override fun setEveningCheckInEnabled(enabled: Boolean) {}
        override fun getEveningCheckInTime(): String = "21:00"
        override fun setEveningCheckInTime(time: String) {}
        override fun getPolarAccessToken(): String? = null
        override fun setPolarAccessToken(token: String) {}
        override fun getPolarRefreshToken(): String? = null
        override fun setPolarRefreshToken(token: String) {}
        override fun getPolarTokenExpiresAt(): Long = 0L
        override fun setPolarTokenExpiresAt(expiresAt: Long) {}
        override fun getPolarUserId(): String? = null
        override fun setPolarUserId(userId: String) {}
        override fun getPendingOAuthState(): String? = null
        override fun setPendingOAuthState(state: String) {}
        override fun getPendingOAuthSessionId(): String? = null
        override fun setPendingOAuthSessionId(sessionId: String) {}
        override fun clearPendingOAuthSession() {}
        override fun clearPolarTokens() {}
        override fun isPolarConnected(): Boolean = polarConnected
    }
}
