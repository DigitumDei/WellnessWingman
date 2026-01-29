package com.wellnesswingman.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.wellnesswingman.data.db.DriverFactory
import com.wellnesswingman.platform.CameraCaptureService
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.platform.PhotoResizer
import org.koin.dsl.module

/**
 * Android-specific Koin module.
 */
val platformModule = module {
    // DriverFactory - requires Android Context
    single { DriverFactory(get<Context>()) }

    // Settings - using SharedPreferences
    // For production, consider using EncryptedSharedPreferences
    single<Settings> {
        val context = get<Context>()
        val sharedPreferences = context.getSharedPreferences(
            "wellnesswingman_prefs",
            Context.MODE_PRIVATE
        )
        SharedPreferencesSettings(sharedPreferences)
    }

    // Platform services
    single { FileSystem(get<Context>()) }
    single { CameraCaptureService(get<Context>()) }
    single { PhotoResizer() }
}
