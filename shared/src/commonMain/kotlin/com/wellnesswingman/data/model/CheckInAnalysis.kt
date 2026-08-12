package com.wellnesswingman.data.model

import com.wellnesswingman.data.model.analysis.CheckInAnalysisStatus
import com.wellnesswingman.data.model.analysis.CheckInFacets
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * One attempt at extracting structure from a check-in's free text.
 *
 * Stored against the day and slot rather than the check-in's row id, because saving a check-in
 * is an `INSERT OR REPLACE` that mints a new row id each time an answer is revised.
 */
data class CheckInAnalysis(
    val analysisId: Long = 0,
    val externalId: String? = null,

    val checkInDate: LocalDate,
    val slot: CheckInSlot,

    val status: CheckInAnalysisStatus = CheckInAnalysisStatus.PENDING,

    /** Which provider and model produced this, so results can be compared as models change. */
    val providerId: String = "",
    val model: String = "",

    val analyzedAt: Instant,

    /** Null until the attempt succeeds. */
    val facets: CheckInFacets? = null,

    /** Set only when [status] is [CheckInAnalysisStatus.FAILED]. */
    val errorMessage: String? = null,

    val schemaVersion: String = "1.0"
) {
    val isPending: Boolean get() = status == CheckInAnalysisStatus.PENDING
    val hasFailed: Boolean get() = status == CheckInAnalysisStatus.FAILED

    /**
     * Facets only when the extraction actually finished.
     *
     * Guards against a half-written analysis being read as though it were complete — a pending
     * row has no food in it yet, which is not the same as a day with no extra food.
     */
    val completedFacets: CheckInFacets?
        get() = if (status == CheckInAnalysisStatus.COMPLETED) facets else null
}
