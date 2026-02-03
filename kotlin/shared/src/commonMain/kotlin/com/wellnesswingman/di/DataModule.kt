package com.wellnesswingman.di

import com.russhwolf.settings.Settings
import com.wellnesswingman.data.db.DriverFactory
import com.wellnesswingman.data.repository.*
import com.wellnesswingman.db.WellnessWingmanDatabase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin module for data layer dependencies.
 */
val dataModule = module {
    // Database
    single<WellnessWingmanDatabase> {
        val driver = get<DriverFactory>().createDriver()
        WellnessWingmanDatabase(driver)
    }

    // Repositories
    single<TrackedEntryRepository> {
        SqlDelightTrackedEntryRepository(get())
    }

    single<EntryAnalysisRepository> {
        SqlDelightEntryAnalysisRepository(get())
    }

    single<DailySummaryRepository> {
        SqlDelightDailySummaryRepository(get())
    }

    single<WeeklySummaryRepository> {
        SqlDelightWeeklySummaryRepository(get())
    }

    single<AppSettingsRepository> {
        SettingsAppSettingsRepository(get())
    }

    // Settings - platform-specific implementation will be provided
    // via platformModule in each platform's source set
}
