using System;
using System.IO;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.Controls;
using WellnessWingman.Models;
using WellnessWingman.Services.Media;
using WellnessWingman.Utilities;

namespace WellnessWingman.PageModels;

[QueryProperty(nameof(PendingCapture), "PendingCapture")]
public partial class PhotoReviewPageViewModel : ObservableObject
{
    private readonly IPhotoCaptureFinalizationService _finalizationService;
    private readonly IPendingPhotoStore _pendingPhotoStore;
    private readonly ILogger<PhotoReviewPageViewModel> _logger;

    private PendingPhotoCapture? _pendingCapture;

    public PhotoReviewPageViewModel(
        IPhotoCaptureFinalizationService finalizationService,
        IPendingPhotoStore pendingPhotoStore,
        ILogger<PhotoReviewPageViewModel> logger)
    {
        _finalizationService = finalizationService;
        _pendingPhotoStore = pendingPhotoStore;
        _logger = logger;
    }

    public PendingPhotoCapture? PendingCapture
    {
        get => _pendingCapture;
        set
        {
            _pendingCapture = value;
            OnPropertyChanged();
            LoadPreview();
        }
    }

    [ObservableProperty]
    private ImageSource? previewImage;

    [ObservableProperty]
    private string captureInfo = string.Empty;

    [ObservableProperty]
    private string? description;

    [ObservableProperty]
    private bool isSubmitting;

    private void LoadPreview()
    {
        if (PendingCapture is null)
        {
            _logger.LogWarning("LoadPreview: PendingCapture is null");
            return;
        }

        try
        {
            var previewPath = PendingCapture.PreviewAbsolutePath;
            if (File.Exists(previewPath))
            {
                PreviewImage = ImageSource.FromFile(previewPath);
            }
            else
            {
                _logger.LogWarning("LoadPreview: Preview file not found at {PreviewPath}", previewPath);
            }

            var localCapturedAt = DateTimeConverter.ToOriginalLocal(
                PendingCapture.CapturedAtUtc,
                PendingCapture.CapturedAtTimeZoneId,
                PendingCapture.CapturedAtOffsetMinutes);

            CaptureInfo = $"Captured {localCapturedAt:MMM d, h:mm tt}";
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "LoadPreview: Failed to load preview");
        }
    }

    [RelayCommand]
    private async Task SubmitAsync()
    {
        if (IsSubmitting)
        {
            return;
        }

        if (PendingCapture is null)
        {
            _logger.LogWarning("SubmitAsync: PendingCapture is null");
            return;
        }

        try
        {
            IsSubmitting = true;

            _logger.LogInformation("SubmitAsync: Finalizing photo capture with description: {HasDescription}",
                !string.IsNullOrWhiteSpace(Description));

            var entry = await _finalizationService.FinalizeAsync(PendingCapture, Description);

            if (entry is null)
            {
                _logger.LogError("SubmitAsync: Finalization failed");
                await Shell.Current.DisplayAlertAsync("Error",
                    "Failed to save the photo. Please try again.", "OK");
                return;
            }

            _logger.LogInformation("SubmitAsync: Entry {EntryId} created successfully", entry.EntryId);

            // Clear pending store
            await _pendingPhotoStore.ClearAsync();

            // Navigate back to MainPage (which will reload entries on appearing)
            await Shell.Current.GoToAsync("..", true);

            _logger.LogInformation("SubmitAsync: Photo capture finalized successfully");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "SubmitAsync: Failed to submit photo");
            await Shell.Current.DisplayAlertAsync("Error",
                "An error occurred while saving. Please try again.", "OK");
        }
        finally
        {
            IsSubmitting = false;
        }
    }

    [RelayCommand]
    private async Task CancelAsync()
    {
        if (IsSubmitting)
        {
            return;
        }

        if (PendingCapture is null)
        {
            _logger.LogWarning("CancelAsync: PendingCapture is null");
            await Shell.Current.GoToAsync("..", true);
            return;
        }

        try
        {
            _logger.LogInformation("CancelAsync: User cancelled photo review");

            // Delete photo files
            CleanupCaptureFiles(PendingCapture);

            // Clear pending store
            await _pendingPhotoStore.ClearAsync();

            // Navigate back to MainPage
            await Shell.Current.GoToAsync("..", true);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "CancelAsync: Failed to cancel");
            await Shell.Current.GoToAsync("..", true);
        }
    }

    private void CleanupCaptureFiles(PendingPhotoCapture capture)
    {
        try
        {
            if (File.Exists(capture.OriginalAbsolutePath))
            {
                File.Delete(capture.OriginalAbsolutePath);
                _logger.LogInformation("CleanupCaptureFiles: Deleted original file");
            }

            if (File.Exists(capture.PreviewAbsolutePath))
            {
                File.Delete(capture.PreviewAbsolutePath);
                _logger.LogInformation("CleanupCaptureFiles: Deleted preview file");
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "CleanupCaptureFiles: Failed to cleanup files");
        }
    }
}
