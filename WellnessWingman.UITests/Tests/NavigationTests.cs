using WellnessWingman.UITests.Helpers;
using WellnessWingman.UITests.PageObjects;
using Xunit;

namespace WellnessWingman.UITests.Tests;

/// <summary>
/// Tests for app navigation functionality
/// NOTE: Swipe-based navigation tests are disabled as SwipeView gestures are not reliable in Appium
/// </summary>
public class NavigationTests : BaseTest
{
    public NavigationTests()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason == null)
        {
            SetupDriver();
        }
    }

    [Fact]
    [Trait("Category", "Navigation")]
    public void MainPage_CanNavigateToSettings()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason != null) return;

        // Arrange
        MainPage!.WaitForPageLoad();

        // Act
        var settingsPage = MainPage.OpenSettings();

        // Assert
        Assert.True(settingsPage.IsDisplayed(), "Settings page should be displayed after opening from flyout menu");
    }

    // NOTE: The following tests are disabled because SwipeView navigation is not reliable in Appium
    // These would need to be tested manually or with a different automation approach

    // [Fact] - DISABLED: SwipeView gestures not reliable in Appium
    // public void MainPage_CanNavigateToWeekView()

    // [Fact] - DISABLED: Depends on SwipeView navigation
    // public void WeekView_CanNavigateToDay()

    // [Fact] - DISABLED: Depends on SwipeView navigation
    // public void DayDetail_SwipeNavigation_ChangesDate()

    // [Fact] - DISABLED: Depends on SwipeView navigation
    // public void DayDetail_PreviousNextButtons_ChangesDate()
}
