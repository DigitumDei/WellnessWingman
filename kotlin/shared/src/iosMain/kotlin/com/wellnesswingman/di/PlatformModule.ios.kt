package com.wellnesswingman.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.wellnesswingman.data.db.DriverFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.BackgroundExecutionService
import com.wellnesswingman.platform.DiagnosticShare
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.platform.IosBackgroundExecutionService
import com.wellnesswingman.platform.ShareUtil
import com.wellnesswingman.platform.ZipUtil
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
    single { FileSystem() }
    single { AudioRecordingService() }
    single { DiagnosticShare() }
    single { ZipUtil() }
    single { ShareUtil() }

    // Background execution service (stub on iOS)
    single<BackgroundExecutionService> { IosBackgroundExecutionService() }
}
