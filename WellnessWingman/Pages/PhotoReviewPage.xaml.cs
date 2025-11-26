using WellnessWingman.PageModels;
using Microsoft.Maui.Controls;

namespace WellnessWingman.Pages;

public partial class PhotoReviewPage : ContentPage
{
    public PhotoReviewPage(PhotoReviewPageViewModel viewModel)
    {
        InitializeComponent();
        BindingContext = viewModel;
    }
}
