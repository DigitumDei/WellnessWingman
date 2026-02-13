# Build Fixes Applied

All compilation errors have been resolved. The Android app builds and runs successfully.

## Fixes Applied

### Dependencies
- ✅ Koin: Changed from `koin-compose` to `koin-androidx-compose`
- ✅ Coil: Downgraded from 3.0.0 to 2.5.0 and removed network-ktor module

### Compilation Errors (all resolved)
- ✅ Repository API: `getEntriesBetween()` → `getEntriesForDay()` in calendar ViewModels
- ✅ Clock API: `Clock.System.Today()` → `Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date`
- ✅ CalendarMonth type: Replaced with `kotlinx.datetime.LocalDate`
- ✅ TrackedEntry timezone handling: Proper `Instant` → `LocalDate` conversion via `DateTimeUtil`
- ✅ Experimental Material3 APIs: Added `@OptIn` annotations

### Runtime Fixes
- ✅ Gemini API auth: Changed from query param to `x-goog-api-key` header
- ✅ Gemini API serialization: Replaced sealed class `GeminiPart` with flat data class (avoids `type` discriminator)
- ✅ Gemini API error handling: Added HTTP status checking and error body logging
- ✅ Gemini API response sanitization: Strip `` ```json `` code fences from LLM responses
- ✅ Camera process death recovery: File-based `PendingCaptureStore` persists capture state across process death
- ✅ Android theme: Using `Theme.AppCompat.DayNight.NoActionBar` (defined in `res/values/themes.xml`)

### Security Fixes
- ✅ Zip-slip vulnerability: Path traversal check in `ZipUtil` (both Android and Desktop)
- ✅ Path normalization: `DataMigrationService` normalizes backslashes before path comparison

### Build Config
- ✅ Removed hardcoded `org.gradle.java.home` from `gradle.properties`
- ✅ `gradlew` marked as executable (`git update-index --chmod=+x`)
