package com.wellnesswingman.domain.llm

import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeightRecord
import com.wellnesswingman.data.model.llm.ToolCall
import com.wellnesswingman.data.model.llm.ToolDefinition
import com.wellnesswingman.data.model.llm.ToolResult
import com.wellnesswingman.data.model.NutritionTotals
import com.wellnesswingman.data.model.analysis.CheckInFacets
import com.wellnesswingman.data.model.analysis.MealAnalysisResult
import com.wellnesswingman.data.model.analysis.UnifiedAnalysisResult
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.CheckInAnalysisRepository
import com.wellnesswingman.data.repository.DailyCheckInRepository
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.NutritionalProfileRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeeklySummaryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import com.wellnesswingman.domain.analysis.DailyTotalsCalculator
import com.wellnesswingman.domain.polar.PolarInsightService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.putJsonObject

class ToolRegistry(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val entryAnalysisRepository: EntryAnalysisRepository,
    private val weightHistoryRepository: WeightHistoryRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val nutritionalProfileRepository: NutritionalProfileRepository,
    /**
     * Summary and wearable sources are optional. A tool whose data source is absent is not
     * registered at all, rather than being advertised and then failing when called — an
     * unavailable capability should be invisible to the model, not a broken one.
     *
     * Production wiring in DomainModule supplies all of them.
     */
    private val dailySummaryRepository: DailySummaryRepository? = null,
    private val weeklySummaryRepository: WeeklySummaryRepository? = null,
    private val dailyCheckInRepository: DailyCheckInRepository? = null,
    /**
     * Supplies food mentioned in a check-in but never photographed, so chat-reported nutrition
     * matches what the day screens and the generated summary show. Without it a user who logged
     * food only by talking about it gets one answer from the app and a different one from chat.
     */
    private val checkInAnalysisRepository: CheckInAnalysisRepository? = null,
    private val polarInsightService: PolarInsightService? = null,
    private val dailyTotalsCalculator: DailyTotalsCalculator = DailyTotalsCalculator(),
    private val clock: Clock = Clock.System,
    private val timeZoneProvider: () -> TimeZone = { TimeZone.currentSystemDefault() }
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val definitions = linkedMapOf<String, ToolDefinition>()
    private val handlers = linkedMapOf<String, suspend (ToolCall) -> ToolResult>()

    init {
        registerBuiltIns()
        registerTimeRangedTools()
        registerNutritionalProfileTools()
    }

    private fun timeZone(): TimeZone = timeZoneProvider()

    /**
     * Completed check-in facets across a range, keyed by day.
     *
     * Fetched once per tool call rather than per day: a six-month overview would otherwise issue
     * one query per date. Only completed extractions count — a pending one holds no food yet,
     * which is not the same as a day with none.
     *
     * Degrades to nothing on failure, so chat still answers with photographed food rather than
     * erroring out entirely.
     */
    private suspend fun checkInFacetsByDate(
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<LocalDate, List<CheckInFacets>> {
        val repository = checkInAnalysisRepository ?: return emptyMap()

        return try {
            repository.getAnalysesForDateRange(startDate, endDate)
                .mapNotNull { analysis -> analysis.completedFacets?.let { analysis.checkInDate to it } }
                .groupBy({ it.first }, { it.second })
        } catch (e: Exception) {
            Napier.w("Failed to load check-in facets for tool totals: ${e.message}")
            emptyMap()
        }
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZone()).date

    fun register(
        definition: ToolDefinition,
        handler: suspend (ToolCall) -> ToolResult
    ) {
        definitions[definition.name] = definition
        handlers[definition.name] = handler
    }

    fun definitions(): List<ToolDefinition> = definitions.values.toList()

    suspend fun execute(toolCall: ToolCall): ToolResult {
        Napier.d("Tool call: ${toolCall.name}")

        val handler = handlers[toolCall.name]
            ?: run {
                Napier.w("Tool '${toolCall.name}' is not registered")
                return ToolResult(
                    toolCallId = toolCall.id,
                    name = toolCall.name,
                    content = JsonPrimitive("Tool '${toolCall.name}' is not registered."),
                    isError = true
                )
            }

        return try {
            val result = handler(toolCall)
            if (result.isError) {
                Napier.w("Tool '${toolCall.name}' returned error: ${result.content}")
            } else {
                Napier.d("Tool '${toolCall.name}' completed successfully")
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Tool '${toolCall.name}' threw exception", e)
            ToolResult(
                toolCallId = toolCall.id,
                name = toolCall.name,
                content = JsonPrimitive(e.message ?: "Tool execution failed."),
                isError = true
            )
        }
    }

    private fun registerBuiltIns() {
        register(
            definition = ToolDefinition(
                name = "get_user_profile",
                description = "Get the user's saved profile and preference data for analysis context.",
                parametersSchema = emptyObjectSchema()
            )
        ) { call ->
            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    putNullable("sex", appSettingsRepository.getSex())
                    putNullable("dateOfBirth", appSettingsRepository.getDateOfBirth())
                    putNullable("height", appSettingsRepository.getHeight()?.let(::JsonPrimitive))
                    put("heightUnit", JsonPrimitive(appSettingsRepository.getHeightUnit()))
                    putNullable("currentWeight", appSettingsRepository.getCurrentWeight()?.let(::JsonPrimitive))
                    put("weightUnit", JsonPrimitive(appSettingsRepository.getWeightUnit()))
                    putNullable("activityLevel", appSettingsRepository.getActivityLevel())
                }
            )
        }

        register(
            definition = ToolDefinition(
                name = "get_weight_history",
                description = "Get weight records, either for an explicit date range or for the last N days.",
                parametersSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("startDate") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Inclusive start date as YYYY-MM-DD. Takes precedence over days."))
                        }
                        putJsonObject("endDate") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Inclusive end date as YYYY-MM-DD. Defaults to today."))
                        }
                        putJsonObject("days") {
                            put("type", JsonPrimitive("integer"))
                            put("description", JsonPrimitive("Alternative to a date range: number of days back from today, capped at $MAX_WEIGHT_DAYS. Use startDate/endDate to go further back."))
                        }
                    }
                }
            )
        ) { call ->
            val zone = timeZone()
            val hasExplicitRange = call.arguments["startDate"] != null || call.arguments["endDate"] != null

            if (hasExplicitRange) {
                val window = resolveWindow(call, MAX_WEEKLY_DAYS, defaultSpanDays = 30)
                    ?: return@register invalidRange(call)
                val records = weightHistoryRepository.getWeightHistory(
                    window.startInstant(zone),
                    window.endInstantExclusive(zone)
                )
                return@register ToolResult(
                    toolCallId = call.id,
                    name = call.name,
                    content = buildJsonObject {
                        put("range", rangeJson(window))
                        put("count", JsonPrimitive(records.size))
                        put("records", JsonArray(records.map(::weightRecordJson)))
                    }
                )
            }

            val days = call.arguments["days"]?.jsonPrimitive?.intOrNull
                ?.coerceIn(1, MAX_WEIGHT_DAYS) ?: 30
            val now = clock.now()
            val start = now.minus(days, DateTimeUnit.DAY, zone)
            val records = weightHistoryRepository.getWeightHistory(start, now)
            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("days", JsonPrimitive(days))
                    put("count", JsonPrimitive(records.size))
                    put("records", JsonArray(records.map(::weightRecordJson)))
                }
            )
        }

        register(
            definition = ToolDefinition(
                name = "get_recent_entries",
                description = "Get recent tracked entries and their latest stored analyses for additional context.",
                parametersSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("limit") {
                            put("type", JsonPrimitive("integer"))
                            put("description", JsonPrimitive("Maximum number of entries to return, capped at 10."))
                        }
                        putJsonObject("entryType") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Optional entry type filter such as Meal, Exercise, Sleep, Other, or Unknown."))
                        }
                    }
                }
            )
        ) { call ->
            val limit = call.arguments["limit"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 10) ?: 5
            val entryType = call.arguments["entryType"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            val entries = trackedEntryRepository.getRecentEntries(
                limit = limit,
                entryType = parseEntryTypeOrNull(entryType)
            )
            val latestAnalyses = latestAnalysesByEntryId(entries)

            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("limit", JsonPrimitive(limit))
                    put("entryType", entryType?.let(::JsonPrimitive) ?: JsonNull)
                    put("entries", JsonArray(entries.map { entry ->
                        val latestAnalysis = latestAnalyses[entry.entryId]
                        entryJson(entry, latestAnalysis)
                    }))
                }
            )
        }
    }

    /**
     * Date-ranged access to the user's history.
     *
     * Structured as two tiers on purpose. `get_data_availability` and `get_daily_overview` are
     * cheap enough to scan months, letting the model find *where* the interesting days are; the
     * detail tools then run over a narrowed window. Without that, any question about a past
     * period either misses the data entirely or drags the whole period into context.
     *
     * Every ranged result reports the window actually served, and whether it was clamped or
     * truncated, so partial data is never mistaken for complete data.
     */
    private fun registerTimeRangedTools() {
        register(
            definition = ToolDefinition(
                name = "get_data_availability",
                description = "Get today's date, the user's time zone, and what date ranges actually contain data. Call this first when the user asks about a past period, so date ranges can be chosen correctly.",
                parametersSchema = emptyObjectSchema()
            )
        ) { call ->
            val zone = timeZone()
            val entries = trackedEntryRepository.getAllEntries()
            val entryDates = entries.map { it.capturedAt.toLocalDateTime(zone).date }
            val dailySummaries = dailySummaryRepository?.getAllSummaries().orEmpty()
            val weeklySummaries = weeklySummaryRepository?.getAllSummaries().orEmpty()
            val weightRecords = weightHistoryRepository.getAllWeightRecords()

            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("today", JsonPrimitive(today().toString()))
                    put("timeZone", JsonPrimitive(zone.id))
                    putJsonObject("entries") {
                        put("total", JsonPrimitive(entries.size))
                        put("earliestDate", entryDates.minOrNull()?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                        put("latestDate", entryDates.maxOrNull()?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                        putJsonObject("countByType") {
                            entries.groupingBy { it.entryType.toStorageString() }.eachCount()
                                .forEach { (type, count) -> put(type, JsonPrimitive(count)) }
                        }
                    }
                    putJsonObject("dailySummaries") {
                        put("total", JsonPrimitive(dailySummaries.size))
                        put("earliestDate", dailySummaries.minOfOrNull { it.summaryDate }?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                        put("latestDate", dailySummaries.maxOfOrNull { it.summaryDate }?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                    }
                    putJsonObject("weeklySummaries") {
                        put("total", JsonPrimitive(weeklySummaries.size))
                        put("earliestWeekStart", weeklySummaries.minOfOrNull { it.weekStartDate }?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                        put("latestWeekStart", weeklySummaries.maxOfOrNull { it.weekStartDate }?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                    }
                    putJsonObject("weightRecords") {
                        put("total", JsonPrimitive(weightRecords.size))
                    }
                    dailyCheckInRepository?.let { repository ->
                        val allCheckIns = repository.getAllCheckIns()
                        putJsonObject("checkIns") {
                            put("total", JsonPrimitive(allCheckIns.size))
                            put("earliestDate", allCheckIns.minOfOrNull { it.checkInDate }?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                            put("latestDate", allCheckIns.maxOfOrNull { it.checkInDate }?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                        }
                    }
                    put("polarConnected", JsonPrimitive(appSettingsRepository.isPolarConnected()))
                }
            )
        }

        register(
            definition = ToolDefinition(
                name = "get_daily_overview",
                description = "Get a compact per-day rollup over a date range: how many entries of each type, calories, sleep hours, and whether a daily summary exists. Use this to scan a period cheaply and find which days to look at in detail.",
                parametersSchema = dateRangeSchema(
                    extraDescription = "Defaults to the last ${ToolDateWindows.DEFAULT_SPAN_DAYS} days. Maximum span $MAX_OVERVIEW_DAYS days."
                )
            )
        ) { call ->
            val window = resolveWindow(call, MAX_OVERVIEW_DAYS)
                ?: return@register invalidRange(call)
            val zone = timeZone()

            val entries = trackedEntryRepository
                .getEntriesForDay(window.startMillis(zone), window.endMillisExclusive(zone))
                .filter { it.entryType != EntryType.DAILY_SUMMARY }
            val analyses = latestAnalysesByEntryId(entries)
            val summaryDates = dailySummaryRepository
                ?.getSummariesForDateRange(window.startDate, window.endDate)
                ?.map { it.summaryDate }
                ?.toSet()
                .orEmpty()
            val checkInDates = dailyCheckInRepository
                ?.getCheckInsForDateRange(window.startDate, window.endDate)
                ?.map { it.checkInDate }
                ?.toSet()
                .orEmpty()
            val facetsByDate = checkInFacetsByDate(window.startDate, window.endDate)

            val entriesByDate = entries.groupBy { it.capturedAt.toLocalDateTime(zone).date }

            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("range", rangeJson(window))
                    put("daysWithData", JsonPrimitive(entriesByDate.count { it.value.isNotEmpty() }))
                    put("days", JsonArray(window.eachDate().map { date ->
                        val dayEntries = entriesByDate[date].orEmpty()
                        val totals = dailyTotalsCalculator.calculate(
                            dayEntries.filter { it.entryType == EntryType.MEAL }
                                .map { mealAnalysisFrom(analyses[it.entryId]) },
                            facetsByDate[date].orEmpty()
                        )
                        val sleepHours = dayEntries
                            .filter { it.entryType == EntryType.SLEEP }
                            .mapNotNull { sleepHoursFrom(analyses[it.entryId]) }
                            .takeIf { it.isNotEmpty() }
                            ?.sum()

                        buildJsonObject {
                            put("date", JsonPrimitive(date.toString()))
                            put("totalEntries", JsonPrimitive(dayEntries.size))
                            putJsonObject("countByType") {
                                dayEntries.groupingBy { it.entryType.toStorageString() }.eachCount()
                                    .forEach { (type, count) -> put(type, JsonPrimitive(count)) }
                            }
                            // Omitted rather than zeroed when nothing was logged, so an empty day
                            // reads as "no data" instead of "ate nothing".
                            if (totals.calories > 0.0) {
                                put("calories", JsonPrimitive(totals.calories))
                                put("protein", JsonPrimitive(totals.protein))
                                put("carbohydrates", JsonPrimitive(totals.carbs))
                                put("fat", JsonPrimitive(totals.fat))
                            }
                            sleepHours?.let { put("sleepHours", JsonPrimitive(it)) }
                            put("hasDailySummary", JsonPrimitive(date in summaryDates))
                            // Flagged in the index so the model knows which days are worth a
                            // get_check_ins call, without pulling the text during a wide scan.
                            if (date in checkInDates) put("hasCheckIns", JsonPrimitive(true))
                        }
                    }))
                }
            )
        }

        register(
            definition = ToolDefinition(
                name = "get_entries",
                description = "Get tracked entries within a date range, optionally filtered by type, with paging. Returns compact records by default; set includeAnalysis to true for the full stored analysis.",
                parametersSchema = dateRangeSchema(
                    extraDescription = "Maximum span $MAX_DETAIL_DAYS days."
                ) {
                    putJsonObject("entryTypes") {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("Optional filter, any of Meal, Exercise, Sleep, Other, Unknown."))
                        putJsonObject("items") { put("type", JsonPrimitive("string")) }
                    }
                    putJsonObject("limit") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Maximum entries to return, capped at $MAX_ENTRIES_PER_CALL."))
                    }
                    putJsonObject("offset") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Number of matching entries to skip, for paging through a large range."))
                    }
                    putJsonObject("includeAnalysis") {
                        put("type", JsonPrimitive("boolean"))
                        put("description", JsonPrimitive("Include the full stored analysis for each entry. Costly; prefer get_entry_details for specific entries."))
                    }
                }
            )
        ) { call ->
            val window = resolveWindow(call, MAX_DETAIL_DAYS)
                ?: return@register invalidRange(call)
            val zone = timeZone()

            val requestedTypes = (call.arguments["entryTypes"] as? JsonArray)
                ?.mapNotNull { parseEntryTypeOrNull(it.jsonPrimitive.contentOrNull) }
                ?.toSet()
                .orEmpty()
            val limit = call.arguments["limit"]?.jsonPrimitive?.intOrNull
                ?.coerceIn(1, MAX_ENTRIES_PER_CALL) ?: DEFAULT_ENTRIES_PER_CALL
            val offset = call.arguments["offset"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(0) ?: 0
            val includeAnalysis = call.arguments["includeAnalysis"]?.jsonPrimitive?.booleanOrNull ?: false

            val matching = trackedEntryRepository
                .getEntriesForDay(window.startMillis(zone), window.endMillisExclusive(zone))
                .filter { it.entryType != EntryType.DAILY_SUMMARY }
                .filter { requestedTypes.isEmpty() || it.entryType in requestedTypes }
                .sortedByDescending { it.capturedAt }

            val page = matching.drop(offset).take(limit)
            val analyses = if (includeAnalysis) latestAnalysesByEntryId(page) else emptyMap()

            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("range", rangeJson(window))
                    put("totalMatching", JsonPrimitive(matching.size))
                    put("returned", JsonPrimitive(page.size))
                    put("offset", JsonPrimitive(offset))
                    // Stated explicitly: a silently truncated list reads as a complete one.
                    val hasMore = offset + page.size < matching.size
                    put("truncated", JsonPrimitive(hasMore))
                    put("nextOffset", if (hasMore) JsonPrimitive(offset + page.size) else JsonNull)
                    put("entries", JsonArray(page.map { entry ->
                        if (includeAnalysis) entryJson(entry, analyses[entry.entryId])
                        else compactEntryJson(entry)
                    }))
                }
            )
        }

        register(
            definition = ToolDefinition(
                name = "get_entry_details",
                description = "Get the full stored analysis for specific entries by ID, as surfaced by get_entries or get_daily_overview.",
                parametersSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("entryIds") {
                            put("type", JsonPrimitive("array"))
                            put("description", JsonPrimitive("Entry IDs to fetch, capped at $MAX_ENTRY_DETAILS."))
                            putJsonObject("items") { put("type", JsonPrimitive("integer")) }
                        }
                    }
                    put("required", JsonArray(listOf(JsonPrimitive("entryIds"))))
                }
            )
        ) { call ->
            val requestedIds = (call.arguments["entryIds"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.longOrNull }
                ?.distinct()
                .orEmpty()

            if (requestedIds.isEmpty()) {
                return@register ToolResult(
                    toolCallId = call.id,
                    name = call.name,
                    content = JsonPrimitive("entryIds is required"),
                    isError = true
                )
            }

            val ids = requestedIds.take(MAX_ENTRY_DETAILS)
            val entries = ids.mapNotNull { trackedEntryRepository.getEntryById(it) }

            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("requested", JsonPrimitive(requestedIds.size))
                    put("truncated", JsonPrimitive(requestedIds.size > ids.size))
                    put("entries", JsonArray(entries.map { entry ->
                        entryJson(entry, entryAnalysisRepository.getLatestAnalysisForEntry(entry.entryId))
                    }))
                }
            )
        }

        val dailySummaries = dailySummaryRepository
        if (dailySummaries != null) register(
            definition = ToolDefinition(
                name = "get_daily_summaries",
                description = "Get the generated daily summaries, including the user's own comments, for a date range.",
                parametersSchema = dateRangeSchema(
                    extraDescription = "Maximum span $MAX_DETAIL_DAYS days."
                )
            )
        ) { call ->
            val window = resolveWindow(call, MAX_DETAIL_DAYS)
                ?: return@register invalidRange(call)

            val summaries = dailySummaries
                .getSummariesForDateRange(window.startDate, window.endDate)

            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("range", rangeJson(window))
                    put("count", JsonPrimitive(summaries.size))
                    put("summaries", JsonArray(summaries.map { summary ->
                        buildJsonObject {
                            put("date", JsonPrimitive(summary.summaryDate.toString()))
                            put("highlights", JsonPrimitive(summary.highlights))
                            put("recommendations", JsonPrimitive(summary.recommendations))
                            putNullable("userComments", summary.userComments)
                            putNullable("generatedAt", summary.generatedAt?.toString())
                        }
                    }))
                }
            )
        }

        val weeklySummaries = weeklySummaryRepository
        if (weeklySummaries != null) register(
            definition = ToolDefinition(
                name = "get_weekly_summaries",
                description = "Get generated weekly summaries overlapping a date range, for questions about longer-term trends.",
                parametersSchema = dateRangeSchema(
                    extraDescription = "Maximum span $MAX_WEEKLY_DAYS days."
                )
            )
        ) { call ->
            val window = resolveWindow(call, MAX_WEEKLY_DAYS, defaultSpanDays = 28)
                ?: return@register invalidRange(call)

            val summaries = weeklySummaries
                .getSummariesForDateRange(window.startDate, window.endDate)

            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("range", rangeJson(window))
                    put("count", JsonPrimitive(summaries.size))
                    put("summaries", JsonArray(summaries.map { summary ->
                        buildJsonObject {
                            put("weekStartDate", JsonPrimitive(summary.weekStartDate.toString()))
                            put("highlights", JsonPrimitive(summary.highlights))
                            put("recommendations", JsonPrimitive(summary.recommendations))
                            put("mealCount", JsonPrimitive(summary.mealCount))
                            put("exerciseCount", JsonPrimitive(summary.exerciseCount))
                            put("sleepCount", JsonPrimitive(summary.sleepCount))
                            put("totalEntries", JsonPrimitive(summary.totalEntries))
                            putNullable("userComments", summary.userComments)
                        }
                    }))
                }
            )
        }

        val checkIns = dailyCheckInRepository
        if (checkIns != null) register(
            definition = ToolDefinition(
                name = "get_check_ins",
                description = "Get the user's own morning and evening check-ins for a date range: how they said they slept, how they felt, how the day went, and anything they did not log. This is subjective self-report, not measured data — treat it as their lived experience rather than something to correct.",
                parametersSchema = dateRangeSchema(
                    extraDescription = "Maximum span $MAX_DETAIL_DAYS days."
                )
            )
        ) { call ->
            val window = resolveWindow(call, MAX_DETAIL_DAYS)
                ?: return@register invalidRange(call)

            val found = checkIns.getCheckInsForDateRange(window.startDate, window.endDate)

            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("range", rangeJson(window))
                    put("count", JsonPrimitive(found.size))
                    put("checkIns", JsonArray(found.map { checkIn ->
                        buildJsonObject {
                            put("date", JsonPrimitive(checkIn.checkInDate.toString()))
                            put("slot", JsonPrimitive(checkIn.slot.toStorageString()))
                            put("capturedAt", JsonPrimitive(checkIn.capturedAt.toString()))
                            // The user's own words, unaltered. No mood or energy score is derived
                            // from them: that would turn feel back into measure.
                            put("response", JsonPrimitive(checkIn.responseText))
                            put("inputSource", JsonPrimitive(checkIn.inputSource.toStorageString()))
                        }
                    }))
                }
            )
        }

        val polarInsights = polarInsightService
        if (polarInsights != null) register(
            definition = ToolDefinition(
                name = "get_polar_context",
                description = "Get Polar wearable data (steps, sleep, training sessions, nightly recharge) for a date range. This is measured device data, distinct from what the user logged themselves.",
                parametersSchema = dateRangeSchema(
                    extraDescription = "Maximum span $MAX_DETAIL_DAYS days."
                )
            )
        ) { call ->
            val window = resolveWindow(call, MAX_DETAIL_DAYS)
                ?: return@register invalidRange(call)

            if (!appSettingsRepository.isPolarConnected()) {
                return@register ToolResult(
                    toolCallId = call.id,
                    name = call.name,
                    content = buildJsonObject {
                        put("range", rangeJson(window))
                        put("polarConnected", JsonPrimitive(false))
                        put("days", JsonArray(emptyList()))
                    }
                )
            }

            val contexts = polarInsights
                .getDayContexts(window.startDate, window.endDateExclusive)
                .filter { it.hasData }

            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("range", rangeJson(window))
                    put("polarConnected", JsonPrimitive(true))
                    put("daysWithData", JsonPrimitive(contexts.size))
                    put("days", JsonArray(contexts.map { context ->
                        buildJsonObject {
                            put("date", JsonPrimitive(context.date.toString()))
                            // Reuses the same formatting the daily summary prompt already uses,
                            // so chat and summaries describe wearable data identically.
                            put("details", JsonArray(
                                context.buildPromptLines(includeSleep = true, includeExercise = true)
                                    .map(::JsonPrimitive)
                            ))
                        }
                    }))
                }
            )
        }

        register(
            definition = ToolDefinition(
                name = "get_nutrition_totals",
                description = "Get calorie and macro totals over a date range, optionally broken down per day or per week. Use this for trends and averages instead of fetching every meal.",
                parametersSchema = dateRangeSchema(
                    extraDescription = "Maximum span $MAX_DETAIL_DAYS days."
                ) {
                    putJsonObject("groupBy") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("One of total, day, or week. Defaults to total."))
                    }
                }
            )
        ) { call ->
            val window = resolveWindow(call, MAX_DETAIL_DAYS)
                ?: return@register invalidRange(call)
            val zone = timeZone()
            val groupBy = call.arguments["groupBy"]?.jsonPrimitive?.contentOrNull
                ?.trim()?.lowercase()?.takeIf { it in setOf("total", "day", "week") } ?: "total"

            val mealEntries = trackedEntryRepository
                .getEntriesForDay(window.startMillis(zone), window.endMillisExclusive(zone))
                .filter { it.entryType == EntryType.MEAL }
            val analyses = latestAnalysesByEntryId(mealEntries)

            val facetsByDate = checkInFacetsByDate(window.startDate, window.endDate)

            /**
             * @param dates which days' mentioned food belongs in this bucket. Passed explicitly
             *   rather than derived from [entries], because a day can have mentioned food and no
             *   photographed meal at all — deriving the dates from meals would drop exactly the
             *   food this fix exists to include.
             */
            fun totalsFor(
                entries: List<TrackedEntry>,
                dates: Collection<LocalDate>
            ): NutritionTotals = dailyTotalsCalculator.calculate(
                entries.map { mealAnalysisFrom(analyses[it.entryId]) },
                dates.flatMap { facetsByDate[it].orEmpty() }
            )

            val byDate = mealEntries.groupBy { it.capturedAt.toLocalDateTime(zone).date }

            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("range", rangeJson(window))
                    put("groupBy", JsonPrimitive(groupBy))
                    put("mealsCounted", JsonPrimitive(mealEntries.size))
                    // Days with no meals are excluded from the divisor: averaging over unlogged
                    // days would understate intake rather than describe it.
                    put("daysWithMeals", JsonPrimitive(byDate.size))

                    when (groupBy) {
                        "day" -> put("buckets", JsonArray(window.eachDate().mapNotNull { date ->
                            val dayEntries = byDate[date] ?: return@mapNotNull null
                            buildJsonObject {
                                put("date", JsonPrimitive(date.toString()))
                                put("meals", JsonPrimitive(dayEntries.size))
                                put("totals", nutritionTotalsJson(totalsFor(dayEntries, listOf(date))))
                            }
                        }))
                        "week" -> {
                            val byWeek = mealEntries.groupBy { entry ->
                                val date = entry.capturedAt.toLocalDateTime(zone).date
                                date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)
                            }
                            put("buckets", JsonArray(byWeek.entries.sortedBy { it.key }.map { (weekStart, weekEntries) ->
                                buildJsonObject {
                                    put("weekStartDate", JsonPrimitive(weekStart.toString()))
                                    put("meals", JsonPrimitive(weekEntries.size))
                                    put("totals", nutritionTotalsJson(totalsFor(weekEntries, weekEntries.map { it.capturedAt.toLocalDateTime(zone).date }.distinct())))
                                }
                            }))
                        }
                        else -> {
                            val totals = totalsFor(mealEntries, window.eachDate().toList())
                            put("totals", nutritionTotalsJson(totals))
                            if (byDate.isNotEmpty()) {
                                put("dailyAverage", nutritionTotalsJson(
                                    divideTotals(totals, byDate.size)
                                ))
                            }
                        }
                    }
                }
            )
        }
    }

    private fun registerNutritionalProfileTools() {
        register(
            definition = ToolDefinition(
                name = "list_nutritional_profiles",
                description = "List saved nutritional profile names and aliases so the model can choose likely matches.",
                parametersSchema = emptyObjectSchema()
            )
        ) { call ->
            val profiles = nutritionalProfileRepository.getAll()
            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("profiles", JsonArray(profiles.map { profile ->
                        buildJsonObject {
                            put("profileId", JsonPrimitive(profile.profileId))
                            put("primaryName", JsonPrimitive(profile.primaryName))
                            put("aliases", JsonArray(profile.aliases.map(::JsonPrimitive)))
                        }
                    }))
                }
            )
        }

        register(
            definition = ToolDefinition(
                name = "get_nutritional_profiles",
                description = "Get full stored nutritional details and household-measure-to-gram guidance for specific saved profile IDs.",
                parametersSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("profileIds") {
                            put("type", JsonPrimitive("array"))
                            put("description", JsonPrimitive("Saved nutritional profile IDs to fetch."))
                            putJsonObject("items") {
                                put("type", JsonPrimitive("integer"))
                            }
                        }
                    }
                    put("required", JsonArray(listOf(JsonPrimitive("profileIds"))))
                }
            )
        ) { call ->
            val requestedIds = (call.arguments["profileIds"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.longOrNull }
                ?.distinct()
                .orEmpty()
            if (requestedIds.isEmpty()) {
                return@register ToolResult(
                    toolCallId = call.id,
                    name = call.name,
                    content = JsonPrimitive("profileIds is required"),
                    isError = true
                )
            }

            val matches = requestedIds.mapNotNull { nutritionalProfileRepository.getById(it) }
            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("profileIds", JsonArray(requestedIds.map(::JsonPrimitive)))
                    put("profiles", JsonArray(matches.map { profile ->
                        buildJsonObject {
                            put("profileId", JsonPrimitive(profile.profileId))
                            put("primaryName", JsonPrimitive(profile.primaryName))
                            put("aliases", JsonArray(profile.aliases.map(::JsonPrimitive)))
                            put("servingSize", profile.servingSize?.let(::JsonPrimitive) ?: JsonNull)
                            put("measurementSize", profile.measurementSize?.let(::JsonPrimitive) ?: JsonNull)
                            putJsonObject("nutrition") {
                                put("totalCalories", profile.calories?.let(::JsonPrimitive) ?: JsonNull)
                                put("protein", profile.protein?.let(::JsonPrimitive) ?: JsonNull)
                                put("carbohydrates", profile.carbohydrates?.let(::JsonPrimitive) ?: JsonNull)
                                put("fat", profile.fat?.let(::JsonPrimitive) ?: JsonNull)
                                put("fiber", profile.fiber?.let(::JsonPrimitive) ?: JsonNull)
                                put("sugar", profile.sugar?.let(::JsonPrimitive) ?: JsonNull)
                                put("sodium", profile.sodium?.let(::JsonPrimitive) ?: JsonNull)
                                put("saturatedFat", profile.saturatedFat?.let(::JsonPrimitive) ?: JsonNull)
                                put("transFat", profile.transFat?.let(::JsonPrimitive) ?: JsonNull)
                                put("cholesterol", profile.cholesterol?.let(::JsonPrimitive) ?: JsonNull)
                            }
                            put("source", JsonPrimitive("exact"))
                        }
                    }))
                }
            )
        }
    }

    private suspend fun latestAnalysesByEntryId(entries: List<TrackedEntry>): Map<Long, EntryAnalysis> {
        if (entries.isEmpty()) return emptyMap()
        val entryIds = entries.map { it.entryId }.toSet()
        return entryAnalysisRepository.getAllAnalyses()
            .asSequence()
            .filter { it.entryId in entryIds }
            .groupBy { it.entryId }
            .mapValues { (_, analyses) -> analyses.maxBy { it.capturedAt } }
    }

    private fun parseEntryTypeOrNull(value: String?): EntryType? {
        return when (value?.trim()?.lowercase()) {
            null, "" -> null
            "meal" -> EntryType.MEAL
            "exercise" -> EntryType.EXERCISE
            "sleep" -> EntryType.SLEEP
            "other" -> EntryType.OTHER
            "dailysummary" -> EntryType.DAILY_SUMMARY
            "unknown" -> EntryType.UNKNOWN
            else -> null
        }
    }

    private fun emptyObjectSchema(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject { })
    }

    /**
     * Shared `startDate` / `endDate` schema so every ranged tool presents the same contract.
     * [extraProperties] appends tool-specific parameters alongside them.
     */
    private fun dateRangeSchema(
        extraDescription: String,
        extraProperties: (JsonObjectBuilder.() -> Unit)? = null
    ): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        putJsonObject("properties") {
            putJsonObject("startDate") {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("Inclusive start date as YYYY-MM-DD. $extraDescription"))
            }
            putJsonObject("endDate") {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("Inclusive end date as YYYY-MM-DD. Defaults to today."))
            }
            extraProperties?.invoke(this)
        }
    }

    /** Returns null when the supplied dates were unparseable. */
    private fun resolveWindow(
        call: ToolCall,
        maxSpanDays: Int,
        defaultSpanDays: Int = ToolDateWindows.DEFAULT_SPAN_DAYS
    ): ToolDateWindow? {
        val result = ToolDateWindows.resolve(
            startRaw = call.arguments["startDate"]?.jsonPrimitive?.contentOrNull,
            endRaw = call.arguments["endDate"]?.jsonPrimitive?.contentOrNull,
            today = today(),
            maxSpanDays = maxSpanDays,
            defaultSpanDays = defaultSpanDays
        )
        return (result as? ToolDateWindowResult.Resolved)?.window
    }

    private fun invalidRange(call: ToolCall): ToolResult = ToolResult(
        toolCallId = call.id,
        name = call.name,
        content = JsonPrimitive("startDate and endDate must be valid dates in YYYY-MM-DD form."),
        isError = true
    )

    /**
     * Reports the window actually served. `clamped` and `swapped` are surfaced so the model can
     * see that it did not get what it asked for, and page or narrow accordingly.
     */
    private fun rangeJson(window: ToolDateWindow): JsonObject = buildJsonObject {
        put("startDate", JsonPrimitive(window.startDate.toString()))
        put("endDate", JsonPrimitive(window.endDate.toString()))
        put("days", JsonPrimitive(window.days))
        if (window.swapped) put("swapped", JsonPrimitive(true))
        if (window.clamped) {
            put("clamped", JsonPrimitive(true))
            put("requestedDays", window.requestedDays?.let(::JsonPrimitive) ?: JsonNull)
        }
    }

    private fun nutritionTotalsJson(totals: NutritionTotals): JsonObject = buildJsonObject {
        put("calories", JsonPrimitive(totals.calories))
        put("protein", JsonPrimitive(totals.protein))
        put("carbohydrates", JsonPrimitive(totals.carbs))
        put("fat", JsonPrimitive(totals.fat))
        put("fiber", JsonPrimitive(totals.fiber))
        put("sugar", JsonPrimitive(totals.sugar))
        put("sodium", JsonPrimitive(totals.sodium))
    }

    private fun divideTotals(totals: NutritionTotals, divisor: Int): NutritionTotals {
        if (divisor <= 0) return totals
        return NutritionTotals(
            calories = totals.calories / divisor,
            protein = totals.protein / divisor,
            carbs = totals.carbs / divisor,
            fat = totals.fat / divisor,
            fiber = totals.fiber / divisor,
            sugar = totals.sugar / divisor,
            sodium = totals.sodium / divisor
        )
    }

    /** Compact entry shape for list results; full analysis comes from get_entry_details. */
    private fun compactEntryJson(entry: TrackedEntry): JsonObject = buildJsonObject {
        put("entryId", JsonPrimitive(entry.entryId))
        put("entryType", JsonPrimitive(entry.entryType.toStorageString()))
        put("capturedAt", JsonPrimitive(entry.capturedAt.toString()))
        put("processingStatus", JsonPrimitive(entry.processingStatus.name))
        putNullable("userNotes", entry.userNotes)
    }

    /**
     * Meal analysis from a stored entry analysis, tolerating both the unified and the legacy
     * meal-only payload shapes that DailySummaryService also has to handle.
     */
    private fun mealAnalysisFrom(analysis: EntryAnalysis?): MealAnalysisResult? {
        val raw = analysis?.insightsJson?.takeIf { it.isNotBlank() } ?: return null

        runCatching { json.decodeFromString<UnifiedAnalysisResult>(raw).mealAnalysis }
            .getOrNull()
            ?.let { return it }

        return runCatching { json.decodeFromString<MealAnalysisResult>(raw) }.getOrNull()
    }

    private fun sleepHoursFrom(analysis: EntryAnalysis?): Double? {
        val raw = analysis?.insightsJson?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            json.decodeFromString<UnifiedAnalysisResult>(raw).sleepAnalysis?.durationHours
        }.getOrNull()
    }

    private fun entryJson(entry: TrackedEntry, latestAnalysis: EntryAnalysis?): JsonObject = buildJsonObject {
        put("entryId", JsonPrimitive(entry.entryId))
        put("entryType", JsonPrimitive(entry.entryType.name))
        put("capturedAt", JsonPrimitive(entry.capturedAt.toString()))
        put("processingStatus", JsonPrimitive(entry.processingStatus.name))
        putNullable("userNotes", entry.userNotes)
        putNullable("dataPayload", parseJsonString(entry.dataPayload))
        putNullable("latestInsightsJson", latestAnalysis?.insightsJson?.let(::parseJsonString))
    }

    private fun weightRecordJson(record: WeightRecord): JsonObject = buildJsonObject {
        put("weightRecordId", JsonPrimitive(record.weightRecordId))
        put("weightValue", JsonPrimitive(record.weightValue))
        put("weightUnit", JsonPrimitive(record.weightUnit))
        put("source", JsonPrimitive(record.source))
        put("recordedAt", JsonPrimitive(record.recordedAt.toString()))
        putNullable("relatedEntryId", record.relatedEntryId?.let(::JsonPrimitive))
    }

    private fun JsonObjectBuilder.putNullable(key: String, value: String?) {
        put(key, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun JsonObjectBuilder.putNullable(key: String, value: JsonElement?) {
        put(key, value ?: JsonNull)
    }

    private fun parseJsonString(raw: String?): JsonElement? {
        val value = raw?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.parseToJsonElement(value) }
            .getOrElse { JsonPrimitive(value) }
    }

    companion object {
        /**
         * Span caps, chosen so a single call cannot flood the context window.
         *
         * The overview is one compact line per day, so it can afford a wide window; detail tools
         * return whole records and are held to a quarter. Exceeding a cap narrows the window and
         * says so rather than failing, so a vague question still gets a useful answer.
         */
        internal const val MAX_OVERVIEW_DAYS = 186
        internal const val MAX_DETAIL_DAYS = 92
        internal const val MAX_WEEKLY_DAYS = 371

        /** Unchanged from before date ranges existed; the range path reaches further back. */
        internal const val MAX_WEIGHT_DAYS = 90

        internal const val MAX_ENTRIES_PER_CALL = 50
        internal const val DEFAULT_ENTRIES_PER_CALL = 20
        internal const val MAX_ENTRY_DETAILS = 20
    }
}
