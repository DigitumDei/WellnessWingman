package com.wellnesswingman.ui.di

import com.wellnesswingman.ui.screens.detail.EntryDetailViewModel
import com.wellnesswingman.ui.screens.main.MainViewModel
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
    factory { params -> EntryDetailViewModel(params.get(), get(), get()) }
}
