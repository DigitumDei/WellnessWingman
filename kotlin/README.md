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

- **Language:** Kotlin 1.9.22
- **UI Framework:** Compose Multiplatform 1.6.0
- **Database:** SQLDelight 2.0.1
- **Dependency Injection:** Koin 3.5.3
- **HTTP Client:** Ktor 2.3.8
- **LLM Integration:** openai-kotlin 3.7.0
- **Navigation:** Voyager 1.0.0
- **Logging:** Napier 2.7.1
- **Testing:** kotlin.test, MockK, Turbine
- **Code Coverage:** Kover 0.7.5

## Current Implementation Status

### ✅ Completed (17 of 24 tasks - 70.8%)

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

#### **Phase 3: UI Layer (71% Complete)**
11. **Compose UI Theme** - Material3 theme with light/dark modes
12. **Navigation** - Voyager setup with screen transitions
13. **MainScreen** - Entry list with pull-to-refresh
14. **SettingsScreen** - API key configuration and provider selection
15. **Detail Screens** - Unified entry detail view for Meal/Exercise/Sleep
18. **DailySummaryScreen** - Daily summary generation and display
19. **Android App Module** - MainActivity and Application class with full DI

### 🚧 Pending (7 of 24 tasks - Optional)

16. **PhotoReviewScreen** - Photo capture and review UI
17. **Calendar Views** - Week, Month, Year, Day timeline views
20. **Unit Tests** - Port tests to kotlin.test
21. **Code Coverage** - Kover setup
22. **E2E Tests** - Maestro flows
23. **iOS App Module** - Basic iOS setup

## Building the Project

### Prerequisites

- JDK 11 or higher
- Android Studio (for Android development)
- Xcode (for iOS development, macOS only)

### Build Commands

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Generate code coverage report
./gradlew koverHtmlReport

# Build Android app
./gradlew :androidApp:assembleDebug
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
- **UI Layer:** Compose Multiplatform screens and components (to be implemented)

## Migration from .NET MAUI

This project is a migration from the original .NET MAUI implementation. Key differences:

- C# → Kotlin
- XAML → Compose
- Entity Framework Core → SQLDelight
- .NET DI → Koin
- Platform-specific code using expect/actual pattern

See `MIGRATION_ANALYSIS.md` for full migration documentation.

## Contributing

This is currently a work in progress. The core data and business logic layers are complete,
with UI and platform-specific implementations still in progress.

## License

[Add license information]
