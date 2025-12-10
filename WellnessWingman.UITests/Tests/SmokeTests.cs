using WellnessWingman.UITests.Helpers;
using Xunit;

namespace WellnessWingman.UITests.Tests;

/// <summary>
/// Smoke tests to verify basic app functionality and test infrastructure
/// </summary>
public class SmokeTests : BaseTest
{
    [Fact]
    public void AppLaunches_Successfully()
    {
        // Arrange & Act
        SetupDriver();

        // Assert
        Assert.NotNull(Driver);
        Assert.NotNull(MainPage);
    }

    [Fact]
    public void MainPage_IsDisplayed_OnAppLaunch()
    {
        // Arrange
        SetupDriver();

        // Act
        MainPage!.WaitForPageLoad();

        // Assert
        Assert.True(MainPage.IsDisplayed(), "Main page should be displayed after app launch");
    }

    [Fact]
    public void TakePhotoButton_IsVisible_OnMainPage()
    {
        // Arrange
        SetupDriver();
        MainPage!.WaitForPageLoad();

        // Act
        var isVisible = MainPage.IsTakePhotoButtonVisible();

        // Assert
        Assert.True(isVisible, "Take Photo button should be visible on the main page");
    }

    [Fact]
    public void RecentEntriesSection_IsVisible_OnMainPage()
    {
        // Arrange
        SetupDriver();
        MainPage!.WaitForPageLoad();

        // Act
        var isVisible = MainPage.IsRecentEntriesHeadingVisible();

        // Assert
        Assert.True(isVisible, "Recent Entries section should be visible on the main page");
    }
}
