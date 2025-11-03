using WellnessWingman.PageModels;
using Microsoft.Maui.Controls;

namespace WellnessWingman.Pages;

public partial class ShareEntryPage : ContentPage
{
    public ShareEntryPage(ShareEntryViewModel viewModel)
    {
        InitializeComponent();
        BindingContext = viewModel;
    }
}
