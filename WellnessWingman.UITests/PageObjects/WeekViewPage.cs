using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;

namespace WellnessWingman.UITests.PageObjects;

/// <summary>
/// Page Object for the Week View page
/// </summary>
public class WeekViewPage : BasePage
{
    public WeekViewPage(AndroidDriver driver) : base(driver)
    {
    }

    // Element locators - these will be updated once AutomationIds are added to XAML
    private const string WeekViewTitle = "Week View";

    /// <summary>
    /// Checks if the week view page is displayed
    /// </summary>
    public bool IsDisplayed()
    {
        return IsTextVisible("Week");
    }

    /// <summary>
    /// Waits for the week view page to load
    /// </summary>
    public void WaitForPageLoad(int timeoutSeconds = 10)
    {
        WaitForElement(MobileBy.AndroidUIAutomator(
            "new UiSelector().textContains(\"Week\")"), timeoutSeconds);
    }

    /// <summary>
    /// Swipes to navigate back to Main page
    /// </summary>
    public MainPage SwipeToMainPage()
    {
        SwipeRight();
        return new MainPage(Driver);
    }

    /// <summary>
    /// Swipes to navigate to Month View
    /// </summary>
    public MonthViewPage SwipeToMonthView()
    {
        SwipeLeft();
        return new MonthViewPage(Driver);
    }
}
