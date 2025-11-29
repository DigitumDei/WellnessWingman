using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;

namespace WellnessWingman.UITests.PageObjects;

/// <summary>
/// Page Object for the Main/Entry Log page
/// </summary>
public class MainPage : BasePage
{
    public MainPage(AndroidDriver driver) : base(driver)
    {
    }

    // AutomationId constants
    private const string MainPageTitleId = "MainPageTitle";
    private const string TakePhotoButtonId = "TakePhotoButton";
    private const string RecentEntriesTitleId = "RecentEntriesTitle";
    private const string EntriesCollectionId = "EntriesCollection";
    private const string NutritionSummaryPanelId = "NutritionSummaryPanel";

    // Text-based locators (fallback)
    private const string TakePhotoButtonText = "Take Photo";
    private const string CaptureYourHealthEntriesText = "Capture Your Health Entries";
    private const string RecentEntriesText = "Recent Entries";
    private const string NoEntriesText = "No entries tracked yet.";

    /// <summary>
    /// Checks if the main page is displayed
    /// </summary>
    public bool IsDisplayed()
    {
        var element = FindByAutomationId(MainPageTitleId);
        return element?.Displayed ?? IsTextVisible(CaptureYourHealthEntriesText);
    }

    /// <summary>
    /// Waits for the main page to load
    /// </summary>
    public void WaitForPageLoad(int timeoutSeconds = 15)
    {
        var element = WaitForAutomationId(MainPageTitleId, timeoutSeconds);
        if (element == null)
        {
            // Fallback to text-based locator
            WaitForElement(MobileBy.AndroidUIAutomator(
                $"new UiSelector().text(\"{CaptureYourHealthEntriesText}\")"), timeoutSeconds);
        }
    }

    /// <summary>
    /// Taps the "Take Photo" button
    /// </summary>
    public void TapTakePhotoButton()
    {
        var button = FindByAutomationId(TakePhotoButtonId) ?? FindButtonByText(TakePhotoButtonText);
        if (button == null)
        {
            throw new InvalidOperationException("Take Photo button not found");
        }
        Tap(button);
    }

    /// <summary>
    /// Checks if the "Take Photo" button is visible
    /// </summary>
    public bool IsTakePhotoButtonVisible()
    {
        var button = FindByAutomationId(TakePhotoButtonId) ?? FindButtonByText(TakePhotoButtonText);
        return button?.Displayed ?? false;
    }

    /// <summary>
    /// Checks if entries are displayed
    /// </summary>
    public bool HasEntries()
    {
        return !IsTextVisible(NoEntriesText);
    }

    /// <summary>
    /// Gets the count of visible entries
    /// </summary>
    public int GetEntriesCount()
    {
        try
        {
            // Look for meal cards, exercise cards, or sleep cards
            var entries = Driver.FindElements(MobileBy.AndroidUIAutomator(
                "new UiSelector().className(\"android.view.ViewGroup\")"));
            // This is a rough count - will need refinement once AutomationIds are added
            return entries.Count;
        }
        catch
        {
            return 0;
        }
    }

    /// <summary>
    /// Swipes to navigate to Week View
    /// </summary>
    public WeekViewPage SwipeToWeekView()
    {
        SwipeLeft();
        return new WeekViewPage(Driver);
    }

    /// <summary>
    /// Checks if "Recent Entries" heading is visible
    /// </summary>
    public bool IsRecentEntriesHeadingVisible()
    {
        return IsTextVisible(RecentEntriesText);
    }

    /// <summary>
    /// Checks if nutrition totals are displayed
    /// </summary>
    public bool AreNutritionTotalsVisible()
    {
        return IsTextVisible("Today's Nutrition");
    }

    /// <summary>
    /// Gets the title of the page
    /// </summary>
    public string GetPageTitle()
    {
        try
        {
            var titleElement = Driver.FindElement(MobileBy.AndroidUIAutomator(
                "new UiSelector().resourceId(\"android:id/action_bar_title\")"));
            return titleElement.Text;
        }
        catch
        {
            return string.Empty;
        }
    }
}
