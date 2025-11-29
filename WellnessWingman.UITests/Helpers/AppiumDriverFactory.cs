using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;
using WellnessWingman.UITests.Configuration;

namespace WellnessWingman.UITests.Helpers;

/// <summary>
/// Factory class for creating and managing Appium driver instances
/// </summary>
public static class AppiumDriverFactory
{
    /// <summary>
    /// Creates a new AndroidDriver instance with appropriate capabilities
    /// </summary>
    public static AndroidDriver CreateAndroidDriver()
    {
        var serverUri = new Uri(AppiumConfig.AppiumServerUrl);
        var options = CreateAndroidOptions();

        var driver = new AndroidDriver(serverUri, options, TimeSpan.FromSeconds(AppiumConfig.CommandTimeoutSeconds));
        driver.Manage().Timeouts().ImplicitWait = TimeSpan.FromSeconds(AppiumConfig.ImplicitWaitSeconds);

        return driver;
    }

    /// <summary>
    /// Creates AppiumOptions configured for Android testing
    /// </summary>
    private static AppiumOptions CreateAndroidOptions()
    {
        var options = new AppiumOptions
        {
            AutomationName = "UiAutomator2",
            PlatformName = "Android",
            PlatformVersion = AppiumConfig.AndroidPlatformVersion,
            DeviceName = AppiumConfig.AndroidDeviceName,
        };

        // App configuration
        options.AddAdditionalAppiumOption("app", AppiumConfig.AppPath);
        options.AddAdditionalAppiumOption("appPackage", AppiumConfig.AppPackage);
        options.AddAdditionalAppiumOption("appActivity", AppiumConfig.AppActivity);

        // Performance and stability options
        options.AddAdditionalAppiumOption("newCommandTimeout", AppiumConfig.CommandTimeoutSeconds);
        options.AddAdditionalAppiumOption("autoGrantPermissions", true);
        options.AddAdditionalAppiumOption("noReset", false); // Fresh app state for each test
        options.AddAdditionalAppiumOption("fullReset", false); // But don't uninstall between tests

        // MAUI-specific optimizations
        options.AddAdditionalAppiumOption("waitForIdleTimeout", 0); // MAUI apps don't always go idle
        options.AddAdditionalAppiumOption("androidInstallTimeout", 90000); // Longer timeout for MAUI app installation

        return options;
    }

    /// <summary>
    /// Safely quits and disposes of a driver instance
    /// </summary>
    public static void QuitDriver(AndroidDriver? driver)
    {
        if (driver == null) return;

        try
        {
            driver.Quit();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error while quitting driver: {ex.Message}");
        }
        finally
        {
            driver.Dispose();
        }
    }
}
