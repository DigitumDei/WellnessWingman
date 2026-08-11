package com.wellnesswingman.domain.checkin

import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.DailyCheckInRepository
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Supplies the check-in slots a screen should show for a date: answers already given, plus a
 * blank prompt on today once its window has opened.
 *
 * Shared by the Today screen and the calendar's day view so both present check-ins identically
 * — a slot appearing on one and not the other is exactly the sort of inconsistency that makes a
 * feature feel unreliable.
 */
class DayCheckInsProvider(
    private val dailyCheckInRepository: DailyCheckInRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val clock: Clock = Clock.System,
    private val timeZoneProvider: () -> TimeZone = { TimeZone.currentSystemDefault() }
) {

    /**
     * Never throws: a screen should still render its entries if check-ins cannot be read, so a
     * failure degrades to showing none.
     */
    suspend fun slotsFor(date: LocalDate): List<DayCheckInSlot> {
        return try {
            val nowLocal = clock.now().toLocalDateTime(timeZoneProvider())

            DayCheckInPlanner.plan(
                date = date,
                today = nowLocal.date,
                now = nowLocal.time,
                settings = mapOf(
                    CheckInSlot.MORNING to CheckInSlotSetting(
                        enabled = appSettingsRepository.isMorningCheckInEnabled(),
                        timeOfDay = CheckInScheduleCalculator.parseTimeOfDay(
                            appSettingsRepository.getMorningCheckInTime()
                        )
                    ),
                    CheckInSlot.EVENING to CheckInSlotSetting(
                        enabled = appSettingsRepository.isEveningCheckInEnabled(),
                        timeOfDay = CheckInScheduleCalculator.parseTimeOfDay(
                            appSettingsRepository.getEveningCheckInTime()
                        )
                    )
                ),
                checkIns = dailyCheckInRepository.getCheckInsForDate(date)
            )
        } catch (e: Exception) {
            Napier.w("Failed to load check-ins for $date. Continuing without them.", e)
            emptyList()
        }
    }
}
