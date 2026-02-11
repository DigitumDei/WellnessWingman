package com.wellnesswingman.di

import com.wellnesswingman.domain.analysis.AnalysisOrchestrator
import com.wellnesswingman.domain.analysis.BackgroundAnalysisService
import com.wellnesswingman.domain.analysis.DailySummaryService
import com.wellnesswingman.domain.analysis.WeeklySummaryService
import com.wellnesswingman.domain.analysis.DailyTotalsCalculator
import com.wellnesswingman.domain.analysis.DefaultBackgroundAnalysisService
import com.wellnesswingman.domain.analysis.DefaultStaleEntryRecoveryService
import com.wellnesswingman.domain.analysis.StaleEntryRecoveryService
import com.wellnesswingman.domain.events.DefaultStatusChangeNotifier
import com.wellnesswingman.domain.events.StatusChangeNotifier
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.migration.DataMigrationService
import com.wellnesswingman.domain.migration.DefaultDataMigrationService
import com.wellnesswingman.domain.navigation.CalendarNavigationService
import com.wellnesswingman.domain.navigation.HistoricalNavigationContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin module for domain layer dependencies (business logic).
 */
val domainModule = module {
    // Calculators and utilities
    singleOf(::DailyTotalsCalculator)

    // LLM
    singleOf(::LlmClientFactory)

    // Event system
    single<StatusChangeNotifier> { DefaultStatusChangeNotifier() }

    // Navigation
    single { HistoricalNavigationContext() }
    single { CalendarNavigationService(get()) }

    // Services
    singleOf(::AnalysisOrchestrator)
    singleOf(::DailySummaryService)
    singleOf(::WeeklySummaryService)

    // Background services
    single<BackgroundAnalysisService> {
        DefaultBackgroundAnalysisService(
            trackedEntryRepository = get(),
            entryAnalysisRepository = get(),
            analysisOrchestrator = get(),
            backgroundExecutionService = get(),
            statusChangeNotifier = get()
        )
    }

    single<StaleEntryRecoveryService> {
        DefaultStaleEntryRecoveryService(
            trackedEntryRepository = get()
        )
    }

    // Data migration
    single<DataMigrationService> {
        DefaultDataMigrationService(
            trackedEntryRepository = get(),
            entryAnalysisRepository = get(),
            dailySummaryRepository = get(),
            fileSystem = get(),
            zipUtil = get()
        )
    }
}
