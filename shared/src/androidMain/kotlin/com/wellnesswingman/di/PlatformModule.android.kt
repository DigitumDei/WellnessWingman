package com.wellnesswingman.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.wellnesswingman.data.db.DriverFactory
import com.wellnesswingman.platform.AndroidBackgroundExecutionService
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.BackgroundExecutionService
import com.wellnesswingman.platform.CameraCaptureService
import com.wellnesswingman.platform.DiagnosticLogger
import com.wellnesswingman.platform.DiagnosticShare
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.platform.PhotoResizer
import com.wellnesswingman.platform.ShareUtil
import com.wellnesswingman.platform.ZipUtil
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
    single { AudioRecordingService(get<Context>()) }
    single { PhotoResizer() }
    single { DiagnosticLogger(get<Context>()) }
    single { DiagnosticShare(get<Context>(), get()) }

    // ZIP and sharing
    single { ZipUtil() }
    single { ShareUtil(get<Context>()) }

    // Background execution service
    single<BackgroundExecutionService> { AndroidBackgroundExecutionService(get()) }
}
