# Testing Guide

## Test Suite Overview

WellnessWingman includes comprehensive unit tests using kotlin.test for multiplatform testing.

### Test Files Created

**Domain Layer Tests:**
- `DailyTotalsCalculatorTest.kt` - Tests for nutrition totals calculation
- `DateTimeUtilTest.kt` - Tests for date/time utility functions

**Data Layer Tests:**
- `SqlDelightTrackedEntryRepositoryTest.kt` - Repository tests with in-memory SQLite
- `TrackedEntryTest.kt` - Data model tests
- `MealAnalysisResultTest.kt` - JSON serialization tests for analysis results

### Running Tests

#### Prerequisites

**Java Version Requirement:**
- Tests require **Java 17 LTS** or **Java 21 LTS**
- Current system has Java 25 (early access), which is not yet supported by Kotlin 2.x
- To install Java 17:
  - **Windows**: Download from [Adoptium](https://adoptium.net/temurin/releases/)
  - **Using Chocolatey**: `choco install openjdk17`
  - **Using Scoop**: `scoop install openjdk17`

#### Running the Test Suite

Once Java 17/21 is installed:

```bash
# Run all tests
./gradlew test

# Run shared module tests
./gradlew :shared:testDebugUnitTest

# Run with coverage report (when Kover is re-enabled)
./gradlew koverHtmlReport
```

### Test Coverage

Current test coverage includes:
- ✅ Business logic (DailyTotalsCalculator)
- ✅ Utilities (DateTimeUtil)
- ✅ Data models and serialization
- ✅ Repository layer with in-memory database
- ⏳ ViewModels (TODO - requires MockK setup)
- ⏳ Use cases/Orchestrators (TODO)

### Known Issues

1. **Java 25 Compatibility**
   - Kotlin 2.1.0/2.2.0 doesn't recognize Java 25
   - Error: `java.lang.IllegalArgumentException: 25`
   - **Solution**: Install Java 17 or 21

2. **SQLDelight Reserved Keywords**
   - Fixed: Changed `count` column alias to `entry_count` in SQL queries

3. **Gradle Wrapper**
   - ✅ Successfully generated with Gradle 9.3.0
   - ✅ iOS targets temporarily disabled for initial setup
   - Can be re-enabled after Gradle/Java version stabilization

### Future Test Improvements

- Add ViewModel tests using Turbine for Flow testing
- Add integration tests for AnalysisOrchestrator
- Set up Maestro E2E tests (Task #22)
- Re-enable Kover code coverage reporting (Task #21)
- Add UI tests using Compose Testing framework

### Dependencies

Test dependencies are configured in `shared/build.gradle.kts`:
- `kotlin-test` - Multiplatform test framework
- `coroutines-test` - Testing utilities for coroutines
- `mockk` - Mocking framework
- `turbine` - Flow testing library
- `sqldelight-driver-jdbc` - In-memory SQLite for tests

## Project Status

- **Overall Completion**: 79.2% (19/24 tasks)
- **Phase 3 (UI Layer)**: 100% complete
- **Phase 5 (Testing)**: Partially complete (test code written, requires Java 17/21 to run)

See [PROGRESS.md](./PROGRESS.md) for detailed task breakdown.
