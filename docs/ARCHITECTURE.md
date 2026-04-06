# WellnessWingman Architecture

## Overview

WellnessWingman is an Android-first Kotlin Multiplatform application organized around a shared `shared/` module, a shared Compose UI module, and an Android app shell. The repository also contains an `iosApp/` host project and a standalone Polar OAuth broker backend.

## Modules

- `shared/`
  - shared models, repositories, SQLDelight schemas/migrations, platform abstractions, LLM clients, Polar sync logic, summary services, media retention, and export/import support
- `composeApp/`
  - shared Compose UI, Voyager navigation, screen models, and presentation logic
- `androidApp/`
  - Android manifest, application startup, Koin bootstrap, deep-link/share-intent handling, and WorkManager jobs
- `iosApp/`
  - Xcode host app for the shared Kotlin framework
- `polar-oauth-broker/`
  - Python broker plus Terraform for Polar OAuth callback, session redemption, and token refresh

## Layers

### Data layer

Lives mostly under `shared/src/commonMain/kotlin/com/wellnesswingman/data/`.

Responsibilities:

- SQLDelight-backed repositories for entries, analyses, daily summaries, weekly summaries, weight history, nutritional profiles, and Polar sync state
- app settings and secure-token storage abstractions
- Polar API client and OAuth repository
- serialized models for LLM, export/import, and Polar payloads

### Domain layer

Lives mostly under `shared/src/commonMain/kotlin/com/wellnesswingman/domain/`.

Responsibilities:

- analysis orchestration for meal, sleep, and exercise entries
- daily and weekly summary generation
- LLM provider abstraction and tool registry
- nutrition-label analysis
- Polar sync orchestration, diagnostics, and insight generation
- stale-entry recovery, pending capture state, data migration, navigation helpers, and image retention

### UI layer

Lives in `composeApp/src/commonMain/kotlin/com/wellnesswingman/ui/`.

Responsibilities:

- entry list and entry-detail flows
- calendar/day/week/year views
- daily summary presentation
- sectioned settings navigation
- Polar settings
- nutritional profile management and nutrition-label scanning
- weight history logging
- diagnostics and data-management screens

### Platform layer

Platform abstractions are defined in `shared/src/commonMain/kotlin/com/wellnesswingman/platform/` and implemented in source-set-specific code.

Current Android-specific responsibilities include:

- secure settings storage
- camera and file handling
- audio recording
- diagnostics/log sharing
- WorkManager background jobs
- deep-link and share-intent integration

## Dependency Injection

Koin modules in `shared/src/commonMain/kotlin/com/wellnesswingman/di/` define the shared graph:

- `dataModule`: repositories, database, settings-backed storage, Polar repository/client
- `domainModule`: orchestrators, summaries, tool registry, migration, Polar services, image retention, and background-analysis services
- `platformModule`: platform-specific implementations

`androidApp` adds Android startup wiring and supplies `PolarOAuthConfig` from `local.properties`-backed `BuildConfig` fields.

## Persistence

SQLDelight schemas and migrations live under:

`shared/src/commonMain/sqldelight/com/wellnesswingman/db/`

The database version is currently `7`. Migrations exist for versions:

- `1.sqm`
- `2.sqm`
- `3.sqm`
- `4.sqm`
- `5.sqm`
- `6.sqm`
- `7.sqm`

## Android Runtime Flow

At app startup:

1. `WellnessWingmanApp` initializes Napier persistent logging and Koin.
2. stale processing entries are recovered
3. any pending Polar OAuth redemption is retried
4. periodic WorkManager jobs are enqueued for image retention and Polar sync

At activity level:

- `MainActivity` accepts Android image-share intents and converts them into pending captures
- OAuth callbacks arrive through `wellnesswingman://oauth/result`
- the Compose app launches into `MainScreen` and navigates from there via Voyager

## Current Constraints

- Android is the practical first-class target in this repo today
- Gradle iOS targets are commented out in build scripts pending a compatible Kotlin/Gradle path
- some operational flows are intentionally Android-only at runtime, especially WorkManager scheduling

## Historical Context

`MIGRATION_ANALYSIS.md` remains as archival documentation of the earlier MAUI-to-Kotlin migration. It is not a guide to the currently supported runtime architecture.
