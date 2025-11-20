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

        private async void OnEntryStatusChanged(object? sender, EntryStatusChangedEventArgs e)
        {
            await ViewModel.UpdateEntryStatusAsync(e.EntryId, e.Status);
        }

        private async void EntriesCollection_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (e.CurrentSelection.FirstOrDefault() is not TrackedEntryCard selectedEntry)
            {
                return;
            }

            await HandleEntrySelectionAsync(ViewModel, selectedEntry);

            if (sender is CollectionView collectionView)
            {
                collectionView.SelectedItem = null;
            }
        }

        private static async Task HandleEntrySelectionAsync(EntryLogViewModel viewModel, TrackedEntryCard entry)
        {
            if (entry.ProcessingStatus == ProcessingStatus.Failed || entry.ProcessingStatus == ProcessingStatus.Skipped)
            {
                await viewModel.RetryAnalysisCommand.ExecuteAsync(entry);
            }
            else if (entry.IsClickable)
            {
                await viewModel.GoToEntryDetailCommand.ExecuteAsync(entry);
            }
        }
    }
}
