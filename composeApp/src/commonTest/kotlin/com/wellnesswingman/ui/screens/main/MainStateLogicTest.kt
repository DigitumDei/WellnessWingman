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
}
