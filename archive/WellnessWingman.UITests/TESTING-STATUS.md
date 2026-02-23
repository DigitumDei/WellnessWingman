# UI Testing Status - Phase 2 Implementation

## ✅ Completed

### Mock Services
- **MockLlmClient**: Returns predefined meal analysis JSON (no API calls)
- **MockAudioTranscriptionService**: Returns fixed transcription text
- **MockCameraCaptureService**: Uses embedded sample-meal.png resource
- **Factory classes**: MockLlmClientFactory, MockAudioTranscriptionServiceFactory
- **DI Integration**: Conditional registration in MauiProgram.cs based on marker file

### Page Objects (Following Page Object Model)
- `BasePage`: Common functionality, element finders, swipe gestures
- `MainPage`: Entry log page with photo capture
- `SettingsPage`: Settings navigation and configuration
- `WeekViewPage`: Week view navigation
- `PhotoReviewPage`: Photo review and save/cancel
- `MealDetailPage`: Meal entry details
- `DayDetailPage`: Day detail with navigation
- `DailySummaryPage`: Daily summary display

### AutomationIds Added
Added ~30 AutomationIds across 4 XAML pages:
- `PhotoReviewPage.xaml` (5 IDs)
- `MealDetailPage.xaml` (8 IDs)
- `DayDetailPage.xaml` (6 IDs)
- `DailySummaryPage.xaml` (5 IDs)
- `MainPage.xaml` (existing IDs)

### Test Infrastructure
- `BaseTest`: Driver setup/teardown, skip logic
- `AppiumDriverFactory`: Driver creation with MAUI optimizations
- `AppiumConfig`: Environment-based configuration
- Serial test execution (xunit.runner.json)
- Test categories: Smoke, Navigation, Settings, EntryCreation

### Tests Created
- **SmokeTests** (4 tests): App launch, main page visibility, photo button
- **NavigationTests** (1 test): Flyout menu navigation to Settings
- **SettingsTests** (4 tests): Settings page functionality
- **EntryCreationTests** (4 tests): Photo capture with mock camera

**Total**: 13 automated tests

## ⚠️ Known Limitations

### 1. Mock Services Activation
**Issue**: The marker file (`.use_mock_services`) cannot be reliably created from C# tests because `adb` is not in the PATH when tests run.

**Workaround**: Manually create the marker file before running tests:
```bash
adb shell "mkdir -p /data/data/com.digitumdei.WellnessWingman/files && touch /data/data/com.digitumdei.WellnessWingman/files/.use_mock_services"
```

### 2. SwipeView Navigation
**Issue**: MAUI SwipeView gestures (used for Week View navigation) are not reliable in Appium automation.

**Impact**: Tests for swipe-based navigation are disabled:
- MainPage → Week View (swipe gesture)
- Week View → Day Detail
- Day Detail swipe navigation

**Alternative**: These flows must be tested manually.

### 3. Session Management
**Issue**: Each test class creates a new Appium session. After the first session, app data clears and the marker file is lost.

**Impact**: Only the first test in a run works reliably. Subsequent tests timeout.

**Potential Solutions**:
- Share driver instance across test classes (not recommended for isolation)
- Implement persistent marker file creation via ADB executable path configuration
- Use Shell flyout menu instead of SwipeView for navigation (app change required)

### 4. Flyout Menu Navigation
**Status**: Partially working - needs more robust element locators

The swipe-to-open flyout works, but finding menu items needs improvement.

## 🎯 What Works Reliably

### With Manual Marker File Setup:
1. ✅ App launches successfully
2. ✅ Main page displays
3. ✅ Photo button visible and tappable
4. ✅ Recent entries section visible
5. ✅ Basic navigation to Settings (when first test)

### Mock Services:
- ✅ Mock camera service (when marker file exists)
- ✅ Mock LLM returns predefined analysis
- ✅ Mock audio transcription
- ✅ DI correctly switches between real/mock implementations

## 📋 Running the Tests

### Prerequisites
1. Appium server running: `appium`
2. Android emulator/device connected: `adb devices`
3. App built: `dotnet build WellnessWingman/WellnessWingman.csproj -f net10.0-android`
4. **Manual step**: Create marker file (see workaround above)

### Environment Variables
```powershell
# PowerShell
$env:RUN_UI_TESTS="true"
$env:USE_MOCK_SERVICES="true"

# Bash
export RUN_UI_TESTS="true"
export USE_MOCK_SERVICES="true"
```

### Run Tests
```bash
# All tests (note: only first test may pass due to marker file issue)
dotnet test WellnessWingman.UITests/WellnessWingman.UITests.csproj

# Smoke tests only
dotnet test WellnessWingman.UITests/WellnessWingman.UITests.csproj --filter "Category=Smoke"

# Single test
dotnet test WellnessWingman.UITests/WellnessWingman.UITests.csproj --filter "FullyQualifiedName~AppLaunches_Successfully"
```

## 🔮 Recommendations for Full Test Suite

### Short Term
1. **Fix marker file creation**:
   - Add `adb` path to test configuration
   - Or: Use Appium's `adb` executable path setting

2. **Simplify navigation**:
   - Add toolbar buttons for Week/Month/Year views
   - Reduces reliance on unreliable swipe gestures

3. **Share driver across tests**:
   - Use xUnit collection fixtures
   - One driver instance per test run (trade-off: less isolation)

### Long Term
1. **Component/Integration tests** instead of full E2E for complex flows
2. **Manual test cases** for SwipeView interactions
3. **Visual regression testing** for UI consistency
4. **CI/CD integration** with dedicated test devices

## 📁 File Structure

```
WellnessWingman.UITests/
├── Configuration/
│   └── AppiumConfig.cs          # Environment-based config
├── Helpers/
│   ├── AppiumDriverFactory.cs   # Driver creation
│   └── BaseTest.cs              # Test base class
├── PageObjects/
│   ├── BasePage.cs              # Common page functionality
│   ├── MainPage.cs              # Entry log page
│   ├── SettingsPage.cs          # Settings page
│   ├── WeekViewPage.cs          # Week view
│   ├── PhotoReviewPage.cs       # Photo review
│   ├── MealDetailPage.cs        # Meal details
│   ├── DayDetailPage.cs         # Day detail
│   └── DailySummaryPage.cs      # Daily summary
└── Tests/
    ├── SmokeTests.cs            # Basic functionality (4 tests)
    ├── NavigationTests.cs       # Navigation (1 test, 4 disabled)
    ├── SettingsTests.cs         # Settings (4 tests)
    └── EntryCreationTests.cs    # Entry creation (4 tests)
```

## 🎓 Lessons Learned

1. **MAUI + Appium limitations**: SwipeView gestures don't translate well to automation
2. **Environment isolation**: Hard to pass config to Android apps without custom infrastructure
3. **Mock activation**: Marker file approach works but needs better tooling support
4. **Test parallelization**: Must disable for mobile UI tests (single device/app)
5. **AutomationIds are critical**: Text-based selectors are fragile and locale-dependent

