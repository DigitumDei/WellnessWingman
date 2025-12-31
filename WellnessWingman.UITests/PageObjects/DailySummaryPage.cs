using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;

namespace WellnessWingman.UITests.PageObjects;

/// <summary>
/// Page object for the Daily Summary page
/// </summary>
public class DailySummaryPage : BasePage
{
    public DailySummaryPage(AndroidDriver driver) : base(driver) { }

    public AppiumElement? TitleLabel => FindByAutomationId("DailySummaryTitleLabel");
    public AppiumElement? CaloriesLabel => FindByAutomationId("DailySummaryCaloriesLabel");
    public AppiumElement? InsightsList => FindByAutomationId("DailySummaryInsightsList");
    public AppiumElement? RecommendationsList => FindByAutomationId("DailySummaryRecommendationsList");
    public AppiumElement? RegenerateButton => FindByAutomationId("DailySummaryRegenerateButton");
    public AppiumElement? ActivityIndicator => FindByAutomationId("DailySummaryActivityIndicator");

    public bool IsPageDisplayed() => WaitForAutomationId("DailySummaryTitleLabel", 10) != null;

    public string? GetCaloriesText() => CaloriesLabel?.Text;

    public bool IsActivityIndicatorVisible() => ActivityIndicator?.Displayed ?? false;

    public void TapRegenerateButton()
    {
        var button = RegenerateButton;
        if (button != null)
        {
            Tap(button);
        }
    }

    public void WaitForSummaryLoaded(int timeoutSeconds = 30)
    {
        var wait = new OpenQA.Selenium.Support.UI.WebDriverWait(Driver, TimeSpan.FromSeconds(timeoutSeconds));
        wait.Until(_ => !IsActivityIndicatorVisible());
    }
}
