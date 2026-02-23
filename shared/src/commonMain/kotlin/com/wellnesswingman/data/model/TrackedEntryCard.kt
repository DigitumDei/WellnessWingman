package com.wellnesswingman.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Card representation of a tracked entry for UI display.
 */
@Serializable
data class TrackedEntryCard(
    val entryId: Long,
    val entryType: EntryType,
    val capturedAtUtc: Instant,
    val capturedAtTimeZoneId: String? = null,
    val capturedAtOffsetMinutes: Int? = null,
    val processingStatus: ProcessingStatus,
    val title: String = "",
    val description: String = "",
    val thumbnail: String? = null
) {
    /**
     * Returns true if the entry is clickable (i.e., processing is completed).
     */
    val isClickable: Boolean
        get() = processingStatus == ProcessingStatus.COMPLETED

    /**
     * Returns the local captured time.
     * TODO: Implement proper timezone conversion.
     */
    fun getLocalCapturedAt(): Instant {
        return capturedAtUtc
    }
}
