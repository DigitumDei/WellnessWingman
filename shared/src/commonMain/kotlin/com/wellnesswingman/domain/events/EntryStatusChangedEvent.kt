package com.wellnesswingman.domain.events

import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.analysis.DetectedWeight

/**
 * Event emitted when an entry's processing status changes.
 */
data class EntryStatusChangedEvent(
    val entryId: Long,
    val status: ProcessingStatus,
    val detectedWeight: DetectedWeight? = null
)
