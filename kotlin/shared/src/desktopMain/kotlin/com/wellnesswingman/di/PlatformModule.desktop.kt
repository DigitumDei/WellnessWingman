package com.wellnesswingman.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.wellnesswingman.data.db.DriverFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.BackgroundExecutionService
import com.wellnesswingman.platform.DesktopBackgroundExecutionService
import com.wellnesswingman.platform.DiagnosticShare
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.platform.ShareUtil
import com.wellnesswingman.platform.ZipUtil
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

    // Platform services
    single { FileSystem() }
    single { AudioRecordingService() }
    single { DiagnosticShare() }
    single { ZipUtil() }
    single { ShareUtil() }

    // Background execution service (no-op on desktop)
    single<BackgroundExecutionService> { DesktopBackgroundExecutionService() }
}
