using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;

namespace WellnessWingman.UITests.PageObjects;

/// <summary>
/// Page object for the Meal Detail page
/// </summary>
public class MealDetailPage : BasePage
{
    public MealDetailPage(AndroidDriver driver) : base(driver) { }

    public AppiumElement? Image => FindByAutomationId("MealDetailImage");
    public AppiumElement? DescriptionEditor => FindByAutomationId("MealDetailDescriptionEditor");
    public AppiumElement? SaveDescriptionButton => FindByAutomationId("MealDetailSaveDescriptionButton");
    public AppiumElement? AnalysisLabel => FindByAutomationId("MealDetailAnalysisLabel");
    public AppiumElement? CorrectionToggleButton => FindByAutomationId("MealDetailCorrectionToggleButton");
    public AppiumElement? VoiceButton => FindByAutomationId("MealDetailVoiceButton");
    public AppiumElement? CorrectionEditor => FindByAutomationId("MealDetailCorrectionEditor");
    public AppiumElement? SubmitCorrectionButton => FindByAutomationId("MealDetailSubmitCorrectionButton");
    public AppiumElement? ActivityIndicator => FindByAutomationId("MealDetailActivityIndicator");
    public AppiumElement? DeleteButton => FindByAutomationId("MealDetailDeleteButton");

    public bool IsPageDisplayed() => WaitForAutomationId("MealDetailImage", 10) != null;

    public string? GetAnalysisText() => AnalysisLabel?.Text;

    public string? GetDescription() => DescriptionEditor?.Text;

    public void TapCorrectionToggleButton()
    {
        var button = CorrectionToggleButton;
        if (button != null)
        {
            Tap(button);
        }
    }

    public void TapDeleteButton()
    {
        var button = DeleteButton;
        if (button != null)
        {
            Tap(button);
        }
    }

    public void TapSubmitCorrectionButton()
    {
        var button = SubmitCorrectionButton;
        if (button != null)
        {
            Tap(button);
        }
    }

    public void EnterCorrection(string correction)
    {
        var editor = CorrectionEditor;
        if (editor != null)
        {
            EnterText(editor, correction);
        }
    }

    public void EnterDescription(string description)
    {
        var editor = DescriptionEditor;
        if (editor != null)
        {
            EnterText(editor, description);
        }
    }

    public void TapSaveDescriptionButton()
    {
        var button = SaveDescriptionButton;
        if (button != null)
        {
            Tap(button);
        }
    }

    public void WaitForCorrectionComplete(int timeoutSeconds = 30)
    {
        var wait = new OpenQA.Selenium.Support.UI.WebDriverWait(Driver, TimeSpan.FromSeconds(timeoutSeconds));
        wait.Until(_ => !(ActivityIndicator?.Displayed ?? false));
    }
}
