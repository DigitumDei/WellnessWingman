package com.wellnesswingman.ui.di

import com.wellnesswingman.ui.screens.calendar.CalendarViewModel
import com.wellnesswingman.ui.screens.calendar.WeekViewModel
import com.wellnesswingman.ui.screens.calendar.YearViewModel
import com.wellnesswingman.ui.screens.calendar.day.DayDetailViewModel
import com.wellnesswingman.ui.screens.detail.EntryDetailViewModel
import com.wellnesswingman.ui.screens.main.MainViewModel
import com.wellnesswingman.ui.screens.photo.PhotoReviewViewModel
import com.wellnesswingman.ui.screens.settings.SettingsViewModel
import com.wellnesswingman.ui.screens.summary.DailySummaryViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Koin module for ViewModels/ScreenModels.
 */
val viewModelModule = module {
    factoryOf(::MainViewModel)
    factoryOf(::SettingsViewModel)
    factoryOf(::DailySummaryViewModel)
    factoryOf(::PhotoReviewViewModel)
    factoryOf(::CalendarViewModel)
    factoryOf(::WeekViewModel)
    factoryOf(::YearViewModel)
    factoryOf(::DayDetailViewModel)
    factory { params ->
        EntryDetailViewModel(
            entryId = params.get(),
            trackedEntryRepository = get(),
            entryAnalysisRepository = get(),
            fileSystem = get(),
            backgroundAnalysisService = get(),
            statusChangeNotifier = get()
        )
    }
}
