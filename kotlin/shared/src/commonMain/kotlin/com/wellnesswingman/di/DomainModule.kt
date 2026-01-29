package com.wellnesswingman.di

import com.wellnesswingman.domain.analysis.AnalysisOrchestrator
import com.wellnesswingman.domain.analysis.DailySummaryService
import com.wellnesswingman.domain.analysis.DailyTotalsCalculator
import com.wellnesswingman.domain.llm.LlmClientFactory
import org.koin.core.module.dsl.factoryOf
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

    // Services
    singleOf(::AnalysisOrchestrator)
    singleOf(::DailySummaryService)
}
