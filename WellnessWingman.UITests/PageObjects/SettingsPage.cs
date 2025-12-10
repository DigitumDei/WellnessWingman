using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;

namespace WellnessWingman.UITests.PageObjects;

/// <summary>
/// Page Object for the Settings page
/// </summary>
public class SettingsPage : BasePage
{
    public SettingsPage(AndroidDriver driver) : base(driver)
    {
    }

    // Element locators - these will be updated once AutomationIds are added to XAML
    private const string SettingsTitle = "Settings";

    /// <summary>
    /// Checks if the settings page is displayed
    /// </summary>
    public bool IsDisplayed()
    {
        return IsTextVisible(SettingsTitle);
    }

    /// <summary>
    /// Waits for the settings page to load
    /// </summary>
    public void WaitForPageLoad(int timeoutSeconds = 10)
    {
        WaitForElement(MobileBy.AndroidUIAutomator(
            $"new UiSelector().text(\"{SettingsTitle}\")"), timeoutSeconds);
    }
}
