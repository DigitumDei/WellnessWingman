# Build Fixes Needed

## Current Status

Dependencies are now resolving correctly after fixing:
- ✅ Koin: Changed from `koin-compose` to `koin-androidx-compose`
- ✅ Coil: Downgraded from 3.0.0 to 2.5.0 and removed network-ktor module

## Remaining Compilation Errors

The UI layer has API mismatches with the updated repository/data layer. These need to be fixed:

### 1. Repository API Changes

**Issue**: `getEntriesBetween()` was renamed to `getEntriesForDay()`

**Files affected**:
- `composeApp/src/commonMain/kotlin/com/wellnesswingman/ui/screens/calendar/CalendarViewModel.kt:49`
- `composeApp/src/commonMain/kotlin/com/wellnesswingman/ui/screens/calendar/WeekViewModel.kt:38`
- `composeApp/src/commonMain/kotlin/com/wellnesswingman/ui/screens/calendar/YearViewModel.kt:41`
- `composeApp/src/commonMain/kotlin/com/wellnesswingman/ui/screens/calendar/day/DayDetailViewModel.kt:29`

**Fix**: Replace all calls to `repository.getEntriesBetween(startMs, endMs)` with `repository.getEntriesForDay(startMs, endMs)`

### 2. Clock API Changes

**Issue**: `Clock.System.Today()` doesn't exist in kotlinx-datetime

**Files affected**:
- `composeApp/src/commonMain/kotlin/com/wellnesswingman/ui/screens/calendar/MonthViewScreen.kt:11`
- `composeApp/src/commonMain/kotlin/com/wellnesswingman/ui/screens/calendar/WeekViewScreen.kt:11`

**Fix**: Use `Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date` instead

### 3. CalendarMonth Type

**Issue**: `CalendarMonth` type doesn't exist

**Files affected**:
- `composeApp/src/commonMain/kotlin/com/wellnesswingman/ui/screens/calendar/day/DayDetailScreen.kt:9`

**Fix**: Use `kotlinx.datetime.LocalDate` or create a custom CalendarMonth data class if needed

### 4. TrackedEntry Property

**Issue**: References to `entry.capturedAt` might need timezone handling

**Files affected**:
- Multiple calendar ViewModels

**Fix**: Ensure proper conversion from `Instant` to `LocalDate` using `DateTimeUtil.toLocalDate()`

### 5. Experimental Material3 APIs

**Issue**: Some Material3 APIs are experimental and require opt-in

**Files affected**:
- Multiple screens

**Fix**: Add `@OptIn(ExperimentalMaterial3Api::class)` to affected composables or suppress at module level

## Quick Fix Strategy

Since you want to get the app running quickly, I recommend:

1. **Option A - Temporary Stubs**: Comment out the calendar views temporarily and focus on getting the main screen working first
2. **Option B - Fix All**: Systematically fix all the API mismatches (will take longer but provides full functionality)

## To Resume

Once you decide which approach, I can:
- Fix all the API mismatches (Option B)
- Or stub out the calendar screens and get a minimal app running (Option A)

Let me know which you prefer!
