package com.wellnesswingman.data.repository

import com.russhwolf.settings.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsAppSettingsRepositoryGoalsTest {

    private lateinit var settings: Settings
    private lateinit var repository: SettingsAppSettingsRepository

    @BeforeTest
    fun setUp() {
        settings = mockk(relaxed = true)
        repository = SettingsAppSettingsRepository(settings)
    }

    @Test
    fun `unset or blank goals read as null`() {
        every { settings.getStringOrNull("profile_goals_and_preferences") } returns "   "

        assertNull(repository.getGoalsAndPreferences())
    }

    @Test
    fun `stored nonblank goals are returned`() {
        every { settings.getStringOrNull("profile_goals_and_preferences") } returns "  build strength  "

        assertEquals("  build strength  ", repository.getGoalsAndPreferences())
    }

    @Test
    fun `setter trims and caps goals before persisting`() {
        val value = "  ${"x".repeat(MAX_GOALS_AND_PREFERENCES_LENGTH + 25)}  "

        repository.setGoalsAndPreferences(value)

        verify {
            settings.putString(
                "profile_goals_and_preferences",
                "x".repeat(MAX_GOALS_AND_PREFERENCES_LENGTH)
            )
        }
    }

    @Test
    fun `blank goals remove the stored value`() {
        repository.setGoalsAndPreferences("  \n")

        verify { settings.remove("profile_goals_and_preferences") }
    }

    @Test
    fun `clearProfileData removes goals`() {
        repository.clearProfileData()

        verify { settings.remove("profile_goals_and_preferences") }
    }
}
