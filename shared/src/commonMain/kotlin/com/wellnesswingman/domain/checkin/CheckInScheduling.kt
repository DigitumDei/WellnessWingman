package com.wellnesswingman.domain.checkin

/**
 * Platform hook for arming the check-in reminders.
 *
 * The UI lives in common code but alarms and notifications are platform concerns, so this
 * follows the same shape as `BackgroundExecutionService`: a common interface with a real
 * implementation on Android and a no-op elsewhere.
 */
interface CheckInScheduling {

    /** Re-arms both check-ins from current settings. Safe to call repeatedly. */
    fun rescheduleAll()

    /**
     * Whether the platform can actually deliver a check-in notification right now.
     *
     * Distinct from the enabled setting: a user can switch check-ins on and still never see
     * one because notification permission was denied. Settings uses this to say so rather than
     * showing a toggle that appears on but does nothing.
     */
    fun canDeliverNotifications(): Boolean
}

/**
 * Used on platforms without check-in scheduling (desktop, and iOS while it remains deferred).
 * Answers are still capturable in-app; only the reminders are absent.
 */
class NoOpCheckInScheduling : CheckInScheduling {
    override fun rescheduleAll() = Unit
    override fun canDeliverNotifications(): Boolean = false
}
