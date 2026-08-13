package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.analysis.CheckInAnalysisStatus
import com.wellnesswingman.data.model.analysis.CheckInFacets
import com.wellnesswingman.db.WellnessWingmanDatabase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/**
 * SQLDelight implementation of CheckInAnalysisRepository.
 */
class SqlDelightCheckInAnalysisRepository(
    private val database: WellnessWingmanDatabase
) : CheckInAnalysisRepository {

    private val queries = database.checkInAnalysisQueries

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override suspend fun getAllAnalyses(): List<CheckInAnalysis> = withContext(Dispatchers.IO) {
        queries.getAllAnalyses().executeAsList().mapNotNull { it.toCheckInAnalysisOrNull() }
    }

    override suspend fun getAnalysis(date: LocalDate, slot: CheckInSlot): CheckInAnalysis? =
        withContext(Dispatchers.IO) {
            queries.getAnalysisForDateAndSlot(
                checkInDate = date.toEpochDays().toLong(),
                slot = slot.toStorageString()
            ).executeAsOneOrNull()?.toCheckInAnalysisOrNull()
        }

    override suspend fun getAnalysesForDate(date: LocalDate): List<CheckInAnalysis> =
        withContext(Dispatchers.IO) {
            queries.getAnalysesForDate(date.toEpochDays().toLong())
                .executeAsList().mapNotNull { it.toCheckInAnalysisOrNull() }
        }

    override suspend fun getAnalysesForDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<CheckInAnalysis> = withContext(Dispatchers.IO) {
        queries.getAnalysesForDateRange(
            startDate.toEpochDays().toLong(),
            endDate.toEpochDays().toLong()
        ).executeAsList().mapNotNull { it.toCheckInAnalysisOrNull() }
    }

    override suspend fun getAnalysisByExternalId(externalId: String): CheckInAnalysis? =
        withContext(Dispatchers.IO) {
            queries.getAnalysisByExternalId(externalId).executeAsOneOrNull()?.toCheckInAnalysisOrNull()
        }

    override suspend fun saveAnalysis(analysis: CheckInAnalysis): Long =
        withContext(Dispatchers.IO) {
            queries.upsertAnalysisForDateAndSlot(
                externalId = analysis.externalId,
                checkInDate = analysis.checkInDate.toEpochDays().toLong(),
                slot = analysis.slot.toStorageString(),
                status = analysis.status.toStorageString(),
                providerId = analysis.providerId,
                model = analysis.model,
                analyzedAt = analysis.analyzedAt.toEpochMilliseconds(),
                facetsJson = analysis.facets.toJson(),
                errorMessage = analysis.errorMessage,
                schemaVersion = analysis.schemaVersion
            )
            queries.lastInsertRowId().executeAsOne()
        }

    override suspend fun deleteAnalysis(date: LocalDate, slot: CheckInSlot) =
        withContext(Dispatchers.IO) {
            queries.deleteAnalysisForDateAndSlot(
                checkInDate = date.toEpochDays().toLong(),
                slot = slot.toStorageString()
            )
        }

    override suspend fun deleteOldAnalyses(beforeDate: LocalDate) = withContext(Dispatchers.IO) {
        queries.deleteOldAnalyses(beforeDate.toEpochDays().toLong())
    }

    override suspend fun upsertAnalysis(analysis: CheckInAnalysis) = withContext(Dispatchers.IO) {
        queries.upsertAnalysis(
            analysisId = analysis.analysisId,
            externalId = analysis.externalId,
            checkInDate = analysis.checkInDate.toEpochDays().toLong(),
            slot = analysis.slot.toStorageString(),
            status = analysis.status.toStorageString(),
            providerId = analysis.providerId,
            model = analysis.model,
            analyzedAt = analysis.analyzedAt.toEpochMilliseconds(),
            facetsJson = analysis.facets.toJson(),
            errorMessage = analysis.errorMessage,
            schemaVersion = analysis.schemaVersion
        )
    }

    private fun CheckInFacets?.toJson(): String =
        if (this == null) "" else json.encodeToString(CheckInFacets.serializer(), this)

    /**
     * Maps the SQLDelight row to the domain model, or null when the row cannot be placed.
     *
     * Nothing here throws. An unreadable facets blob degrades to null facets and a FAILED status
     * so the extraction can be re-run; an unrecognised slot drops the row entirely, since an
     * analysis with no slot has no check-in to belong to.
     *
     * The slot case used to throw, which was worse than it looked: `getAllAnalyses()` is read
     * unguarded by `DataMigrationService.exportData()`, so a single row written by a future
     * schema would have aborted the user's entire data export rather than costing one analysis.
     */
    private fun com.wellnesswingman.db.CheckInAnalysis.toCheckInAnalysisOrNull(): CheckInAnalysis? {
        val parsedSlot = CheckInSlot.fromString(slot)
        if (parsedSlot == null) {
            Napier.w("Skipping check-in analysis $analysisId: unrecognised slot '$slot'")
            return null
        }

        val storedStatus = CheckInAnalysisStatus.fromString(status)

        var unreadable = false
        val parsedFacets = if (facetsJson.isBlank()) {
            null
        } else {
            try {
                json.decodeFromString(CheckInFacets.serializer(), facetsJson)
            } catch (e: Exception) {
                Napier.w("Failed to parse check-in facets for analysisId=$analysisId: ${e.message}")
                unreadable = true
                null
            }
        }

        // A completed row whose facets will not parse — an import from a newer schema, say —
        // must not be reported as completed. The UI would read "extraction finished and found
        // nothing" and offer no way to re-run, even though this is derived data that exists
        // precisely to be regenerable. Surfacing it as failed puts the retry control back.
        val effectiveStatus = if (unreadable && storedStatus == CheckInAnalysisStatus.COMPLETED) {
            CheckInAnalysisStatus.FAILED
        } else {
            storedStatus
        }

        return CheckInAnalysis(
            analysisId = analysisId,
            externalId = externalId,
            checkInDate = LocalDate.fromEpochDays(checkInDate.toInt()),
            slot = parsedSlot,
            status = effectiveStatus,
            providerId = providerId,
            model = model,
            analyzedAt = Instant.fromEpochMilliseconds(analyzedAt),
            facets = parsedFacets,
            errorMessage = if (unreadable && errorMessage == null) {
                "The stored result could not be read. Try again to regenerate it."
            } else {
                errorMessage
            },
            schemaVersion = schemaVersion
        )
    }
}

