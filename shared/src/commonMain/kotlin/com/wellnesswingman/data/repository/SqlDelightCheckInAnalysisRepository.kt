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
        queries.getAllAnalyses().executeAsList().map { it.toCheckInAnalysis() }
    }

    override suspend fun getAnalysis(date: LocalDate, slot: CheckInSlot): CheckInAnalysis? =
        withContext(Dispatchers.IO) {
            queries.getAnalysisForDateAndSlot(
                checkInDate = date.toEpochDays().toLong(),
                slot = slot.toStorageString()
            ).executeAsOneOrNull()?.toCheckInAnalysis()
        }

    override suspend fun getAnalysesForDate(date: LocalDate): List<CheckInAnalysis> =
        withContext(Dispatchers.IO) {
            queries.getAnalysesForDate(date.toEpochDays().toLong())
                .executeAsList().map { it.toCheckInAnalysis() }
        }

    override suspend fun getAnalysesForDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<CheckInAnalysis> = withContext(Dispatchers.IO) {
        queries.getAnalysesForDateRange(
            startDate.toEpochDays().toLong(),
            endDate.toEpochDays().toLong()
        ).executeAsList().map { it.toCheckInAnalysis() }
    }

    override suspend fun getAnalysisByExternalId(externalId: String): CheckInAnalysis? =
        withContext(Dispatchers.IO) {
            queries.getAnalysisByExternalId(externalId).executeAsOneOrNull()?.toCheckInAnalysis()
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
     * Maps the SQLDelight row to the domain model.
     *
     * An unreadable facets blob degrades to null rather than throwing: the extraction is derived
     * data and can be re-run, so a bad blob should not make the whole day unreadable.
     */
    private fun com.wellnesswingman.db.CheckInAnalysis.toCheckInAnalysis(): CheckInAnalysis {
        val parsedSlot = CheckInSlot.fromString(slot)
            ?: error("Unrecognised check-in slot '$slot' for analysisId=$analysisId")

        val parsedFacets = if (facetsJson.isBlank()) {
            null
        } else {
            try {
                json.decodeFromString(CheckInFacets.serializer(), facetsJson)
            } catch (e: Exception) {
                Napier.w("Failed to parse check-in facets for analysisId=$analysisId: ${e.message}")
                null
            }
        }

        return CheckInAnalysis(
            analysisId = analysisId,
            externalId = externalId,
            checkInDate = LocalDate.fromEpochDays(checkInDate.toInt()),
            slot = parsedSlot,
            status = CheckInAnalysisStatus.fromString(status),
            providerId = providerId,
            model = model,
            analyzedAt = Instant.fromEpochMilliseconds(analyzedAt),
            facets = parsedFacets,
            errorMessage = errorMessage,
            schemaVersion = schemaVersion
        )
    }
}
