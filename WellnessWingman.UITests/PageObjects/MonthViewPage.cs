using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;

namespace WellnessWingman.UITests.PageObjects;

/// <summary>
/// Page Object for the Month View page
/// </summary>
public class MonthViewPage : BasePage
{
    public MonthViewPage(AndroidDriver driver) : base(driver)
    {
    }

    /// <summary>
    /// Checks if the month view page is displayed
    /// </summary>
    public bool IsDisplayed()
    {
        return IsTextVisible("Month");
    }

    /// <summary>
    /// Waits for the month view page to load
    /// </summary>
    public void WaitForPageLoad(int timeoutSeconds = 10)
    {
        WaitForElement(MobileBy.AndroidUIAutomator(
            "new UiSelector().textContains(\"Month\")"), timeoutSeconds);
    }

    /// <summary>
    /// Swipes to navigate back to Week View
    /// </summary>
    public WeekViewPage SwipeToWeekView()
    {
        SwipeRight();
        return new WeekViewPage(Driver);
    }
}
