using OpenQA.Selenium.Appium.Android;
using WellnessWingman.UITests.PageObjects;

namespace WellnessWingman.UITests.Helpers;

/// <summary>
/// Base class for all UI tests providing driver lifecycle management
/// </summary>
public abstract class BaseTest : IDisposable
{
    protected AndroidDriver? Driver { get; private set; }
    protected MainPage? MainPage { get; private set; }

    /// <summary>
    /// Determines if UI tests should run based on environment configuration
    /// </summary>
    /// <returns>Skip reason if tests should be skipped, null if tests should run</returns>
    public static string? ShouldSkipUiTests()
    {
        var runUiTests = Environment.GetEnvironmentVariable("RUN_UI_TESTS");

        // If RUN_UI_TESTS is not set or is "false", skip tests
        if (string.IsNullOrWhiteSpace(runUiTests) ||
            !bool.TryParse(runUiTests, out var shouldRun) ||
            !shouldRun)
        {
            return "UI tests are disabled. Set environment variable RUN_UI_TESTS=true to enable them.";
        }

        return null;
    }

    /// <summary>
    /// Initializes the Appium driver and launches the app
    /// </summary>
    protected void SetupDriver()
    {
        Driver = AppiumDriverFactory.CreateAndroidDriver();
        MainPage = new MainPage(Driver);
    }

    /// <summary>
    /// Cleans up the driver after test execution
    /// </summary>
    public void Dispose()
    {
        if (Driver != null)
        {
            AppiumDriverFactory.QuitDriver(Driver);
            Driver = null;
        }
        GC.SuppressFinalize(this);
    }
}
