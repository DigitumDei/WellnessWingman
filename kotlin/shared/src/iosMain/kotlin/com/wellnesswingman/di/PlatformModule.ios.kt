package com.wellnesswingman.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.wellnesswingman.data.db.DriverFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.BackgroundExecutionService
import com.wellnesswingman.platform.DiagnosticShare
import com.wellnesswingman.platform.IosBackgroundExecutionService
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

/**
 * iOS-specific Koin module.
 */
val platformModule = module {
    // DriverFactory - no context needed on iOS
    single { DriverFactory() }

    // Settings - using NSUserDefaults
    // For production, consider using Keychain for sensitive data
    single<Settings> {
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    }

    // Platform services
    single { AudioRecordingService() }
    single { DiagnosticShare() }

    // Background execution service (stub on iOS)
    single<BackgroundExecutionService> { IosBackgroundExecutionService() }
}
