# Running WellnessWingman

This repository currently ships an Android-first Kotlin Multiplatform setup. The fastest path is to build and run the Android app from the repo root.

## Prerequisites

- JDK 17 or newer installed and available via `JAVA_HOME` or `PATH`
- Android SDK installed
- `local.properties` present with at least `sdk.dir=/path/to/Android/Sdk`

Optional for Polar testing:

```properties
polar.client.id=your-polar-client-id
polar.broker.base.url=https://your-cloud-function-url
```

## Project Shape

- `androidApp/`: Android app entry point and manifest
- `composeApp/`: shared Compose UI
- `shared/`: shared data, domain, SQLDelight, and platform abstractions
- `polar-oauth-broker/`: Polar OAuth broker and Terraform

## Build the App

From the repository root:

```bash
./gradlew :androidApp:assembleDebug
```

APK output:

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## Install and Launch

Start an emulator or connect a device, then:

```bash
./gradlew :androidApp:installDebug
adb shell am start -n com.wellnesswingman/.MainActivity
```

Useful device checks:

```bash
adb devices
adb logcat
```

## Android Studio

1. Open the repository root in Android Studio.
2. Let Gradle sync finish.
3. Confirm the SDK path and JDK.
4. Select the `androidApp` run configuration.
5. Run or debug on an emulator/device.

## First-Run Setup

- Configure an LLM provider in Settings.
- Add the required API key for the selected provider.
- Grant camera, microphone, and notification permissions as needed.
- Add Polar config to `local.properties` if you want to test OAuth and sync flows.

## Notable Runtime Behaviors

- `MainActivity` handles image share intents, so Android can send images directly into a pending capture flow.
- OAuth callbacks use the `wellnesswingman://oauth/result` deep link.
- `WellnessWingmanApp` schedules background jobs for image retention and Polar sync via WorkManager.
- On startup, the app recovers stale processing entries and retries a pending Polar OAuth redemption if one survived process death.

## Useful Commands

```bash
# Clean
./gradlew clean

# Build everything needed for Android debug
./gradlew :androidApp:assembleDebug

# Shared tests
./gradlew :shared:desktopTest

# Coverage report
./gradlew :shared:desktopTest :shared:koverHtmlReport
```

## Troubleshooting

### Java not found

If Gradle fails with `JAVA_HOME is not set` or `java: command not found`, install JDK 17+ and export `JAVA_HOME` before running Gradle.

### SDK location missing

Create or update `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

### Polar OAuth button does nothing

Check both:

- `polar.client.id`
- `polar.broker.base.url`

If either is blank, the Android build will still compile, but Polar connection flows will not work.

### Background sync is not running

Verify:

- network is available
- battery saver is not aggressively restricting WorkManager
- the app has been launched at least once so periodic work can be enqueued

### App launch or processing issues

Use Logcat and filter on:

- `com.wellnesswingman`
- `Napier`

The app uses persistent Napier-backed logging, and diagnostics can also be shared from Settings.
