package com.wellnesswingman.di

import org.koin.core.module.Module

/**
 * All shared modules that should be loaded in the Koin container.
 * Platform-specific modules should be added separately.
 */
fun getSharedModules(): List<Module> = listOf(
    dataModule,
    domainModule
)
