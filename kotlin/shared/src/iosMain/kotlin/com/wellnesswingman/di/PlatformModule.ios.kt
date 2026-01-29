package com.wellnesswingman.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.wellnesswingman.data.db.DriverFactory
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
}
