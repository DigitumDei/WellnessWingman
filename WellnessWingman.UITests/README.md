# WellnessWingman UI Tests

Automated UI testing infrastructure for WellnessWingman Android app using Appium and xUnit.

## Overview

This test project provides end-to-end UI automation for the WellnessWingman Android application. It uses:
- **Appium** for mobile automation
- **xUnit** for test framework
- **Page Object Model** for maintainable test code
- **Selenium WebDriver** for element interaction

## Prerequisites

### Required Software

1. **Node.js** (v18 or later)
   - Download from: https://nodejs.org/
   - Verify: `node --version`

2. **Appium Server** (v2.x)
   ```bash
   npm install -g appium
   appium driver install uiautomator2
   ```
   - Verify: `appium --version`

3. **Android SDK**
   - Install Android Studio or Android SDK Command-line Tools
   - Set `ANDROID_HOME` environment variable
   - Ensure `adb` is in PATH

4. **Java Development Kit (JDK 11 or later)**
   - Required for Android SDK tools
   - Set `JAVA_HOME` environment variable

5. **.NET 10 SDK**
   - Required to build and run tests
   - Download from: https://dotnet.microsoft.com/download

### Android Emulator Setup

1. Create an Android emulator via Android Studio or command line:
   ```bash
   # List available system images
   sdkmanager --list

   # Install a system image (example: Android API 34)
   sdkmanager "system-images;android-34;google_apis;x86_64"

   # Create an AVD
   avdmanager create avd -n test-emulator -k "system-images;android-34;google_apis;x86_64"
   ```

2. Start the emulator:
   ```bash
   emulator -avd test-emulator
   ```

3. Verify emulator is running:
   ```bash
   adb devices
   ```

## Configuration

### Environment Variables

The following environment variables can be used to configure test execution:

| Variable | Description | Default |
|----------|-------------|---------|
| `APPIUM_SERVER_URL` | Appium server URL | `http://127.0.0.1:4723` |
| `ANDROID_DEVICE_NAME` | Device/emulator name | First connected device (fallback `emulator-5554`) |
| `ANDROID_UDID` | Specific device/emulator serial (overrides auto-detect) | Inherits `ANDROID_DEVICE_NAME` |
| `ANDROID_PLATFORM_VERSION` | Android version | `16` |
| `WELLNESS_WINGMAN_APK_PATH` | Path to APK file | Auto-detected from build output |
| `WELLNESS_WINGMAN_APP_PACKAGE` | App package id | `com.digitumdei.WellnessWingman` |
| `WELLNESS_WINGMAN_APP_ACTIVITY` | App main activity (optional) | Auto-detected from manifest |

### APK Path

By default, tests look for the APK at:
```
WellnessWingman/bin/Debug/net10.0-android/com.digitumdei.WellnessWingman-Signed.apk
```

To use a custom APK location, set the `WELLNESS_WINGMAN_APK_PATH` environment variable.

## Building the App

Before running tests, build the Android APK:

```bash
# From the solution root
dotnet build WellnessWingman/WellnessWingman.csproj -c Debug -f net10.0-android
```

Or build a signed release version:

```bash
dotnet publish WellnessWingman/WellnessWingman.csproj -c Release -f net10.0-android
```

## Running Tests

### Start Appium Server

In a separate terminal, start the Appium server:

```bash
appium
```

The server should start on `http://127.0.0.1:4723`

### Run All Tests

```bash
# From the solution root
dotnet test WellnessWingman.UITests/WellnessWingman.UITests.csproj
```

### Run Specific Test

```bash
dotnet test WellnessWingman.UITests/WellnessWingman.UITests.csproj --filter "FullyQualifiedName~SmokeTests.AppLaunches_Successfully"
```

### Run Tests in Category

```bash
# Example: Run only smoke tests
dotnet test WellnessWingman.UITests/WellnessWingman.UITests.csproj --filter "Category=Smoke"
```

## Project Structure

```
WellnessWingman.UITests/
├── Configuration/
│   └── AppiumConfig.cs          # Test configuration settings
├── Helpers/
│   ├── AppiumDriverFactory.cs   # Driver creation and management
│   └── BaseTest.cs              # Base class for all tests
├── PageObjects/
│   ├── BasePage.cs              # Base page with common functionality
│   ├── MainPage.cs              # Main/Entry Log page object
│   ├── WeekViewPage.cs          # Week view page object
│   ├── MonthViewPage.cs         # Month view page object
│   └── SettingsPage.cs          # Settings page object
└── Tests/
    └── SmokeTests.cs            # Basic smoke tests
```

## Page Object Model

This project follows the Page Object Model (POM) pattern:

### BasePage
All page objects inherit from `BasePage`, which provides:
- Element finding helpers (by AutomationId, text, etc.)
- Wait helpers
- Common actions (tap, swipe, scroll)

### Creating a New Page Object

```csharp
using OpenQA.Selenium.Appium.Android;

namespace WellnessWingman.UITests.PageObjects;

public class MyPage : BasePage
{
    public MyPage(AndroidDriver driver) : base(driver)
    {
    }

    // Element locators
    private const string MyButtonId = "MyButton";

    // Page actions
    public void TapMyButton()
    {
        var button = FindByAutomationId(MyButtonId);
        Tap(button);
    }

    public bool IsDisplayed()
    {
        return FindByAutomationId(MyButtonId)?.Displayed ?? false;
    }
}
```

### Creating a New Test

```csharp
using WellnessWingman.UITests.Helpers;
using Xunit;

namespace WellnessWingman.UITests.Tests;

public class MyTests : BaseTest
{
    [Fact]
    public void MyTest()
    {
        // Arrange
        SetupDriver();
        MainPage!.WaitForPageLoad();

        // Act
        var myPage = new MyPage(Driver!);
        myPage.TapMyButton();

        // Assert
        Assert.True(myPage.IsDisplayed());
    }
}
```

## Adding AutomationIds to XAML

For reliable element identification, add `AutomationProperties.Name` to XAML elements:

```xml
<Button Text="Click Me"
        AutomationProperties.Name="MyButton"
        Command="{Binding MyCommand}" />
```

This makes elements accessible via `FindByAutomationId("MyButton")` in tests.

## Troubleshooting

### Appium Server Not Starting
- Check Node.js is installed: `node --version`
- Reinstall Appium: `npm install -g appium`
- Check for port conflicts on 4723

### Cannot Find APK
- Ensure app is built: `dotnet build WellnessWingman/WellnessWingman.csproj -f net10.0-android`
- Check APK path in build output
- Set `WELLNESS_WINGMAN_APK_PATH` environment variable

### Tests Timing Out
- Increase timeouts in test code
- Check emulator is responsive
- Verify Appium server is running

### Element Not Found
- Add AutomationId to XAML element
- Check element locator strategy
- Increase implicit wait timeout
- Use explicit waits for dynamic elements

### Session Creation Failed
- Verify Android emulator is running: `adb devices`
- Check UiAutomator2 driver is installed: `appium driver list`
- Ensure ANDROID_HOME is set correctly

## Best Practices

1. **Always use AutomationIds** for stable element location
2. **Use explicit waits** for elements that may take time to appear
3. **Keep tests independent** - each test should set up its own state
4. **Follow AAA pattern** - Arrange, Act, Assert
5. **Clean up resources** - BaseTest handles driver cleanup automatically
6. **Use Page Objects** - never access Driver directly in test methods
7. **Add meaningful assertions** - explain what's being tested

## CI/CD Integration

Tests can be integrated into CI/CD pipelines:

```yaml
# Example GitHub Actions workflow
- name: Start Appium
  run: |
    npm install -g appium
    appium driver install uiautomator2
    appium &

- name: Run UI Tests
  run: dotnet test WellnessWingman.UITests/WellnessWingman.UITests.csproj
```

## Next Steps (Phase 2+)

- [ ] Critical user flow E2E tests
- [ ] Integration tests for services
- [ ] Visual regression testing
- [ ] Performance testing
- [ ] Full CI/CD pipeline integration

## Resources

- [Appium Documentation](https://appium.io/docs/en/latest/)
- [Selenium Documentation](https://www.selenium.dev/documentation/)
- [xUnit Documentation](https://xunit.net/)
- [.NET MAUI Testing Guidance](https://learn.microsoft.com/en-us/dotnet/maui/deployment/testing)
