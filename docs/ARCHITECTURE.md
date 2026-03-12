# WellnessWingman Architecture

## Purpose & Scope
WellnessWingman is a Kotlin Multiplatform application built around a shared data and domain layer, a Compose Multiplatform UI module, and platform entry points for Android and iOS. The active repository targets the Kotlin/Gradle stack only; any prior MAUI implementation is historical and no longer part of the supported architecture.

## Active Modules
- `shared/`: shared domain models, SQLDelight schema, repositories, platform abstractions, and business workflows.
- `composeApp/`: shared Compose UI, navigation, screen models, and reusable presentation components.
- `androidApp/`: Android application entry point, manifest, and Android-specific wiring.
- `iosApp/`: iOS host application that integrates the shared Kotlin framework.

## Layered Structure
- **UI Layer**: Compose screens in `composeApp/src/commonMain/kotlin/com/wellnesswingman/ui/` render entry capture, calendar views, summaries, and settings. Android-specific launch code lives in `androidApp/src/main/kotlin/com/wellnesswingman/`.
- **Domain Layer**: Shared services in `shared/src/commonMain/kotlin/com/wellnesswingman/domain/` coordinate capture state, analysis orchestration, summaries, migration, and navigation.
- **Data Layer**: Repositories in `shared/src/commonMain/kotlin/com/wellnesswingman/data/repository/` persist entries, analyses, summaries, and settings using SQLDelight-backed storage.
- **Platform Layer**: `expect`/`actual` implementations in the source-set-specific `shared/src/*Main/` trees provide filesystem, camera, background execution, audio, sharing, and ZIP support.

## Data Flow
1. Platform capture services create or import raw assets and metadata.
2. Shared repositories persist `TrackedEntry` records and related analysis state via SQLDelight.
3. Domain services orchestrate LLM analysis, status updates, stale-entry recovery, and summary generation.
4. Compose screens consume repository and domain outputs to render the current state across platforms.

## Dependency Injection
Koin modules under `shared/src/commonMain/kotlin/com/wellnesswingman/di/` define the shared dependency graph. Platform modules contribute the required actual implementations, and application entry points load the combined module set during startup.

## Storage & Analysis
- SQLDelight schemas and migrations live under `shared/src/commonMain/sqldelight/com/wellnesswingman/db/`.
- LLM provider abstractions live under `shared/src/commonMain/kotlin/com/wellnesswingman/domain/llm/`.
- Sensitive configuration must stay in secure platform-managed storage and should not be written to plaintext files or logs.

## Historical Context
`MIGRATION_ANALYSIS.md` remains in the repository as archival documentation explaining the move from the former .NET MAUI implementation to Kotlin Multiplatform. It is not an operational guide for the current codebase.
