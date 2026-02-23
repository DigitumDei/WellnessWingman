package com.wellnesswingman.data.model.export

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// region -- C# DateTime parsing helper --

/**
 * Parses a DateTime string from C# into a kotlinx.datetime.Instant.
 *
 * C# DateTime.ToString() produces formats like:
 * - "2026-02-11T09:41:21.1571689"  (no timezone, 7 fractional digits)
 * - "2026-02-11T09:41:21.1571689Z" (with Z)
 * - "2026-02-11T09:41:21Z"
 * - "2026-02-11T09:41:21"
 *
 * kotlinx.datetime.Instant.parse() requires an offset designator and
 * supports at most 9 fractional-second digits in standard ISO 8601.
 * C#'s 7-digit "ticks" fraction is valid ISO but kotlinx chokes on it
 * when there is no trailing offset. We normalise by:
 *  1. Truncating fractional seconds to 3 digits (milliseconds) for safety.
 *  2. Appending "Z" when no offset is present (C# DateTimes from the MAUI
 *     app are stored as UTC).
 */
internal fun parseCSharpInstant(value: String): Instant {
    // Fast path: already parseable
    try {
        return Instant.parse(value)
    } catch (_: Exception) {
        // Fall through to normalisation
    }

    var s = value.trim()

    // Normalise fractional seconds – keep at most 3 digits (ms precision)
    val dotIndex = s.indexOf('.')
    if (dotIndex != -1) {
        // Find the end of the fractional part (digits only)
        var endOfFrac = dotIndex + 1
        while (endOfFrac < s.length && s[endOfFrac].isDigit()) {
            endOfFrac++
        }
        val suffix = s.substring(endOfFrac) // e.g. "Z", "+05:30", or ""
        val fracDigits = s.substring(dotIndex + 1, endOfFrac)
        val truncated = fracDigits.take(3).padEnd(3, '0')
        s = s.substring(0, dotIndex) + "." + truncated + suffix
    }

    // Append Z if no timezone offset is present
    val hasZ = s.endsWith('Z') || s.endsWith('z')
    val hasPlusOffset = s.indexOf('+', startIndex = 10) != -1
    val hasMinusOffset = s.lastIndexOf('-').let { it >= 10 }
    if (!hasZ && !hasPlusOffset && !hasMinusOffset) {
        s += "Z"
    }

    return Instant.parse(s)
}

// endregion

// region -- Enum-as-integer serializers (MAUI compatibility) --

object EntryTypeIntSerializer : KSerializer<EntryType> {
    override val descriptor = PrimitiveSerialDescriptor("EntryType", PrimitiveKind.INT)

    // C# enum order: Unknown=0, Meal=1, Exercise=2, Sleep=3, Other=4, DailySummary=5
    private val intToType = mapOf(
        0 to EntryType.UNKNOWN,
        1 to EntryType.MEAL,
        2 to EntryType.EXERCISE,
        3 to EntryType.SLEEP,
        4 to EntryType.OTHER,
        5 to EntryType.DAILY_SUMMARY
    )
    private val typeToInt = intToType.entries.associate { (k, v) -> v to k }

    override fun serialize(encoder: Encoder, value: EntryType) {
        encoder.encodeInt(typeToInt[value] ?: 0)
    }

    override fun deserialize(decoder: Decoder): EntryType {
        return try {
            val intVal = decoder.decodeInt()
            intToType[intVal] ?: EntryType.UNKNOWN
        } catch (_: Exception) {
            // Fallback: try reading as string for robustness
            EntryType.UNKNOWN
        }
    }
}

object ProcessingStatusIntSerializer : KSerializer<ProcessingStatus> {
    override val descriptor = PrimitiveSerialDescriptor("ProcessingStatus", PrimitiveKind.INT)

    // C# enum: Pending=0, Processing=1, Completed=2, Failed=3, Skipped=4
    private val intToStatus = mapOf(
        0 to ProcessingStatus.PENDING,
        1 to ProcessingStatus.PROCESSING,
        2 to ProcessingStatus.COMPLETED,
        3 to ProcessingStatus.FAILED,
        4 to ProcessingStatus.SKIPPED
    )
    private val statusToInt = intToStatus.entries.associate { (k, v) -> v to k }

    override fun serialize(encoder: Encoder, value: ProcessingStatus) {
        encoder.encodeInt(statusToInt[value] ?: 0)
    }

    override fun deserialize(decoder: Decoder): ProcessingStatus {
        return try {
            val intVal = decoder.decodeInt()
            intToStatus[intVal] ?: ProcessingStatus.PENDING
        } catch (_: Exception) {
            ProcessingStatus.PENDING
        }
    }
}

// endregion

// region -- Export models (MAUI-compatible PascalCase JSON) --

@Serializable
data class ExportData(
    @SerialName("Version") val version: Int = 1,
    @SerialName("ExportedAt") val exportedAt: String, // ISO 8601
    @SerialName("Entries") val entries: List<ExportTrackedEntry> = emptyList(),
    @SerialName("Analyses") val analyses: List<ExportEntryAnalysis> = emptyList(),
    @SerialName("Summaries") val summaries: List<ExportDailySummary> = emptyList(),
    @SerialName("SummariesAnalyses") val summariesAnalyses: List<ExportSummaryAnalysis> = emptyList()
)

@Serializable
data class ExportTrackedEntry(
    @SerialName("EntryId") val entryId: Long,
    @SerialName("ExternalId") val externalId: String? = null,
    @SerialName("EntryType") @Serializable(with = EntryTypeIntSerializer::class) val entryType: EntryType = EntryType.UNKNOWN,
    @SerialName("CapturedAt") val capturedAt: String, // ISO 8601
    @SerialName("CapturedAtTimeZoneId") val capturedAtTimeZoneId: String? = null,
    @SerialName("CapturedAtOffsetMinutes") val capturedAtOffsetMinutes: Int? = null,
    @SerialName("BlobPath") val blobPath: String? = null,
    @SerialName("DataPayload") val dataPayload: String = "",
    @SerialName("DataSchemaVersion") val dataSchemaVersion: Int = 1,
    @SerialName("ProcessingStatus") @Serializable(with = ProcessingStatusIntSerializer::class) val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
    @SerialName("UserNotes") val userNotes: String? = null
)

@Serializable
data class ExportEntryAnalysis(
    @SerialName("AnalysisId") val analysisId: Long,
    @SerialName("EntryId") val entryId: Long,
    @SerialName("ExternalId") val externalId: String? = null,
    @SerialName("ProviderId") val providerId: String = "",
    @SerialName("Model") val model: String = "",
    @SerialName("CapturedAt") val capturedAt: String, // ISO 8601
    @SerialName("InsightsJson") val insightsJson: String = "",
    @SerialName("SchemaVersion") val schemaVersion: String = "1.0"
)

@Serializable
data class ExportDailySummary(
    @SerialName("SummaryId") val summaryId: Long,
    @SerialName("ExternalId") val externalId: String? = null,
    @SerialName("SummaryDate") val summaryDate: String, // ISO 8601
    @SerialName("Highlights") val highlights: String = "",
    @SerialName("Recommendations") val recommendations: String = ""
)

@Serializable
data class ExportSummaryAnalysis(
    @SerialName("SummaryId") val summaryId: Long,
    @SerialName("AnalysisId") val analysisId: Long
)

// endregion

// region -- Mapping functions --

fun TrackedEntry.toExport(): ExportTrackedEntry = ExportTrackedEntry(
    entryId = entryId,
    externalId = externalId,
    entryType = entryType,
    capturedAt = capturedAt.toString(),
    capturedAtTimeZoneId = capturedAtTimeZoneId,
    capturedAtOffsetMinutes = capturedAtOffsetMinutes,
    blobPath = blobPath,
    dataPayload = dataPayload,
    dataSchemaVersion = dataSchemaVersion,
    processingStatus = processingStatus,
    userNotes = userNotes
)

fun ExportTrackedEntry.toDomain(): TrackedEntry = TrackedEntry(
    entryId = entryId,
    externalId = externalId,
    entryType = entryType,
    capturedAt = parseCSharpInstant(capturedAt),
    capturedAtTimeZoneId = capturedAtTimeZoneId,
    capturedAtOffsetMinutes = capturedAtOffsetMinutes,
    blobPath = blobPath,
    dataPayload = dataPayload,
    dataSchemaVersion = dataSchemaVersion,
    processingStatus = processingStatus,
    userNotes = userNotes
)

fun EntryAnalysis.toExport(): ExportEntryAnalysis = ExportEntryAnalysis(
    analysisId = analysisId,
    entryId = entryId,
    externalId = externalId,
    providerId = providerId,
    model = model,
    capturedAt = capturedAt.toString(),
    insightsJson = insightsJson,
    schemaVersion = schemaVersion
)

fun ExportEntryAnalysis.toDomain(): EntryAnalysis = EntryAnalysis(
    analysisId = analysisId,
    entryId = entryId,
    externalId = externalId,
    providerId = providerId,
    model = model,
    capturedAt = parseCSharpInstant(capturedAt),
    insightsJson = insightsJson,
    schemaVersion = schemaVersion
)

fun DailySummary.toExport(): ExportDailySummary = ExportDailySummary(
    summaryId = summaryId,
    externalId = externalId,
    summaryDate = summaryDate.atStartOfDayIn(TimeZone.UTC).toString(),
    highlights = highlights,
    recommendations = recommendations
    // Note: generatedAt is Kotlin-only, not exported
)

fun ExportDailySummary.toDomain(): DailySummary {
    // Parse date - could be ISO datetime or just a date
    val date = try {
        LocalDate.parse(summaryDate.substringBefore('T'))
    } catch (_: Exception) {
        parseCSharpInstant(summaryDate).toLocalDateTime(TimeZone.UTC).date
    }
    return DailySummary(
        summaryId = summaryId,
        externalId = externalId,
        summaryDate = date,
        highlights = highlights,
        recommendations = recommendations,
        generatedAt = null // Kotlin-only field
    )
}

// endregion
