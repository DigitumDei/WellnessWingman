package com.wellnesswingman.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a tracked health entry (meal, exercise, sleep, etc.).
 */
@Serializable
data class TrackedEntry(
    val entryId: Long = 0,
    val externalId: String? = null,
    val entryType: EntryType = EntryType.UNKNOWN,
    val capturedAt: Instant,
    val capturedAtTimeZoneId: String? = null,
    val capturedAtOffsetMinutes: Int? = null,
    val blobPath: String? = null,
    val dataPayload: String = "",
    val dataSchemaVersion: Int = 1,
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,

    /**
     * User-provided notes (text or voice transcription) captured at time of photo submission.
     * This field persists independently of LLM analysis and remains available for corrections.
     */
    val userNotes: String? = null
) {
    /**
     * Returns the local captured time using timezone information.
     * Note: For full implementation, this would need platform-specific timezone handling.
     */
    fun getCapturedAtLocal(): Instant {
        // For now, just return the UTC time
        // TODO: Implement proper timezone conversion using platform-specific APIs
        return capturedAt
    }
}
