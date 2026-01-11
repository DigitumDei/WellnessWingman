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
            // Try to find entries collection first
            var entriesCollection = FindByAutomationId(EntriesCollectionId);
            if (entriesCollection != null)
            {
                var entries = entriesCollection.FindElements(MobileBy.ClassName("android.view.ViewGroup"));
                return entries.Count;
            }

            // Fallback to generic selector (less reliable)
            var allEntries = Driver.FindElements(MobileBy.AndroidUIAutomator(
                "new UiSelector().className(\"android.view.ViewGroup\")"));
            return allEntries.Count;
        }
        catch (OpenQA.Selenium.WebDriverException ex)
        {
            Console.WriteLine($"Error getting entries count: {ex.Message}");
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
        catch (OpenQA.Selenium.WebDriverException ex)
        {
            Console.WriteLine($"Error getting page title: {ex.Message}");
            return string.Empty;
        }
    }

    /// <summary>
    /// Opens Settings via the flyout menu
    /// </summary>
    public SettingsPage OpenSettings()
    {
        // Open flyout menu by swiping from left edge (Shell flyout gesture)
        SwipeRight();

        // Wait for the settings item to appear in the flyout
        var wait = new OpenQA.Selenium.Support.UI.WebDriverWait(Driver, TimeSpan.FromSeconds(10));
        var settingsItem = wait.Until(_ => FindByText("Settings"));

        if (settingsItem == null)
        {
            throw new InvalidOperationException("Settings menu item not found in flyout");
        }
        Tap(settingsItem);

        return new SettingsPage(Driver);
    }
}
