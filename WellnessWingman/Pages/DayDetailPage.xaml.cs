using WellnessWingman.PageModels;
using WellnessWingman.Models;
using Microsoft.Maui.Controls;
using WellnessWingman.Services.Analysis;

namespace WellnessWingman.Pages
{
    public partial class DayDetailPage : ContentPage
    {
        public EntryLogViewModel ViewModel => BindingContext as EntryLogViewModel ?? throw new ArgumentException("BindingContext is not an EntryLogViewModel");
        private readonly IBackgroundAnalysisService _backgroundAnalysisService;

        public DayDetailPage(EntryLogViewModel viewModel, IBackgroundAnalysisService backgroundAnalysisService)
        {
            InitializeComponent();
            BindingContext = viewModel;
            _backgroundAnalysisService = backgroundAnalysisService;
        }

        protected override void OnAppearing()
        {
            base.OnAppearing();
            _backgroundAnalysisService.StatusChanged += OnEntryStatusChanged;
        }

        protected override void OnDisappearing()
        {
            base.OnDisappearing();
            _backgroundAnalysisService.StatusChanged -= OnEntryStatusChanged;
        }

        private async void EntriesCollection_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (e.CurrentSelection.FirstOrDefault() is not TrackedEntryCard selectedEntry)
            {
                return;
            }

            if (selectedEntry.ProcessingStatus == ProcessingStatus.Failed || selectedEntry.ProcessingStatus == ProcessingStatus.Skipped)
            {
                await ViewModel.RetryAnalysisCommand.ExecuteAsync(selectedEntry);
            }
            else if (selectedEntry.IsClickable)
            {
                await ViewModel.GoToEntryDetailCommand.ExecuteAsync(selectedEntry);
            }

            if (sender is CollectionView collectionView)
            {
                collectionView.SelectedItem = null;
            }
        }

        private async void OnEntryStatusChanged(object? sender, EntryStatusChangedEventArgs e)
        {
            await ViewModel.UpdateEntryStatusAsync(e.EntryId, e.Status);
        }
    }
}
