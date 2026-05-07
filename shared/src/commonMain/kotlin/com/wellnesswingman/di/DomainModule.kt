package com.wellnesswingman.di

import com.wellnesswingman.domain.analysis.AnalysisOrchestrator
import com.wellnesswingman.domain.analysis.BackgroundAnalysisService
import com.wellnesswingman.domain.analysis.DailySummaryService
import com.wellnesswingman.domain.analysis.WeeklySummaryService
import com.wellnesswingman.domain.analysis.DailyTotalsCalculator
import com.wellnesswingman.domain.analysis.DefaultBackgroundAnalysisService
import com.wellnesswingman.domain.analysis.DefaultStaleEntryRecoveryService
import com.wellnesswingman.domain.analysis.NutritionLabelAnalyzer
import com.wellnesswingman.domain.analysis.NutritionLabelAnalyzing
import com.wellnesswingman.domain.analysis.StaleEntryRecoveryService
import com.wellnesswingman.domain.capture.PendingCaptureStore
import com.wellnesswingman.domain.events.DefaultStatusChangeNotifier
import com.wellnesswingman.domain.events.StatusChangeNotifier
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.ToolRegistry
import com.wellnesswingman.domain.migration.DataMigrationService
import com.wellnesswingman.domain.migration.DefaultDataMigrationService
import com.wellnesswingman.domain.navigation.CalendarNavigationService
import com.wellnesswingman.domain.navigation.HistoricalNavigationContext
import com.wellnesswingman.domain.oauth.PendingOAuthResultStore
import com.wellnesswingman.domain.polar.PolarInsightService
import com.wellnesswingman.domain.polar.PolarSyncOrchestrator
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin module for domain layer dependencies (business logic).
 */
val domainModule = module {
    // Calculators and utilities
    singleOf(::DailyTotalsCalculator)

    // Capture recovery
    singleOf(::PendingCaptureStore)

    // OAuth
    singleOf(::PendingOAuthResultStore)
    singleOf(::PolarInsightService)
    singleOf(::PolarSyncOrchestrator)

    // LLM
    singleOf(::LlmClientFactory)
    singleOf(::NutritionLabelAnalyzer) { bind<NutritionLabelAnalyzing>() }
    single {
        ToolRegistry(
            trackedEntryRepository = get(),
            entryAnalysisRepository = get(),
            weightHistoryRepository = get(),
            appSettingsRepository = get(),
            nutritionalProfileRepository = get()
        )
    }

    // Event system
    single<StatusChangeNotifier> { DefaultStatusChangeNotifier() }

    // Navigation
    single { HistoricalNavigationContext() }
    single { CalendarNavigationService(get()) }

    // Services
    single {
        AnalysisOrchestrator(
            trackedEntryRepository = get(),
            entryAnalysisRepository = get(),
            llmClientFactory = get(),
            toolRegistry = get(),
            fileSystem = get(),
            appSettingsRepository = get()
        )
    }
    single {
        DailySummaryService(
            trackedEntryRepository = get(),
            entryAnalysisRepository = get(),
            dailySummaryRepository = get(),
            llmClientFactory = get(),
            toolRegistry = get(),
            dailyTotalsCalculator = get(),
            weightHistoryRepository = get(),
            polarInsightService = get()
        )
    }
    single {
        WeeklySummaryService(
            trackedEntryRepository = get(),
            weeklySummaryRepository = get(),
            dailySummaryRepository = get(),
            llmClientFactory = get(),
            toolRegistry = get(),
            weightHistoryRepository = get(),
            polarInsightService = get()
        )
    }

    // Background services
    single<BackgroundAnalysisService> {
        DefaultBackgroundAnalysisService(
            trackedEntryRepository = get(),
            entryAnalysisRepository = get(),
            analysisOrchestrator = get(),
            backgroundExecutionService = get(),
            statusChangeNotifier = get(),
            weightHistoryRepository = get(),
            appSettingsRepository = get()
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
            nutritionalProfileRepository = get(),
            dailySummaryRepository = get(),
            weeklySummaryRepository = get(),
            appSettingsRepository = get(),
            weightHistoryRepository = get(),
            fileSystem = get(),
            zipUtil = get()
        )
    }

    // Media
    single { com.wellnesswingman.domain.media.ImageRetentionService(get(), get(), get(), get()) }
}
