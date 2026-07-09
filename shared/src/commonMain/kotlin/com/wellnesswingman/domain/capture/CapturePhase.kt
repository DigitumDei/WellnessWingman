package com.wellnesswingman.domain.capture

import kotlinx.serialization.Serializable

/**
 * Phase of the in-progress photo capture workflow.
 *
 * Persisted as part of [PendingCapture] so the workflow can be reconciled
 * after an Android configuration change (activity recreation) or process death.
 */
@Serializable
enum class CapturePhase {
    /** Photo has been captured/selected and is awaiting user confirmation. */
    CAPTURED,

    /** Entry creation is in flight (normalization, persistence, analysis queueing). */
    PROCESSING,

    /** Entry has been committed and analysis has been queued (or marked skipped). */
    DONE
}
