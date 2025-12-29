using WellnessWingman.UITests.Helpers;
using WellnessWingman.UITests.PageObjects;
using Xunit;

namespace WellnessWingman.UITests.Tests;

/// <summary>
/// Tests for the Settings page functionality
/// </summary>
public class SettingsTests : BaseTest
{
    public SettingsTests()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason == null)
        {
            SetupDriver();
        }
    }

    [Fact]
    [Trait("Category", "Settings")]
    public void Settings_ProviderPicker_IsVisible()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason != null) return;

        // Arrange
        MainPage!.WaitForPageLoad();
        var settingsPage = MainPage.OpenSettings();

        // Assert
        Assert.True(settingsPage.IsDisplayed(), "Settings page should be displayed");
        Assert.True(settingsPage.IsProviderPickerVisible(), "Provider picker should be visible");
    }

    [Fact]
    [Trait("Category", "Settings")]
    public void Settings_ApiKeyEntry_IsVisible()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason != null) return;

        // Arrange
        MainPage!.WaitForPageLoad();
        var settingsPage = MainPage.OpenSettings();

        // Assert
        Assert.True(settingsPage.IsApiKeyEntryVisible(), "API key entry should be visible");
    }

    [Fact]
    [Trait("Category", "Settings")]
    public void Settings_SaveButton_IsVisible()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason != null) return;

        // Arrange
        MainPage!.WaitForPageLoad();
        var settingsPage = MainPage.OpenSettings();

        // Assert
        Assert.True(settingsPage.IsSaveButtonVisible(), "Save button should be visible");
    }

    [Fact]
    [Trait("Category", "Settings")]
    public void Settings_CanEnterApiKey()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason != null) return;

        // Arrange
        MainPage!.WaitForPageLoad();
        var settingsPage = MainPage.OpenSettings();
        var testApiKey = "test-api-key-12345";

        // Act
        settingsPage.EnterApiKey(testApiKey);

        // Assert
        var enteredKey = settingsPage.GetApiKeyText();
        Assert.Equal(testApiKey, enteredKey);
    }
}
