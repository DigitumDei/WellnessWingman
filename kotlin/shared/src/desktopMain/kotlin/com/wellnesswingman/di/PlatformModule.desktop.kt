package com.wellnesswingman.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.wellnesswingman.data.db.DriverFactory
import org.koin.dsl.module
import java.util.prefs.Preferences

/**
 * Desktop (JVM) specific Koin module.
 */
val platformModule = module {
    // DriverFactory - no context needed on Desktop
    single { DriverFactory() }

    // Settings - using Java Preferences
    single<Settings> {
        val preferences = Preferences.userRoot().node("com.wellnesswingman")
        PreferencesSettings(preferences)
    }
}
