package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * SQLDelight implementation of EntryAnalysisRepository.
 */
class SqlDelightEntryAnalysisRepository(
    private val database: WellnessWingmanDatabase
) : EntryAnalysisRepository {

    private val queries = database.entryAnalysisQueries

    override suspend fun getAllAnalyses(): List<EntryAnalysis> = withContext(Dispatchers.IO) {
        queries.getAllAnalyses().executeAsList().map { it.toEntryAnalysis() }
    }

    override suspend fun getAnalysisById(id: Long): EntryAnalysis? = withContext(Dispatchers.IO) {
        queries.getAnalysisById(id).executeAsOneOrNull()?.toEntryAnalysis()
    }

    override suspend fun getAnalysisByExternalId(externalId: String): EntryAnalysis? =
        withContext(Dispatchers.IO) {
            queries.getAnalysisByExternalId(externalId).executeAsOneOrNull()?.toEntryAnalysis()
        }

    override suspend fun getAnalysesForEntry(entryId: Long): List<EntryAnalysis> =
        withContext(Dispatchers.IO) {
            queries.getAnalysesForEntry(entryId).executeAsList().map { it.toEntryAnalysis() }
        }

    override suspend fun getLatestAnalysisForEntry(entryId: Long): EntryAnalysis? =
        withContext(Dispatchers.IO) {
            queries.getLatestAnalysisForEntry(entryId).executeAsOneOrNull()?.toEntryAnalysis()
        }

    override suspend fun insertAnalysis(analysis: EntryAnalysis): Long =
        withContext(Dispatchers.IO) {
            queries.insertAnalysis(
                entryId = analysis.entryId,
                externalId = analysis.externalId,
                providerId = analysis.providerId,
                model = analysis.model,
                capturedAt = analysis.capturedAt.toEpochMilliseconds(),
                insightsJson = analysis.insightsJson,
                schemaVersion = analysis.schemaVersion
            )
            queries.lastInsertRowId().executeAsOne()
        }

    override suspend fun updateAnalysis(id: Long, insightsJson: String, schemaVersion: String) =
        withContext(Dispatchers.IO) {
            queries.updateAnalysis(insightsJson, schemaVersion, id)
        }

    override suspend fun deleteAnalysis(id: Long) = withContext(Dispatchers.IO) {
        queries.deleteAnalysis(id)
    }

    override suspend fun deleteAnalysesForEntry(entryId: Long) = withContext(Dispatchers.IO) {
        queries.deleteAnalysesForEntry(entryId)
    }

    /**
     * Maps SQLDelight EntryAnalysis to domain EntryAnalysis.
     */
    private fun com.wellnesswingman.db.EntryAnalysis.toEntryAnalysis(): EntryAnalysis {
        return EntryAnalysis(
            analysisId = analysisId,
            entryId = entryId,
            externalId = externalId,
            providerId = providerId,
            model = model,
            capturedAt = Instant.fromEpochMilliseconds(capturedAt),
            insightsJson = insightsJson,
            schemaVersion = schemaVersion
        )
    }
}
