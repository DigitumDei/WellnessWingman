using WellnessWingman.UITests.Helpers;
using WellnessWingman.UITests.PageObjects;
using Xunit;

namespace WellnessWingman.UITests.Tests;

/// <summary>
/// Tests for entry creation functionality (using mock camera service)
/// Note: These tests require USE_MOCK_SERVICES=true to be set
/// </summary>
public class EntryCreationTests : BaseTest
{
    public EntryCreationTests()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason == null)
        {
            SetupDriver();
        }
    }

    [Fact]
    [Trait("Category", "EntryCreation")]
    public void TakePhoto_ShowsPhotoReviewPage()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason != null) return;

        // Arrange
        MainPage!.WaitForPageLoad();

        // Act
        MainPage.TapTakePhotoButton();
        var photoReviewPage = new PhotoReviewPage(Driver!);

        // Assert - With mock camera, should go directly to review page
        Assert.True(photoReviewPage.IsPageDisplayed(), "Photo review page should be displayed after taking photo with mock camera");
    }

    [Fact]
    [Trait("Category", "EntryCreation")]
    public void PhotoReview_CancelButton_ReturnsToMainPage()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason != null) return;

        // Arrange
        MainPage!.WaitForPageLoad();
        MainPage.TapTakePhotoButton();
        var photoReviewPage = new PhotoReviewPage(Driver!);
        Assert.True(photoReviewPage.IsPageDisplayed());

        // Act
        photoReviewPage.TapCancelButton();

        // Assert
        MainPage.WaitForPageLoad();
        Assert.True(MainPage.IsDisplayed(), "Main page should be displayed after canceling photo review");
    }

    [Fact]
    [Trait("Category", "EntryCreation")]
    public void PhotoReview_SaveButton_CreatesEntryAndReturnsToMain()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason != null) return;

        // Arrange
        MainPage!.WaitForPageLoad();
        MainPage.TapTakePhotoButton();
        var photoReviewPage = new PhotoReviewPage(Driver!);
        Assert.True(photoReviewPage.IsPageDisplayed());

        // Act
        photoReviewPage.TapSaveButton();
        photoReviewPage.WaitForSaveComplete(60); // Analysis may take time

        // Assert
        MainPage.WaitForPageLoad();
        Assert.True(MainPage.IsDisplayed(), "Main page should be displayed after saving entry");
    }

    [Fact]
    [Trait("Category", "EntryCreation")]
    public void PhotoReview_CanAddDescription()
    {
        var skipReason = ShouldSkipUiTests();
        if (skipReason != null) return;

        // Arrange
        MainPage!.WaitForPageLoad();
        MainPage.TapTakePhotoButton();
        var photoReviewPage = new PhotoReviewPage(Driver!);
        Assert.True(photoReviewPage.IsPageDisplayed());

        var description = "Test meal with chicken and vegetables";

        // Act
        photoReviewPage.EnterDescription(description);
        photoReviewPage.TapSaveButton();
        photoReviewPage.WaitForSaveComplete(60);

        // Assert
        MainPage.WaitForPageLoad();
        Assert.True(MainPage.IsDisplayed(), "Main page should be displayed after saving entry with description");
    }
}
