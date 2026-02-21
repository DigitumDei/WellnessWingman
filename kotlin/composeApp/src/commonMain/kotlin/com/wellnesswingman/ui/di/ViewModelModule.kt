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
import com.wellnesswingman.ui.screens.weighthistory.WeightHistoryViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Koin module for ViewModels/ScreenModels.
 */
val viewModelModule = module {
    factory {
        MainViewModel(
            trackedEntryRepository = get(),
            entryAnalysisRepository = get(),
            dailySummaryRepository = get(),
            dailySummaryService = get(),
            dailyTotalsCalculator = get(),
            fileSystem = get(),
            pendingCaptureStore = get()
        )
    }
    factoryOf(::SettingsViewModel)
    factoryOf(::WeightHistoryViewModel)
    factoryOf(::DailySummaryViewModel)
    factoryOf(::PhotoReviewViewModel)
    factoryOf(::CalendarViewModel)
    factory {
        WeekViewModel(
            trackedEntryRepository = get(),
            weeklySummaryService = get()
        )
    }
    factoryOf(::YearViewModel)
    factory {
        DayDetailViewModel(
            trackedEntryRepository = get(),
            entryAnalysisRepository = get(),
            dailySummaryRepository = get(),
            dailySummaryService = get(),
            dailyTotalsCalculator = get(),
            fileSystem = get()
        )
    }
    factory { params ->
        EntryDetailViewModel(
            entryId = params.get(),
            trackedEntryRepository = get(),
            entryAnalysisRepository = get(),
            fileSystem = get(),
            backgroundAnalysisService = get(),
            statusChangeNotifier = get(),
            audioRecordingService = get(),
            llmClientFactory = get()
        )
    }
}
