package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.EntryAnalysis

/**
 * Repository interface for entry analyses.
 */
interface EntryAnalysisRepository {
    suspend fun getAllAnalyses(): List<EntryAnalysis>
    suspend fun getAnalysisById(id: Long): EntryAnalysis?
    suspend fun getAnalysisByExternalId(externalId: String): EntryAnalysis?
    suspend fun getAnalysesForEntry(entryId: Long): List<EntryAnalysis>
    suspend fun getLatestAnalysisForEntry(entryId: Long): EntryAnalysis?
    suspend fun insertAnalysis(analysis: EntryAnalysis): Long
    suspend fun updateAnalysis(id: Long, insightsJson: String, schemaVersion: String)
    suspend fun deleteAnalysis(id: Long)
    suspend fun deleteAnalysesForEntry(entryId: Long)
}
