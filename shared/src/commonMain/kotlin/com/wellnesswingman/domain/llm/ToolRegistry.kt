package com.wellnesswingman.domain.llm

import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeightRecord
import com.wellnesswingman.data.model.llm.ToolCall
import com.wellnesswingman.data.model.llm.ToolDefinition
import com.wellnesswingman.data.model.llm.ToolResult
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonObject

class ToolRegistry(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val entryAnalysisRepository: EntryAnalysisRepository,
    private val weightHistoryRepository: WeightHistoryRepository,
    private val appSettingsRepository: AppSettingsRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val definitions = linkedMapOf<String, ToolDefinition>()
    private val handlers = linkedMapOf<String, suspend (ToolCall) -> ToolResult>()

    init {
        registerBuiltIns()
    }

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
                description = "Get recent weight history records for the user.",
                parametersSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("days") {
                            put("type", JsonPrimitive("integer"))
                            put("description", JsonPrimitive("Number of days of history to return, capped at 90."))
                        }
                    }
                }
            )
        ) { call ->
            val days = call.arguments["days"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 90) ?: 30
            val now = Clock.System.now()
            val start = now.minus(days, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
            val records = weightHistoryRepository.getWeightHistory(start, now)
            ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = buildJsonObject {
                    put("days", JsonPrimitive(days))
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
}
