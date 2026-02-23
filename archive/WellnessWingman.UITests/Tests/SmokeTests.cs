using WellnessWingman.UITests.Helpers;
using Xunit;

namespace WellnessWingman.UITests.Tests;

/// <summary>
/// Smoke tests to verify basic app functionality and test infrastructure
/// </summary>
public class SmokeTests : BaseTest
{
    public SmokeTests()
    {
        if (SkipReason == null)
        {
            SetupDriver();
        }
    }

    [Fact]
    [Trait("Category", "Smoke")]
    public void AppLaunches_Successfully()
    {
        if (SkipReason != null) return;

        // Assert
        Assert.NotNull(Driver);
        Assert.NotNull(MainPage);
    }

    [Fact]
    [Trait("Category", "Smoke")]
    public void MainPage_IsDisplayed_OnAppLaunch()
    {
        if (SkipReason != null) return;

        // Act
        MainPage!.WaitForPageLoad();

        // Assert
        Assert.True(MainPage.IsDisplayed(), "Main page should be displayed after app launch");
    }

    [Fact]
    [Trait("Category", "Smoke")]
    public void TakePhotoButton_IsVisible_OnMainPage()
    {
        if (SkipReason != null) return;

        // Arrange
        MainPage!.WaitForPageLoad();

        // Act
        var isVisible = MainPage.IsTakePhotoButtonVisible();

        // Assert
        Assert.True(isVisible, "Take Photo button should be visible on the main page");
    }

    [Fact]
    [Trait("Category", "Smoke")]
    public void RecentEntriesSection_IsVisible_OnMainPage()
    {
        if (SkipReason != null) return;

        // Arrange
        MainPage!.WaitForPageLoad();

        // Act
        var isVisible = MainPage.IsRecentEntriesHeadingVisible();

        // Assert
        Assert.True(isVisible, "Recent Entries section should be visible on the main page");
    }
}
