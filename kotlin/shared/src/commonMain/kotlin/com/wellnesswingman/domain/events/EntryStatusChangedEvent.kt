package com.wellnesswingman.domain.events

import com.wellnesswingman.data.model.ProcessingStatus

/**
 * Event emitted when an entry's processing status changes.
 */
data class EntryStatusChangedEvent(
    val entryId: Long,
    val status: ProcessingStatus
)
