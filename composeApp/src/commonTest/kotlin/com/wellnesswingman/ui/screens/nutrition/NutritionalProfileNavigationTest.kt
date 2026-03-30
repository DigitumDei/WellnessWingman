package com.wellnesswingman.ui.screens.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NutritionalProfileNavigationTest {

    @Test
    fun `profileIdParameter maps create flow to sentinel`() {
        assertEquals(NEW_NUTRITIONAL_PROFILE_ID, profileIdParameter(null))
    }

    @Test
    fun `profileIdParameter preserves existing profile id`() {
        assertEquals(42L, profileIdParameter(42L))
    }

    @Test
    fun `profileIdFromParameter maps sentinel back to null`() {
        assertNull(profileIdFromParameter(NEW_NUTRITIONAL_PROFILE_ID))
    }

    @Test
    fun `profileIdFromParameter preserves existing profile id`() {
        assertEquals(42L, profileIdFromParameter(42L))
    }
}
