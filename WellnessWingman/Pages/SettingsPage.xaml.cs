using CommunityToolkit.Mvvm.Input;
using WellnessWingman.PageModels;

namespace WellnessWingman.Pages;

public partial class SettingsPage : ContentPage
{
    public SettingsPage(SettingsViewModel viewModel)
    {
        InitializeComponent();
        BindingContext = viewModel;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();

        if (BindingContext is SettingsViewModel viewModel)
        {
            await viewModel.LoadSettingsCommand.ExecuteAsync(null);
        }
    }
}
