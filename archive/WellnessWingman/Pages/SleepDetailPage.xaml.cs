using WellnessWingman.PageModels;

namespace WellnessWingman.Pages;

public partial class SleepDetailPage : ContentPage
{
    public SleepDetailPage(SleepDetailViewModel viewModel)
    {
        InitializeComponent();
        BindingContext = viewModel;
    }
}
