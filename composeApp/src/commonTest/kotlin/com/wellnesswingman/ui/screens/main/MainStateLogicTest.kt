package com.wellnesswingman.ui.screens.main

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainStateLogicTest {

    @Test
    fun `shows success state when polar data exists without entries`() {
        assertFalse(shouldShowEmptyMainState(entryCount = 0, polarHasData = true))
    }

    @Test
    fun `shows empty state when neither entries nor polar data exist`() {
        assertTrue(shouldShowEmptyMainState(entryCount = 0, polarHasData = false))
    }

    @Test
    fun `allows summary actions when polar data exists without meals`() {
        assertTrue(hasMainSummaryInputs(hasCompletedMeals = false, polarHasData = true))
    }

    @Test
    fun `a day holding only a check-in is not empty`() {
        assertFalse(
            shouldShowEmptyMainState(entryCount = 0, polarHasData = false, checkInSlotCount = 1)
        )
    }

    @Test
    fun `allows summary actions when only a check-in was answered`() {
        // DailySummaryService generates from a check-in alone; without this the action card
        // stays hidden and that path is unreachable from the UI.
        assertTrue(
            hasMainSummaryInputs(
                hasCompletedMeals = false,
                polarHasData = false,
                hasAnsweredCheckIn = true
            )
        )
    }

    @Test
    fun `a waiting prompt alone does not enable summary actions`() {
        // An unanswered slot is an invitation, not content to summarise.
        assertFalse(
            hasMainSummaryInputs(
                hasCompletedMeals = false,
                polarHasData = false,
                hasAnsweredCheckIn = false
            )
        )
    }
}
