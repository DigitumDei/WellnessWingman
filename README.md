# WellnessWingman

WellnessWingman is a Kotlin Multiplatform health-tracking app with a shared data/domain layer, a shared Compose UI module, and an Android app entry point. The active repository is the Kotlin codebase; older MAUI migration material remains only as historical reference.

## Current State

- Android is the primary runnable target in this repository.
- Shared modules cover data storage, LLM-driven analysis, daily and weekly summaries, Polar integration, nutrition profile workflows, and export/import support.
- iOS source remains in the repo, but Gradle iOS targets are currently disabled while the project stays aligned with the current Kotlin and Gradle toolchain.
- Polar OAuth uses the included `polar-oauth-broker/` backend so the mobile client never stores the Polar client secret.

## Recent Changes

Merged during March 2026:

- Polar OAuth, API client wiring, sync persistence, refresh orchestration, and insight bridging landed in PRs `#118`, `#119`, `#120`, and `#121`.
- Shared LLM tool calling and summary-service tool registry wiring landed in PRs `#123` and `#125`.
- Nutrition profile persistence, editing, and label-scanning workflow landed in PR `#127`.
- Settings were reorganized into sectioned navigation in PR `#102`.
- Image retention/downsizing background work landed in PR `#93`.
- Several npm dependency security updates also merged during the month.

## Module Layout

- `shared/`: domain models, repositories, SQLDelight schema/migrations, platform abstractions, LLM clients, Polar sync logic, summaries, exports, and media services.
- `composeApp/`: shared Compose UI, Voyager navigation, screen models, and presentation logic.
- `androidApp/`: Android application, manifest, startup wiring, WorkManager jobs, share intents, and OAuth deep-link handling.
- `iosApp/`: Xcode host app for the shared Kotlin framework.
- `polar-oauth-broker/`: Python broker plus Terraform for Polar OAuth token exchange and refresh.
- `docs/`: architecture notes and feature-specific docs.

## Major Features

- Capture meal, exercise, and sleep entries from the app or Android image-share intents.
- Analyze entries with OpenAI or Gemini-backed LLM flows.
- Generate daily and weekly summaries with tool-calling support for profile, history, and recent-entry context.
- Connect a Polar account, persist sync checkpoints, refresh data in the foreground, and schedule Android background sync.
- Manage nutrition profiles by scanning packaged-food labels and saving exact macro profiles.
- Track weight history and use it in summaries and profile flows.
- Export and import app data from settings.
- Share diagnostics logs from the app.
- Downsize older completed-entry images with the Android retention worker.

## Build Requirements

- JDK 17 or newer available through `JAVA_HOME` or `PATH`
- Android SDK installed locally
- `local.properties` configured for your Android SDK

Optional local configuration:

```properties
sdk.dir=/path/to/Android/Sdk
polar.client.id=your-polar-client-id
polar.broker.base.url=https://your-cloud-function-url
```

## Common Commands

```bash
# Build the Android debug app
./gradlew assembleDebug

# Run the shared desktop/unit test suite
./gradlew :shared:desktopTest

# Generate Kover reports
./gradlew :shared:koverXmlReport
./gradlew :shared:koverHtmlReport

# Run the broader verification pass
./gradlew check
```

See [RUNNING_THE_APP.md](RUNNING_THE_APP.md) for Android setup and [TESTING.md](TESTING.md) for the current verification workflow.

## Testing Notes

- The main shared test suite runs via `:shared:desktopTest`.
- Compose/common presentation tests live under `composeApp/src/commonTest/`.
- Kover is enabled in `shared/` and currently verifies a 25% minimum baseline while coverage continues to grow.

## Architecture

The project uses a layered KMP design:

- data: SQLDelight-backed repositories, settings, OAuth, and API clients
- domain: analysis orchestration, summaries, tool registry, Polar sync, exports, media retention
- ui: Compose screens and screen models
- platform: Android-specific app startup, WorkManager, secure storage, file handling, camera, audio, and sharing

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/POLAR.md](docs/POLAR.md) for more detail.

## Historical Docs

- [MIGRATION_ANALYSIS.md](MIGRATION_ANALYSIS.md): archived MAUI-to-Kotlin migration analysis
- [PROGRESS.md](PROGRESS.md): current implementation snapshot and recent milestone log

## License

MIT. See [LICENSE.txt](LICENSE.txt).
