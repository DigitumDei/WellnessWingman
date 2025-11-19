using WellnessWingman.PageModels;
using WellnessWingman.Models;
using Microsoft.Maui.Controls;

namespace WellnessWingman.Pages
{
    public partial class DayDetailPage : ContentPage
    {
        public EntryLogViewModel ViewModel => BindingContext as EntryLogViewModel ?? throw new ArgumentException("BindingContext is not an EntryLogViewModel");

        public DayDetailPage(EntryLogViewModel viewModel)
        {
            InitializeComponent();
            BindingContext = viewModel;
        }

        protected override void OnAppearing()
        {
            base.OnAppearing();
        }

        private async void EntriesCollection_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (e.CurrentSelection.FirstOrDefault() is TrackedEntryCard selectedEntry)
            {
                await ViewModel.GoToEntryDetailCommand.ExecuteAsync(selectedEntry);
                ((CollectionView)sender).SelectedItem = null; // Deselect item
            }
        }
    }
}
