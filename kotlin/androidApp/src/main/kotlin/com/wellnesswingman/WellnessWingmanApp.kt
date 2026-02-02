package com.wellnesswingman

import android.app.Application
import com.wellnesswingman.di.getSharedModules
import com.wellnesswingman.di.platformModule
import com.wellnesswingman.platform.LogBuffer
import com.wellnesswingman.ui.di.viewModelModule
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class WellnessWingmanApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Napier logging with log buffer
        // LogBuffer handles both storing logs and outputting to Logcat
        Napier.base(LogBuffer.getInstance())

        // Initialize Koin dependency injection
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@WellnessWingmanApp)
            modules(
                getSharedModules() +
                platformModule +
                viewModelModule
            )
        }

        Napier.i("WellnessWingman app initialized")
    }
}
