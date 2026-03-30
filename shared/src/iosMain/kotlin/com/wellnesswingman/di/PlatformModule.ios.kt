package com.wellnesswingman.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.wellnesswingman.data.db.DriverFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.BackgroundExecutionService
import com.wellnesswingman.platform.CameraCaptureOperations
import com.wellnesswingman.platform.CameraCaptureService
import com.wellnesswingman.platform.DiagnosticShare
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.platform.FileSystemOperations
import com.wellnesswingman.platform.IosBackgroundExecutionService
import com.wellnesswingman.platform.ShareUtil
import com.wellnesswingman.platform.ZipOperations
import com.wellnesswingman.platform.ZipUtil
import org.koin.dsl.bind
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
    single { FileSystem() } bind FileSystemOperations::class
    single { CameraCaptureService() } bind CameraCaptureOperations::class
    single { AudioRecordingService() }
    single { DiagnosticShare() }
    single { ZipUtil() } bind ZipOperations::class
    single { ShareUtil() }

    // Background execution service (stub on iOS)
    single<BackgroundExecutionService> { IosBackgroundExecutionService() }
}
