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

    // Element locators
    private const string SettingsTitle = "Settings";
    private const string ProviderPickerId = "LlmProviderPicker";
    private const string ApiKeyEntryId = "ApiKeyEntry";
    private const string SaveButtonId = "SaveSettingsButton";

    public AppiumElement? ProviderPicker => FindByAutomationId(ProviderPickerId);
    public AppiumElement? ApiKeyEntry => FindByAutomationId(ApiKeyEntryId);
    public AppiumElement? SaveButton => FindByAutomationId(SaveButtonId);

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

    /// <summary>
    /// Checks if the provider picker is visible
    /// </summary>
    public bool IsProviderPickerVisible()
    {
        return ProviderPicker?.Displayed ?? false;
    }

    /// <summary>
    /// Checks if the API key entry is visible
    /// </summary>
    public bool IsApiKeyEntryVisible()
    {
        return ApiKeyEntry?.Displayed ?? false;
    }

    /// <summary>
    /// Checks if the save button is visible
    /// </summary>
    public bool IsSaveButtonVisible()
    {
        return SaveButton?.Displayed ?? false;
    }

    /// <summary>
    /// Enters an API key
    /// </summary>
    public void EnterApiKey(string apiKey)
    {
        var entry = ApiKeyEntry;
        if (entry != null)
        {
            EnterText(entry, apiKey);
        }
    }

    /// <summary>
    /// Gets the current API key text
    /// </summary>
    public string? GetApiKeyText()
    {
        return ApiKeyEntry?.Text;
    }

    /// <summary>
    /// Taps the save button
    /// </summary>
    public void TapSaveButton()
    {
        var button = SaveButton;
        if (button != null)
        {
            Tap(button);
        }
    }
}
