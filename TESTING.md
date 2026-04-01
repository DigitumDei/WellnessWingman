# Testing Guide

This project’s main automated verification path is the shared desktop/unit suite plus Kover coverage reporting.

## Primary Commands

```bash
# Shared tests used in CI
./gradlew :shared:desktopTest

# XML report for Codecov
./gradlew :shared:koverXmlReport

# Browsable local report
./gradlew :shared:koverHtmlReport

# Recommended local coverage pass
./gradlew :shared:desktopTest :shared:koverHtmlReport

# Full verification pass
./gradlew check
```

## Test Locations

- `shared/src/commonTest/`: shared domain, repository, serialization, Polar, LLM, migration, and media tests
- `composeApp/src/commonTest/`: shared presentation and view-model/state logic tests

## Coverage

- XML report: `shared/build/reports/kover/report.xml`
- HTML report: `shared/build/reports/kover/html/index.html`
- Current Kover verification baseline in Gradle: `25%`
- Team target documented in repository guidance: `80%` line coverage and `70%` branch coverage for production code

The docs and the Gradle configuration are intentionally distinguished here: the repository guidance describes the desired target, while the current enforced Gradle minimum is still lower.

## What Is Covered Today

The current suites cover:

- SQLDelight repository behavior
- analysis orchestration and summary services
- OpenAI and Gemini client behavior
- tool registry behavior
- nutrition-label analysis and nutrition-profile flows
- Polar OAuth, API client, diagnostics, insights, and sync orchestration
- image retention and data migration services
- Compose state logic for calendar, main screen, and nutrition workflows

## Environment Requirements

- JDK 17 or newer
- `JAVA_HOME` configured, or `java` available on `PATH`

Without Java, Gradle will fail before configuration. In this workspace, `java` is currently unavailable, so the commands above could not be executed during this documentation update.

## Practical Workflow

For most changes:

```bash
./gradlew :shared:desktopTest
```

Before a PR that touches shared logic:

```bash
./gradlew :shared:desktopTest :shared:koverXmlReport :shared:koverHtmlReport
```

When touching multiple modules or build logic:

```bash
./gradlew check
```

## Manual Verification Areas

Automated tests do not replace manual checks for:

- Android camera and share-intent capture flows
- notification and foreground-service behavior
- Polar OAuth browser redirect flow
- WorkManager-triggered image retention and Polar sync
- export/import UX and diagnostics sharing
