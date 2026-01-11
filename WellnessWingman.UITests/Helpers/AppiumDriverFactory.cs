using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;
using WellnessWingman.UITests.Configuration;

namespace WellnessWingman.UITests.Helpers;

/// <summary>
/// Factory class for creating and managing Appium driver instances
/// </summary>
public static class AppiumDriverFactory
{
    private const int AdbCommandTimeoutMs = 10000;

    /// <summary>
    /// Creates a new AndroidDriver instance with appropriate capabilities
    /// </summary>
    public static AndroidDriver CreateAndroidDriver()
    {
        // Create marker file for mock services BEFORE launching the app
        CreateMockServicesMarkerFileIfNeeded();

        var serverUri = new Uri(AppiumConfig.AppiumServerUrl);
        var options = CreateAndroidOptions();

        var driver = new AndroidDriver(serverUri, options, TimeSpan.FromSeconds(AppiumConfig.CommandTimeoutSeconds));
        driver.Manage().Timeouts().ImplicitWait = TimeSpan.FromSeconds(AppiumConfig.ImplicitWaitSeconds);

        return driver;
    }

    /// <summary>
    /// Creates the mock services marker file using adb before the app launches
    /// </summary>
    private static void CreateMockServicesMarkerFileIfNeeded()
    {
        var useMockServices = Environment.GetEnvironmentVariable("USE_MOCK_SERVICES");
        if (string.IsNullOrWhiteSpace(useMockServices) || !useMockServices.Equals("true", StringComparison.OrdinalIgnoreCase))
        {
            return;
        }

        try
        {
            // Create marker file in app's data directory using adb
            var appDataPath = $"/data/data/{AppiumConfig.AppPackage}/files";
            var markerFile = $"{appDataPath}/.use_mock_services";

            // First, ensure the app data directory exists (app might not be installed yet)
            RunAdbCommand($"shell mkdir -p {appDataPath}");

            // Create the marker file
            RunAdbCommand($"shell touch {markerFile}");

            Console.WriteLine($"Created mock services marker file: {markerFile}");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error: Could not create mock services marker file: {ex.Message}");
            Console.WriteLine("Aborting test run as mock services are required but could not be configured.");
            throw new InvalidOperationException("Failed to create mock services marker file. See console output for details.", ex);
        }
    }

    /// <summary>
    /// Runs an adb command
    /// </summary>
    private static void RunAdbCommand(string arguments)
    {
        var startInfo = new System.Diagnostics.ProcessStartInfo
        {
            FileName = "adb",
            Arguments = arguments,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };

        using var process = System.Diagnostics.Process.Start(startInfo);
        if (process == null)
        {
            throw new InvalidOperationException("Failed to start adb process");
        }

        process.WaitForExit(AdbCommandTimeoutMs);

        if (process.ExitCode != 0)
        {
            var error = process.StandardError.ReadToEnd();
            throw new InvalidOperationException($"adb command failed: {error}");
        }
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
            App = AppiumConfig.AppPath // Use App property instead of AddAdditionalAppiumOption
        };

        // App configuration
        options.AddAdditionalAppiumOption("appPackage", AppiumConfig.AppPackage);
        if (!string.IsNullOrWhiteSpace(AppiumConfig.AppActivity))
        {
            options.AddAdditionalAppiumOption("appActivity", AppiumConfig.AppActivity);
        }
        options.AddAdditionalAppiumOption("udid", AppiumConfig.AndroidUdid); // force specific emulator/device
        options.AddAdditionalAppiumOption("appWaitActivity", "*"); // accept any main activity during app launch
        options.AddAdditionalAppiumOption("adbExecTimeout", 120000); // allow slower emulator/app start

        // Performance and stability options
        options.AddAdditionalAppiumOption("newCommandTimeout", AppiumConfig.CommandTimeoutSeconds);
        options.AddAdditionalAppiumOption("autoGrantPermissions", true);
        options.AddAdditionalAppiumOption("noReset", true); // Don't clear app data (preserves marker file for mock services)
        options.AddAdditionalAppiumOption("fullReset", false); // But don't uninstall between tests
        if (!string.IsNullOrWhiteSpace(AppiumConfig.AndroidSdkRoot))
        {
            options.AddAdditionalAppiumOption("androidSdkRoot", AppiumConfig.AndroidSdkRoot); // ensure SDK path is explicit
        }

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
    }
}
