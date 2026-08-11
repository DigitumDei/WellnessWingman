package com.wellnesswingman.ui.screens.calendar.day

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DayDetailStateLogicTest {

    @Test
    fun `polar-only day does not show empty state`() {
        assertFalse(shouldShowEmptyDayState(entryCount = 0, polarHasData = true))
    }

    @Test
    fun `day with no entries and no Polar data shows empty state`() {
        assertTrue(shouldShowEmptyDayState(entryCount = 0, polarHasData = false))
    }

    @Test
    fun `Polar data enables summary inputs without completed meals`() {
        assertTrue(hasDaySummaryInputs(hasCompletedMeals = false, polarHasData = true))
    }

    @Test
    fun `a day holding only a check-in is not empty`() {
        assertFalse(
            shouldShowEmptyDayState(entryCount = 0, polarHasData = false, checkInSlotCount = 1)
        )
    }

    @Test
    fun `an answered check-in enables summary inputs on its own`() {
        assertTrue(
            hasDaySummaryInputs(
                hasCompletedMeals = false,
                polarHasData = false,
                hasAnsweredCheckIn = true
            )
        )
    }

    @Test
    fun `a waiting prompt alone does not enable summary inputs`() {
        assertFalse(
            hasDaySummaryInputs(
                hasCompletedMeals = false,
                polarHasData = false,
                hasAnsweredCheckIn = false
            )
        )
    }

    @Test
    fun `summary action copy reflects mixed tracked and Polar inputs`() {
        assertTrue(
            daySummaryActionDescription(hasTrackedEntries = true, hasPolarData = true)
                .contains("tracked entries and synced Polar data")
        )
    }
}
