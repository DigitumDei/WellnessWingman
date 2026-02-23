using System.Collections.Generic;
using WellnessWingman.PageModels;
using Microsoft.Extensions.DependencyInjection;

namespace WellnessWingman.Pages;

public partial class WeekViewPage : ContentPage, IQueryAttributable
{
    private readonly WeekViewModel _viewModel;

    public WeekViewPage()
        : this(((App)Application.Current!).Services.GetRequiredService<WeekViewModel>())
    {
    }

    public WeekViewPage(WeekViewModel viewModel)
    {
        InitializeComponent();
        _viewModel = viewModel;
        BindingContext = viewModel;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();

        _viewModel.TriggerInitialLoad();
    }

    public void ApplyQueryAttributes(IDictionary<string, object> query)
    {
        _viewModel.ApplyQueryAttributes(query);
    }

    private async void Day_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (e.CurrentSelection.FirstOrDefault() is WeekDayView selectedDay)
        {
            await _viewModel.SelectDayCommand.ExecuteAsync(selectedDay);
            ((CollectionView)sender).SelectedItem = null; // Deselect item
        }
    }
}
