package com.wellnesswingman.domain.checkin

import com.wellnesswingman.data.model.CheckInSlot
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory bridge between the check-in notification deep link and the UI.
 *
 * Mirrors [com.wellnesswingman.domain.oauth.PendingOAuthResultStore]: MainActivity delivers the
 * slot from the deep link, and the composition consumes it to navigate to the check-in screen.
 */
/** A check-in the user has asked to open, and the day it is about. */
data class PendingCheckIn(
    val slot: CheckInSlot,
    /** Null means today; a notification always names the day it was raised for. */
    val date: LocalDate?
)

class PendingCheckInStore {

    private val _requested = MutableStateFlow<PendingCheckIn?>(null)
    val requested: StateFlow<PendingCheckIn?> = _requested.asStateFlow()

    /**
     * Called from MainActivity when a check-in deep link arrives.
     *
     * @param date the day the notification was raised for. Carried explicitly so that answering
     *   an evening notification the next morning still records against that evening, rather
     *   than whatever day it is when the notification is finally tapped.
     */
    fun request(slot: CheckInSlot, date: LocalDate?) {
        _requested.value = PendingCheckIn(slot, date)
    }

    /**
     * Takes the date as `YYYY-MM-DD` so platform code can pass it straight from a deep link
     * without needing a kotlinx-datetime dependency of its own. An unparseable or absent value
     * means today.
     */
    fun request(slot: CheckInSlot, isoDate: String?) {
        val date = isoDate
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        request(slot, date)
    }

    /**
     * Returns the pending request and clears it, so a configuration change does not navigate to
     * the check-in screen a second time.
     */
    fun consume(): PendingCheckIn? {
        val current = _requested.value
        _requested.value = null
        return current
    }
}
