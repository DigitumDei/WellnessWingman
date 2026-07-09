package com.wellnesswingman.domain.capture

import kotlinx.serialization.Serializable

/**
 * Represents a photo capture that is in-progress or was interrupted by
 * process death or an Android configuration change (activity recreation).
 *
 * Persisted to disk so the capture can be recovered when the app restarts or
 * the photo-review screen is recreated. The persisted file path is the durable
 * source of truth; full image bytes are re-read from it on recovery rather than
 * being retained only in composition state.
 */
@Serializable
data class PendingCapture(
    val photoFilePath: String,
    val capturedAtMillis: Long,
    val notes: String = "",
    val phase: CapturePhase = CapturePhase.CAPTURED,
    val entryId: Long? = null,
    val source: CaptureSource = CaptureSource.CAMERA,
    val apiKeyMissing: Boolean = false
)
