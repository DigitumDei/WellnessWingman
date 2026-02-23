# Running the WellnessWingman Android App

This guide will help you build, run, and debug the WellnessWingman Android app on an emulator or device.

## Prerequisites

1. **Java 17** - Already installed at `C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot`
2. **Android SDK** - Install via Android Studio or command line tools
3. **Android Emulator** - At least one emulator device configured

## Project Structure

```
kotlin/
├── androidApp/          # Android application module (entry point)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/wellnesswingman/
│           ├── MainActivity.kt           # Main activity
│           └── WellnessWingmanApp.kt    # Application class (Koin setup)
├── composeApp/          # Compose UI library (shared UI components)
│   └── src/
│       ├── commonMain/   # Cross-platform UI code
│       └── androidMain/  # Android-specific UI
├── shared/              # Shared business logic (models, repos, services)
│   └── src/
│       ├── commonMain/   # Cross-platform code
│       └── androidMain/  # Android-specific implementations
└── gradle/              # Gradle wrapper and dependencies
```

## Quick Start - Command Line

### 1. List Available Gradle Tasks

```bash
cd D:\SourceCode\WellnessWingman\kotlin
.\gradlew.bat tasks --group build
```

### 2. Build the App

```bash
# Debug build (faster, includes debug info)
.\gradlew.bat :androidApp:assembleDebug

# Release build (optimized, no debug info)
.\gradlew.bat :androidApp:assembleRelease
```

APK will be generated at: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

### 3. Install to Emulator/Device

First, make sure an emulator is running or device is connected:

```bash
# List connected devices/emulators
adb devices

# If no emulator is running, start one (requires Android SDK)
emulator -list-avds                    # List available emulators
emulator -avd <avd_name> &            # Start emulator in background
```

Install the app:

```bash
# Install debug build
.\gradlew.bat :androidApp:installDebug

# Or install directly with adb
adb install androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### 4. Run the App

```bash
# Build, install, and launch the app in one command
.\gradlew.bat :androidApp:installDebug
adb shell am start -n com.wellnesswingman/.MainActivity
```

## Using Android Studio

### Initial Setup

1. **Open Project**
   - Launch Android Studio
   - Select "Open" and navigate to `D:\SourceCode\WellnessWingman\kotlin`
   - Wait for Gradle sync to complete (first time may take several minutes)

2. **Configure SDK**
   - File → Project Structure → SDK Location
   - Ensure Android SDK location is set (usually `C:\Users\<username>\AppData\Local\Android\Sdk`)
   - Ensure JDK is set to Java 17

3. **Set Up Emulator**
   - Tools → Device Manager (or AVD Manager)
   - Click "Create Device"
   - Recommended: Pixel 6 or similar with API 34 (Android 14)
   - Download system image if prompted
   - Finish setup

### Running from Android Studio

1. **Select Configuration**
   - Top toolbar: Select `androidApp` from the run configuration dropdown
   - Select your emulator device next to it

2. **Run the App**
   - Click the green "Run" button (▶) or press `Shift+F10`
   - Or: Run → Run 'androidApp'
   - First build will take a few minutes

3. **Debug the App**
   - Click the "Debug" button (🐞) or press `Shift+F9`
   - Or: Run → Debug 'androidApp'

### Setting Breakpoints

1. Open any Kotlin file (e.g., `MainActivity.kt`)
2. Click in the left gutter next to a line number to set a breakpoint (red dot)
3. Run in debug mode
4. When breakpoint hits, you can:
   - Inspect variables in the "Variables" panel
   - Step through code (F8 = step over, F7 = step into)
   - Evaluate expressions (Alt+F8)
   - View call stack

### Useful Debug Configurations

**Build Variants** (Bottom left or Build → Select Build Variant):
- `debug` - Includes debug symbols, logging, slower but easier to debug
- `release` - Optimized, minified (when enabled), no debug logging

## Viewing Logs

### Android Studio Logcat

1. Open Logcat panel (bottom toolbar)
2. Filter by:
   - Package: `com.wellnesswingman`
   - Tag: `WellnessWingman`, `Napier`, or custom tags
   - Log level: Verbose, Debug, Info, Warn, Error

### Command Line

```bash
# View all logs from the app
adb logcat | grep "com.wellnesswingman"

# View Napier logs (our logging library)
adb logcat | grep "Napier"

# Clear log buffer first
adb logcat -c
adb logcat
```

## Common Gradle Commands

```bash
# Clean build (removes all build artifacts)
.\gradlew.bat clean

# Clean and rebuild everything
.\gradlew.bat clean :androidApp:assembleDebug

# Run tests
.\gradlew.bat :shared:test

# Run tests with coverage report
.\gradlew.bat :shared:test :shared:koverHtmlReport

# Check dependencies
.\gradlew.bat :androidApp:dependencies

# Sync Gradle (if you edit build files)
.\gradlew.bat --refresh-dependencies
```

## Troubleshooting

### "No connected devices"

```bash
# Check if emulator is running
adb devices

# Restart adb if needed
adb kill-server
adb start-server
adb devices
```

### "SDK location not found"

- Create/edit `local.properties` in the kotlin directory:
  ```properties
  sdk.dir=C:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
  ```

### "Build failed" or "Sync failed"

```bash
# Clean and retry
.\gradlew.bat clean
.\gradlew.bat :androidApp:assembleDebug --refresh-dependencies
```

### "Out of memory" during build

Edit `gradle.properties` and increase heap size:
```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g
```

### App crashes on launch

1. Check Logcat for crash logs
2. Common issues:
   - Missing API keys (OpenAI) - Check settings screen
   - Database initialization errors - Clear app data
   - Permission errors - Grant permissions in emulator settings

## App Configuration

### Required Setup on First Launch

The app requires configuration before it can analyze meals:

1. **OpenAI API Key**
   - Launch app → Settings screen
   - Enter your OpenAI API key
   - Key is stored in encrypted preferences

2. **Permissions** (will be requested on first use)
   - Camera - for meal photo capture
   - Microphone - for voice notes
   - Storage - for saving photos

### Database Location

SQLite database is stored at:
- Debug: `/data/data/com.wellnesswingman/databases/wellness_wingman.db`
- Access via: `adb shell` then `run-as com.wellnesswingman`

## Development Workflow

### Typical Edit-Debug Cycle

1. Make code changes in Android Studio
2. Click "Run" or "Debug" (Gradle will auto-rebuild changed modules)
3. App automatically installs and launches
4. Test your changes
5. Check Logcat for any errors or debug logs

### Hot Reload (Compose)

- Compose supports live preview of UI changes
- Open any `@Composable` function
- Click "Split" or "Design" mode to see preview
- UI changes reflect immediately in preview (no rebuild needed)

### Testing Individual Modules

```bash
# Test just the shared module
.\gradlew.bat :shared:test

# Test androidApp (if instrumented tests exist)
.\gradlew.bat :androidApp:connectedDebugAndroidTest
```

## Performance Profiling

In Android Studio:

1. Run → Profile 'androidApp'
2. Choose profiler:
   - CPU - Find slow methods
   - Memory - Find leaks
   - Network - Monitor API calls
   - Energy - Battery usage

## Next Steps

- See `TESTING.md` for E2E testing with Maestro
- See `shared/build/reports/kover/html/index.html` for test coverage
- Check the project README for architecture details

## Useful Resources

- [Kotlin Multiplatform Docs](https://kotlinlang.org/docs/multiplatform.html)
- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)
- [Android Debug Guide](https://developer.android.com/studio/debug)
- [Gradle User Guide](https://docs.gradle.org/current/userguide/userguide.html)
