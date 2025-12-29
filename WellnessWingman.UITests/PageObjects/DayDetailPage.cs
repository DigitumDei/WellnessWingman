using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;

namespace WellnessWingman.UITests.PageObjects;

/// <summary>
/// Page object for the Day Detail page
/// </summary>
public class DayDetailPage : BasePage
{
    public DayDetailPage(AndroidDriver driver) : base(driver) { }

    public AppiumElement? DateLabel => FindByAutomationId("DayDetailDateLabel");
    public AppiumElement? PreviousDayButton => FindByAutomationId("DayDetailPreviousDayButton");
    public AppiumElement? NextDayButton => FindByAutomationId("DayDetailNextDayButton");
    public AppiumElement? SummaryButton => FindByAutomationId("DayDetailSummaryButton");
    public AppiumElement? ViewAnalysisButton => FindByAutomationId("DayDetailViewAnalysisButton");
    public AppiumElement? EntriesCollection => FindByAutomationId("DayDetailEntriesCollection");

    public bool IsPageDisplayed() => WaitForAutomationId("DayDetailDateLabel", 10) != null;

    public string? GetDateText() => DateLabel?.Text;

    public void TapPreviousDay()
    {
        var button = PreviousDayButton;
        if (button != null)
        {
            Tap(button);
        }
    }

    public void TapNextDay()
    {
        var button = NextDayButton;
        if (button != null)
        {
            Tap(button);
        }
    }

    public void TapGenerateSummaryButton()
    {
        var button = SummaryButton;
        if (button != null)
        {
            Tap(button);
        }
    }

    public void TapViewAnalysisButton()
    {
        var button = ViewAnalysisButton;
        if (button != null)
        {
            Tap(button);
        }
    }

    public bool IsSummaryButtonVisible() => SummaryButton?.Displayed ?? false;

    public bool IsViewAnalysisButtonVisible() => ViewAnalysisButton?.Displayed ?? false;

    public void SwipeToNextDay()
    {
        SwipeLeft();
    }

    public void SwipeToPreviousDay()
    {
        SwipeRight();
    }
}
