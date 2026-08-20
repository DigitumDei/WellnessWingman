package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.EntryAnalysis
import kotlinx.datetime.Instant

/**
 * Repository interface for entry analyses.
 */
interface EntryAnalysisRepository {
    suspend fun getAllAnalyses(): List<EntryAnalysis>
    suspend fun getAnalysisById(id: Long): EntryAnalysis?
    suspend fun getAnalysisByExternalId(externalId: String): EntryAnalysis?
    suspend fun getAnalysesForEntry(entryId: Long): List<EntryAnalysis>
    suspend fun getLatestAnalysisForEntry(entryId: Long): EntryAnalysis?

    /**
     * Returns the latest analysis of each TrackedEntry captured in the
     * half-open range [startInclusive, endExclusive). At most one analysis
     * per parent entry; entries without an analysis are omitted. The latest
     * analysis is resolved by capturedAt descending, then analysisId
     * descending.
     *
     * Membership is determined by the parent entry's capturedAt — never by
     * the analysis's own capturedAt — so an in-range entry whose re-analysis
     * occurred after the range still returns its latest analysis.
     *
     * Default returns an empty list so test fakes that do not exercise this
     * path keep compiling without an override.
     */
    suspend fun getLatestAnalysesForEntries(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<EntryAnalysis> = emptyList()

    suspend fun insertAnalysis(analysis: EntryAnalysis): Long

    suspend fun updateAnalysis(id: Long, insightsJson: String, schemaVersion: String)
    suspend fun deleteAnalysis(id: Long)
    suspend fun deleteAnalysesForEntry(entryId: Long)
    suspend fun upsertAnalysis(analysis: EntryAnalysis)
}
