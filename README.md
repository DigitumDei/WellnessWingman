# WellnessWingman - Kotlin Multiplatform

This is the Kotlin Multiplatform (KMP) migration of the WellnessWingman health tracking application.

## Project Structure

```
kotlin/
├── shared/                      # Shared KMP module
│   └── src/
│       ├── commonMain/kotlin/   # Shared business logic
│       │   ├── com/wellnesswingman/
│       │   │   ├── data/        # Data layer
│       │   │   │   ├── db/      # Database drivers
│       │   │   │   ├── model/   # Data models
│       │   │   │   └── repository/  # Repositories
│       │   │   ├── domain/      # Business logic
│       │   │   │   ├── analysis/  # Analysis services
│       │   │   │   └── llm/     # LLM clients
│       │   │   └── util/        # Utilities
│       │   └── sqldelight/      # SQL schemas
│       ├── androidMain/kotlin/  # Android implementations
│       ├── iosMain/kotlin/      # iOS implementations
│       └── desktopMain/kotlin/  # Desktop implementations
├── composeApp/                  # Compose Multiplatform UI
├── androidApp/                  # Android application
├── iosApp/                      # iOS application (Xcode)
└── maestro/                     # E2E tests
```

## Tech Stack

- **Language:** Kotlin 2.2.10
- **UI Framework:** Compose Multiplatform 1.7.0
- **Database:** SQLDelight 2.0.2
- **Dependency Injection:** Koin 3.5.3
- **HTTP Client:** Ktor 2.3.12
- **LLM Integration:** openai-kotlin 3.7.2
- **Navigation:** Voyager 1.1.0-beta02
- **Image Loading:** Coil 2.5.0
- **Logging:** Napier 2.7.1
- **Testing:** kotlin.test, MockK, Turbine
- **Code Coverage:** Kover 0.8.3

## Current Implementation Status

### ✅ Completed (21 of 24 tasks - 87.5%)

#### **Phase 1: Foundation (100% Complete)**
1. **Project Structure** - Full KMP setup with Gradle and modules
2. **Data Models** - All domain models (10+ files)
3. **SQLDelight Schemas** - Database with cross-platform drivers
4. **Repository Layer** - Complete data access layer

#### **Phase 2: Core Business Logic (100% Complete)**
5. **Business Logic Services** - Calculators and utilities
6. **LLM Client Interfaces** - OpenAI and Gemini clients
7. **Analysis Orchestrator** - Entry processing pipeline
8. **Daily Summary Service** - Summary generation with LLM
9. **Platform Services** - FileSystem, Camera, PhotoResizer (expect/actual)
10. **Dependency Injection** - Complete Koin setup

#### **Phase 3: UI Layer (100% Complete)**
11. **Compose UI Theme** - Material3 theme with light/dark modes
12. **Navigation** - Voyager setup with screen transitions
13. **MainScreen** - Entry list with pull-to-refresh
14. **SettingsScreen** - API key configuration and provider selection
15. **Detail Screens** - Unified entry detail view for Meal/Exercise/Sleep
16. **PhotoReviewScreen** - Photo capture and review UI
17. **Calendar Views** - Week, Month, Year, Day timeline views
18. **DailySummaryScreen** - Daily summary generation and display
19. **Android App Module** - MainActivity and Application class with full DI

#### **Phase 4: Testing & Quality (67% Complete)**
20. **Unit Tests** - 39 tests passing with kotlin.test
21. **Code Coverage** - Kover setup with 25.5% baseline coverage
24. **Documentation** - README, RUNNING_THE_APP.md, BUILD_FIXES_NEEDED.md

### 🚧 Pending (3 of 24 tasks - Optional)

22. **E2E Tests** - Maestro flows
23. **iOS App Module** - Blocked by Gradle 9.3 compatibility issues with iOS targets
    - iOS platform implementations exist in `shared/src/iosMain`
    - Targets disabled temporarily pending Kotlin/Gradle compatibility updates

## Building the Project

### Prerequisites

- **JDK 17** (configured in `gradle.properties`)
- **Gradle 9.3.0** (included via wrapper)
- **Android Studio** (recommended for Android development)
- **Xcode** (for iOS development, macOS only - currently disabled)

### Quick Start

See [RUNNING_THE_APP.md](RUNNING_THE_APP.md) for comprehensive guide on:
- Building from command line
- Running in emulator/device
- Debugging in Android Studio
- Viewing logs and troubleshooting

### Build Commands

```bash
# Build Android debug APK
./gradlew :androidApp:assembleDebug

# Run all unit tests (39 tests)
./gradlew :shared:test

# Generate code coverage report (HTML)
./gradlew :shared:test :shared:koverHtmlReport

# View coverage report
# Opens: shared/build/reports/kover/html/index.html

# Verify coverage meets threshold
./gradlew :shared:koverVerify
```

## Database

The app uses SQLDelight for type-safe SQL queries across all platforms.

### Database Location

- **Android:** `/data/data/com.wellnesswingman/databases/wellnesswingman.db`
- **iOS:** App documents directory
- **Desktop:** `~/.wellnesswingman/wellnesswingman.db`

## LLM Integration

The app supports two LLM providers:

1. **OpenAI** (gpt-4o-mini)
   - Image analysis
   - Audio transcription (Whisper)
   - Text completion

2. **Google Gemini** (gemini-1.5-flash)
   - Image analysis
   - Text completion

API keys are stored securely using platform-specific secure storage:
- Android: EncryptedSharedPreferences
- iOS: Keychain
- Desktop: OS-specific credential storage

## Architecture

The project follows clean architecture principles:

- **Data Layer:** Repositories, database access, network clients
- **Domain Layer:** Business logic, use cases, LLM orchestration
- **UI Layer:** Compose Multiplatform screens and components

## Migration History

This repository previously contained a .NET MAUI implementation. That code is no longer shipped or supported here; this section is historical context only. Key migration differences were:

- C# → Kotlin
- XAML → Compose
- Entity Framework Core → SQLDelight
- .NET DI → Koin
- Platform-specific code using expect/actual pattern

See `MIGRATION_ANALYSIS.md` for the archived migration analysis.

## Contributing

This is currently a work in progress. The core data and business logic layers are complete,
with UI and platform-specific implementations still in progress.

## License

MIT License — see [LICENSE.txt](LICENSE.txt) for details.
