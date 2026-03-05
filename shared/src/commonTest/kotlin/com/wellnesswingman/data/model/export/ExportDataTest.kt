package com.wellnesswingman.data.model.export

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeeklySummary
import com.wellnesswingman.data.model.WeightRecord
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportDataTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // region -- parseCSharpInstant --

    @Test
    fun `parseCSharpInstant handles ISO 8601 with Z`() {
        val result = parseCSharpInstant("2026-02-11T09:41:21Z")
        assertEquals(Instant.parse("2026-02-11T09:41:21Z"), result)
    }

    @Test
    fun `parseCSharpInstant handles ISO 8601 with milliseconds and Z`() {
        val result = parseCSharpInstant("2026-02-11T09:41:21.157Z")
        assertEquals(Instant.parse("2026-02-11T09:41:21.157Z"), result)
    }

    @Test
    fun `parseCSharpInstant handles CSharp 7 fractional digits without offset`() {
        val result = parseCSharpInstant("2026-02-11T09:41:21.1571689")
        // Should truncate to 3 digits and append Z
        assertEquals(Instant.parse("2026-02-11T09:41:21.157Z"), result)
    }

    @Test
    fun `parseCSharpInstant handles CSharp 7 fractional digits with Z`() {
        // With Z suffix, Instant.parse may handle it directly or truncate
        val result = parseCSharpInstant("2026-02-11T09:41:21.1571689Z")
        // The instant should represent the same point in time (within millisecond precision)
        val expected = Instant.parse("2026-02-11T09:41:21.157Z")
        // Allow for full precision parsing (157168900 nanoseconds) vs truncated (157000000)
        val diffMs = (result.toEpochMilliseconds() - expected.toEpochMilliseconds())
        assertTrue(diffMs in -1..1, "Expected instants to be within 1ms, diff was ${diffMs}ms")
    }

    @Test
    fun `parseCSharpInstant handles no fractional seconds and no offset`() {
        val result = parseCSharpInstant("2026-02-11T09:41:21")
        assertEquals(Instant.parse("2026-02-11T09:41:21Z"), result)
    }

    @Test
    fun `parseCSharpInstant handles plus offset`() {
        val result = parseCSharpInstant("2026-02-11T09:41:21+05:30")
        assertEquals(Instant.parse("2026-02-11T09:41:21+05:30"), result)
    }

    @Test
    fun `parseCSharpInstant handles whitespace`() {
        val result = parseCSharpInstant("  2026-02-11T09:41:21  ")
        assertEquals(Instant.parse("2026-02-11T09:41:21Z"), result)
    }

    @Test
    fun `parseCSharpInstant handles short fractional digits`() {
        val result = parseCSharpInstant("2026-02-11T09:41:21.1")
        // Should pad to 3 digits: .100
        assertEquals(Instant.parse("2026-02-11T09:41:21.100Z"), result)
    }

    // endregion

    // region -- EntryTypeIntSerializer --

    @Test
    fun `EntryTypeIntSerializer round-trips all values`() {
        val types = mapOf(
            0 to EntryType.UNKNOWN,
            1 to EntryType.MEAL,
            2 to EntryType.EXERCISE,
            3 to EntryType.SLEEP,
            4 to EntryType.OTHER,
            5 to EntryType.DAILY_SUMMARY
        )
        for ((intVal, expectedType) in types) {
            val jsonStr = json.encodeToString(EntryTypeIntSerializer, expectedType)
            assertEquals(intVal.toString(), jsonStr)
            val deserialized = json.decodeFromString(EntryTypeIntSerializer, jsonStr)
            assertEquals(expectedType, deserialized)
        }
    }

    @Test
    fun `EntryTypeIntSerializer defaults unknown int to UNKNOWN`() {
        val result = json.decodeFromString(EntryTypeIntSerializer, "99")
        assertEquals(EntryType.UNKNOWN, result)
    }

    // endregion

    // region -- ProcessingStatusIntSerializer --

    @Test
    fun `ProcessingStatusIntSerializer round-trips all values`() {
        val statuses = mapOf(
            0 to ProcessingStatus.PENDING,
            1 to ProcessingStatus.PROCESSING,
            2 to ProcessingStatus.COMPLETED,
            3 to ProcessingStatus.FAILED,
            4 to ProcessingStatus.SKIPPED
        )
        for ((intVal, expectedStatus) in statuses) {
            val jsonStr = json.encodeToString(ProcessingStatusIntSerializer, expectedStatus)
            assertEquals(intVal.toString(), jsonStr)
            val deserialized = json.decodeFromString(ProcessingStatusIntSerializer, jsonStr)
            assertEquals(expectedStatus, deserialized)
        }
    }

    @Test
    fun `ProcessingStatusIntSerializer defaults unknown int to PENDING`() {
        val result = json.decodeFromString(ProcessingStatusIntSerializer, "99")
        assertEquals(ProcessingStatus.PENDING, result)
    }

    // endregion

    // region -- ExportData serialization --

    @Test
    fun `ExportData serialization round-trip`() {
        val data = ExportData(
            version = 1,
            exportedAt = "2026-02-11T09:41:21Z",
            entries = listOf(
                ExportTrackedEntry(
                    entryId = 1,
                    entryType = EntryType.MEAL,
                    capturedAt = "2026-02-11T09:41:21Z",
                    dataPayload = "{}",
                    processingStatus = ProcessingStatus.COMPLETED
                )
            ),
            analyses = listOf(
                ExportEntryAnalysis(
                    analysisId = 1,
                    entryId = 1,
                    capturedAt = "2026-02-11T09:41:21Z",
                    insightsJson = "{}"
                )
            ),
            summaries = listOf(
                ExportDailySummary(
                    summaryId = 1,
                    summaryDate = "2026-02-11T00:00:00Z",
                    highlights = "Good day"
                )
            ),
            weeklySummaries = listOf(
                ExportWeeklySummary(
                    summaryId = 1,
                    weekStartDate = "2026-02-10",
                    highlights = "Good week",
                    mealCount = 21
                )
            ),
            userProfile = ExportUserProfile(
                height = 180.0,
                sex = "Male",
                currentWeight = 75.0
            ),
            weightRecords = listOf(
                ExportWeightRecord(
                    weightRecordId = 1,
                    recordedAt = "2026-02-11T09:41:21Z",
                    weightValue = 75.0,
                    weightUnit = "kg",
                    source = "Manual"
                )
            )
        )
        val jsonStr = json.encodeToString(ExportData.serializer(), data)
        val decoded = json.decodeFromString(ExportData.serializer(), jsonStr)
        assertEquals(data, decoded)
    }

    // endregion

    // region -- Mapping functions: TrackedEntry --

    @Test
    fun `TrackedEntry toExport and back preserves data`() {
        val now = Instant.parse("2026-02-11T09:41:21Z")
        val entry = TrackedEntry(
            entryId = 42,
            externalId = "ext-1",
            entryType = EntryType.EXERCISE,
            capturedAt = now,
            capturedAtTimeZoneId = "America/New_York",
            capturedAtOffsetMinutes = -300,
            blobPath = "/photos/img.jpg",
            dataPayload = """{"key":"value"}""",
            dataSchemaVersion = 2,
            processingStatus = ProcessingStatus.COMPLETED,
            userNotes = "Test notes"
        )

        val exported = entry.toExport()
        assertEquals(42, exported.entryId)
        assertEquals("ext-1", exported.externalId)
        assertEquals(EntryType.EXERCISE, exported.entryType)
        assertEquals(now.toString(), exported.capturedAt)
        assertEquals("America/New_York", exported.capturedAtTimeZoneId)
        assertEquals(-300, exported.capturedAtOffsetMinutes)
        assertEquals("/photos/img.jpg", exported.blobPath)
        assertEquals(ProcessingStatus.COMPLETED, exported.processingStatus)
        assertEquals("Test notes", exported.userNotes)

        val roundTripped = exported.toDomain()
        assertEquals(entry, roundTripped)
    }

    // endregion

    // region -- Mapping functions: EntryAnalysis --

    @Test
    fun `EntryAnalysis toExport and back preserves data`() {
        val now = Instant.parse("2026-03-01T12:00:00Z")
        val analysis = EntryAnalysis(
            analysisId = 10,
            entryId = 42,
            externalId = "ext-a",
            providerId = "openai",
            model = "gpt-4",
            capturedAt = now,
            insightsJson = """{"calories":500}""",
            schemaVersion = "2.0"
        )

        val exported = analysis.toExport()
        assertEquals(10, exported.analysisId)
        assertEquals(42, exported.entryId)
        assertEquals("openai", exported.providerId)
        assertEquals("gpt-4", exported.model)
        assertEquals(now.toString(), exported.capturedAt)

        val roundTripped = exported.toDomain()
        assertEquals(analysis, roundTripped)
    }

    // endregion

    // region -- Mapping functions: DailySummary --

    @Test
    fun `DailySummary toExport and back preserves data`() {
        val generatedAt = Instant.parse("2026-02-11T20:00:00Z")
        val summary = DailySummary(
            summaryId = 5,
            externalId = "ext-s",
            summaryDate = LocalDate.parse("2026-02-11"),
            highlights = "Ate well",
            recommendations = "Drink more water",
            generatedAt = generatedAt,
            userComments = "Felt great",
            payloadJson = """{"key":"val"}"""
        )

        val exported = summary.toExport()
        assertEquals(5, exported.summaryId)
        assertEquals("ext-s", exported.externalId)
        assertEquals("Ate well", exported.highlights)

        val roundTripped = exported.toDomain()
        assertEquals(summary.summaryId, roundTripped.summaryId)
        assertEquals(summary.summaryDate, roundTripped.summaryDate)
        assertEquals(summary.highlights, roundTripped.highlights)
        assertEquals(summary.recommendations, roundTripped.recommendations)
        assertEquals(summary.generatedAt, roundTripped.generatedAt)
        assertEquals(summary.userComments, roundTripped.userComments)
        assertEquals(summary.payloadJson, roundTripped.payloadJson)
    }

    @Test
    fun `ExportDailySummary toDomain handles date-only string`() {
        val exported = ExportDailySummary(
            summaryId = 1,
            summaryDate = "2026-02-11",
            highlights = "test"
        )
        val domain = exported.toDomain()
        assertEquals(LocalDate.parse("2026-02-11"), domain.summaryDate)
    }

    @Test
    fun `ExportDailySummary toDomain handles full ISO datetime`() {
        val exported = ExportDailySummary(
            summaryId = 1,
            summaryDate = "2026-02-11T00:00:00Z",
            highlights = "test"
        )
        val domain = exported.toDomain()
        assertEquals(LocalDate.parse("2026-02-11"), domain.summaryDate)
    }

    @Test
    fun `ExportDailySummary toDomain handles null generatedAt`() {
        val exported = ExportDailySummary(
            summaryId = 1,
            summaryDate = "2026-02-11",
            generatedAt = null
        )
        val domain = exported.toDomain()
        assertNull(domain.generatedAt)
    }

    // endregion

    // region -- Mapping functions: WeeklySummary --

    @Test
    fun `WeeklySummary toExport and back preserves data`() {
        val generatedAt = Instant.parse("2026-02-17T20:00:00Z")
        val summary = WeeklySummary(
            summaryId = 3,
            weekStartDate = LocalDate.parse("2026-02-10"),
            highlights = "Active week",
            recommendations = "Keep it up",
            mealCount = 21,
            exerciseCount = 5,
            sleepCount = 7,
            otherCount = 2,
            totalEntries = 35,
            generatedAt = generatedAt,
            userComments = "Good week",
            payloadJson = """{"trend":"up"}"""
        )

        val exported = summary.toExport()
        assertEquals(3, exported.summaryId)
        assertEquals("2026-02-10", exported.weekStartDate)
        assertEquals(21, exported.mealCount)

        val roundTripped = exported.toDomain()
        assertEquals(summary.summaryId, roundTripped.summaryId)
        assertEquals(summary.weekStartDate, roundTripped.weekStartDate)
        assertEquals(summary.highlights, roundTripped.highlights)
        assertEquals(summary.mealCount, roundTripped.mealCount)
        assertEquals(summary.exerciseCount, roundTripped.exerciseCount)
        assertEquals(summary.sleepCount, roundTripped.sleepCount)
        assertEquals(summary.otherCount, roundTripped.otherCount)
        assertEquals(summary.totalEntries, roundTripped.totalEntries)
        assertEquals(summary.generatedAt, roundTripped.generatedAt)
    }

    @Test
    fun `ExportWeeklySummary toDomain handles ISO datetime weekStartDate`() {
        val exported = ExportWeeklySummary(
            summaryId = 1,
            weekStartDate = "2026-02-10T00:00:00Z"
        )
        val domain = exported.toDomain()
        assertEquals(LocalDate.parse("2026-02-10"), domain.weekStartDate)
    }

    @Test
    fun `ExportWeeklySummary toDomain handles null generatedAt`() {
        val exported = ExportWeeklySummary(
            summaryId = 1,
            weekStartDate = "2026-02-10",
            generatedAt = null
        )
        val domain = exported.toDomain()
        assertNull(domain.generatedAt)
    }

    // endregion

    // region -- Mapping functions: WeightRecord --

    @Test
    fun `WeightRecord toExport and back preserves data`() {
        val now = Instant.parse("2026-02-11T08:00:00Z")
        val record = WeightRecord(
            weightRecordId = 7,
            externalId = "ext-w",
            recordedAt = now,
            weightValue = 75.5,
            weightUnit = "kg",
            source = "Manual",
            relatedEntryId = 42
        )

        val exported = record.toExport()
        assertEquals(7, exported.weightRecordId)
        assertEquals("ext-w", exported.externalId)
        assertEquals(now.toString(), exported.recordedAt)
        assertEquals(75.5, exported.weightValue)
        assertEquals("kg", exported.weightUnit)
        assertEquals("Manual", exported.source)
        assertEquals(42, exported.relatedEntryId)

        val roundTripped = exported.toDomain()
        assertEquals(record, roundTripped)
    }

    @Test
    fun `WeightRecord toExport handles null optional fields`() {
        val now = Instant.parse("2026-02-11T08:00:00Z")
        val record = WeightRecord(
            weightRecordId = 1,
            recordedAt = now,
            weightValue = 80.0,
            weightUnit = "lbs",
            source = "LlmDetected"
        )

        val exported = record.toExport()
        assertNull(exported.externalId)
        assertNull(exported.relatedEntryId)

        val roundTripped = exported.toDomain()
        assertEquals(record, roundTripped)
    }

    // endregion
}
