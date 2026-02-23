package com.wellnesswingman.domain.capture

import kotlinx.serialization.Serializable

/**
 * Represents a photo capture that is in-progress or was interrupted by process death.
 * Persisted to disk so the capture can be recovered when the app restarts.
 */
@Serializable
data class PendingCapture(
    val photoFilePath: String,
    val capturedAtMillis: Long
)
