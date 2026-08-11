package com.wellnesswingman.ui.screens.settings

import cafe.adriel.voyager.core.model.ScreenModel
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.domain.checkin.CheckInScheduleCalculator
import com.wellnesswingman.domain.checkin.CheckInScheduling
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CheckInSettingsUiState(
    val morningEnabled: Boolean = false,
    val morningTime: String = "07:00",
    val eveningEnabled: Boolean = false,
    val eveningTime: String = "21:00",
    /**
     * False when the platform cannot deliver a notification — most often because
     * POST_NOTIFICATIONS was denied. A toggle that reads "on" while nothing can arrive is worse
     * than no toggle at all, so the screen surfaces this.
     */
    val canDeliverNotifications: Boolean = true
) {
    val anyEnabled: Boolean get() = morningEnabled || eveningEnabled

    val shouldWarnAboutNotifications: Boolean
        get() = anyEnabled && !canDeliverNotifications
}

class CheckInSettingsViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val checkInScheduling: CheckInScheduling
) : ScreenModel {

    private val _uiState = MutableStateFlow(CheckInSettingsUiState())
    val uiState: StateFlow<CheckInSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re-reads settings and notification availability. Called on entry and whenever the screen
     * is resumed, since the user may have changed permission in system settings meanwhile.
     */
    fun refresh() {
        _uiState.value = CheckInSettingsUiState(
            morningEnabled = appSettingsRepository.isMorningCheckInEnabled(),
            morningTime = appSettingsRepository.getMorningCheckInTime(),
            eveningEnabled = appSettingsRepository.isEveningCheckInEnabled(),
            eveningTime = appSettingsRepository.getEveningCheckInTime(),
            canDeliverNotifications = checkInScheduling.canDeliverNotifications()
        )
    }

    fun setMorningEnabled(enabled: Boolean) {
        appSettingsRepository.setMorningCheckInEnabled(enabled)
        _uiState.value = _uiState.value.copy(morningEnabled = enabled)
        reschedule()
    }

    fun setEveningEnabled(enabled: Boolean) {
        appSettingsRepository.setEveningCheckInEnabled(enabled)
        _uiState.value = _uiState.value.copy(eveningEnabled = enabled)
        reschedule()
    }

    /** Ignores unparseable input so a half-typed time cannot disable a check-in. */
    fun setMorningTime(time: String): Boolean {
        if (CheckInScheduleCalculator.parseTimeOfDay(time) == null) return false

        appSettingsRepository.setMorningCheckInTime(time)
        _uiState.value = _uiState.value.copy(morningTime = time)
        reschedule()
        return true
    }

    fun setEveningTime(time: String): Boolean {
        if (CheckInScheduleCalculator.parseTimeOfDay(time) == null) return false

        appSettingsRepository.setEveningCheckInTime(time)
        _uiState.value = _uiState.value.copy(eveningTime = time)
        reschedule()
        return true
    }

    private fun reschedule() {
        checkInScheduling.rescheduleAll()
        _uiState.value = _uiState.value.copy(
            canDeliverNotifications = checkInScheduling.canDeliverNotifications()
        )
    }
}
