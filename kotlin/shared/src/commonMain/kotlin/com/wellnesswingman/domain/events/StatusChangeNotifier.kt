package com.wellnesswingman.domain.events

import com.wellnesswingman.data.model.ProcessingStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Service for notifying subscribers when entry processing status changes.
 * Uses SharedFlow for multi-subscriber support.
 */
interface StatusChangeNotifier {
    /**
     * Flow of status change events. Subscribe to receive updates.
     */
    val statusChanges: SharedFlow<EntryStatusChangedEvent>

    /**
     * Emit a status change event.
     */
    suspend fun notifyStatusChange(entryId: Long, status: ProcessingStatus)
}

/**
 * Default implementation using MutableSharedFlow.
 */
class DefaultStatusChangeNotifier : StatusChangeNotifier {
    private val _statusChanges = MutableSharedFlow<EntryStatusChangedEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    override val statusChanges: SharedFlow<EntryStatusChangedEvent> = _statusChanges.asSharedFlow()

    override suspend fun notifyStatusChange(entryId: Long, status: ProcessingStatus) {
        _statusChanges.emit(EntryStatusChangedEvent(entryId, status))
    }
}
