using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;

namespace WellnessWingman.UITests.PageObjects;

/// <summary>
/// Page object for the Photo Review page
/// </summary>
public class PhotoReviewPage : BasePage
{
    public PhotoReviewPage(AndroidDriver driver) : base(driver) { }

    public AppiumElement? Image => FindByAutomationId("PhotoReviewImage");
    public AppiumElement? SaveButton => FindByAutomationId("PhotoReviewSaveButton");
    public AppiumElement? CancelButton => FindByAutomationId("PhotoReviewCancelButton");
    public AppiumElement? VoiceButton => FindByAutomationId("PhotoReviewVoiceButton");
    public AppiumElement? DescriptionEditor => FindByAutomationId("PhotoReviewDescriptionEditor");
    public AppiumElement? ActivityIndicator => FindByAutomationId("PhotoReviewActivityIndicator");

    public bool IsPageDisplayed() => WaitForAutomationId("PhotoReviewImage", 10) != null;

    public bool IsActivityIndicatorVisible() => ActivityIndicator?.Displayed ?? false;

    public void TapSaveButton()
    {
        var button = WaitForAutomationId("PhotoReviewSaveButton");
        if (button != null)
        {
            Tap(button);
        }
    }

    public void TapCancelButton()
    {
        var button = CancelButton;
        if (button != null)
        {
            Tap(button);
        }
    }

    public void TapVoiceButton()
    {
        var button = VoiceButton;
        if (button != null)
        {
            Tap(button);
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

    public void WaitForSaveComplete(int timeoutSeconds = 30)
    {
        // Wait for the activity indicator to disappear or page to change
        var wait = new OpenQA.Selenium.Support.UI.WebDriverWait(Driver, TimeSpan.FromSeconds(timeoutSeconds));
        wait.Until(_ => !IsActivityIndicatorVisible());
    }
}
