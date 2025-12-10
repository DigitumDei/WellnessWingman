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
