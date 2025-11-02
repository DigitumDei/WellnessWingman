using WellnessWingman.PageModels;

namespace WellnessWingman.Pages;

public partial class DailySummaryPage : ContentPage
{
    public DailySummaryPage(DailySummaryViewModel viewModel)
    {
        InitializeComponent();
        BindingContext = viewModel;
    }
}
