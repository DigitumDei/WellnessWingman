using WellnessWingman.Data;
using WellnessWingman.Models;
using WellnessWingman.Services.Analysis;
using WellnessWingman.Services.Media;
using WellnessWingman.Services.Platform;
using WellnessWingman.Utilities;
using Microsoft.Extensions.Logging;

namespace WellnessWingman.Pages;

public partial class MainPage : ContentPage
{
    private readonly IPhotoCaptureFinalizationService _finalizationService;
    private readonly IBackgroundAnalysisService _backgroundAnalysisService;
    private readonly ILogger<MainPage> _logger;
    private readonly ICameraCaptureService _cameraCaptureService;
    private readonly IPendingPhotoStore _pendingPhotoStore;
    private bool _isCapturing;
    private bool _isProcessingPending;

    public MainPage(
        EntryLogViewModel viewModel,
        IPhotoCaptureFinalizationService finalizationService,
        IBackgroundAnalysisService backgroundAnalysisService,
        ILogger<MainPage> logger,
        ICameraCaptureService cameraCaptureService,
        IPendingPhotoStore pendingPhotoStore)
    {
        InitializeComponent();
        BindingContext = viewModel;
        _finalizationService = finalizationService;
        _backgroundAnalysisService = backgroundAnalysisService;
        _logger = logger;
        _cameraCaptureService = cameraCaptureService;
        _pendingPhotoStore = pendingPhotoStore;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        _backgroundAnalysisService.StatusChanged += OnEntryStatusChanged;
        if (BindingContext is EntryLogViewModel vm)
        {
            await vm.LoadEntriesAsync();
        }

        await ProcessPendingCaptureAsync();
    }

    protected override void OnDisappearing()
    {
        base.OnDisappearing();
        _backgroundAnalysisService.StatusChanged -= OnEntryStatusChanged;
    }

    private async void EntriesCollection_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (BindingContext is not EntryLogViewModel vm)
        {
            return;
        }

        if (e.CurrentSelection.FirstOrDefault() is not TrackedEntryCard selectedEntry)
        {
            return;
        }

        if (vm.SelectEntryCommand.CanExecute(selectedEntry))
        {
            await vm.SelectEntryCommand.ExecuteAsync(selectedEntry);
        }

        if (sender is CollectionView collectionView)
        {
            collectionView.SelectedItem = null;
        }
    }

    private async void TakePhotoButton_Clicked(object sender, EventArgs e)
    {
        if (_isCapturing)
        {
            _logger.LogInformation("TakePhotoButton_Clicked: Capture already in progress.");
            return;
        }

        PendingPhotoCapture? capture = null;
        bool captureFinalized = false;

        try
        {
            _isCapturing = true;
            _logger.LogInformation("TakePhotoButton_Clicked: Starting photo capture");

            await ProcessPendingCaptureAsync();

            if (!await EnsureCameraPermissionsAsync())
            {
                _logger.LogWarning("TakePhotoButton_Clicked: Camera permissions denied");
                return;
            }

            if (!MediaPicker.Default.IsCaptureSupported)
            {
                _logger.LogWarning("TakePhotoButton_Clicked: Camera not supported");
                await MainThread.InvokeOnMainThreadAsync(async () =>
                {
                    await DisplayAlertAsync("Not Supported", "Camera is not available on this device.", "OK");
                });
                return;
            }

            capture = CreatePendingCaptureMetadata();
            await _pendingPhotoStore.SaveAsync(capture);

            _logger.LogInformation("TakePhotoButton_Clicked: Launching camera");
            var outcome = await _cameraCaptureService.CaptureAsync(capture);

            if (outcome.Status != CameraCaptureStatus.Success)
            {
                _logger.LogInformation("TakePhotoButton_Clicked: Camera capture did not succeed (Status: {Status}).", outcome.Status);
                CleanupCaptureFiles(capture);
                await _pendingPhotoStore.ClearAsync();

                if (outcome.Status == CameraCaptureStatus.Failed && !string.IsNullOrWhiteSpace(outcome.ErrorMessage))
                {
                    await MainThread.InvokeOnMainThreadAsync(async () =>
                    {
                        await DisplayAlertAsync("Camera Error", outcome.ErrorMessage, "OK");
                    });
                }

                return;
            }

            _logger.LogInformation("TakePhotoButton_Clicked: Camera capture returned, navigating to review page");

            await Shell.Current.GoToAsync(nameof(PhotoReviewPage), new Dictionary<string, object>
            {
                ["PendingCapture"] = capture
            });
            captureFinalized = true;
            _logger.LogInformation("TakePhotoButton_Clicked: Navigated to photo review page");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "TakePhotoButton_Clicked: FATAL ERROR during photo capture");

            if (capture is not null && !captureFinalized)
            {
                CleanupCaptureFiles(capture);
                await _pendingPhotoStore.ClearAsync();
            }

            try
            {
                await MainThread.InvokeOnMainThreadAsync(async () =>
                {
                    await DisplayAlertAsync("Error", $"An error occurred: {ex.Message}", "OK");
                });
            }
            catch (Exception alertEx)
            {
                _logger.LogError(alertEx, "TakePhotoButton_Clicked: Failed to display error alert");
            }
        }
        finally
        {
            _isCapturing = false;
        }
    }

    private async Task ProcessPendingCaptureAsync()
    {
        if (_isProcessingPending)
        {
            _logger.LogDebug("ProcessPendingCaptureAsync: Already running. Skipping duplicate invocation.");
            return;
        }

        _isProcessingPending = true;

        PendingPhotoCapture? pending = null;

        try
        {
            pending = await _pendingPhotoStore.GetAsync();
            if (pending is null)
            {
                return;
            }

            if (!File.Exists(pending.OriginalAbsolutePath))
            {
                _logger.LogWarning("Pending photo capture missing original file at {Path}. Clearing pending state.", pending.OriginalAbsolutePath);
                CleanupCaptureFiles(pending);
                await _pendingPhotoStore.ClearAsync();
                return;
            }

            // Navigate to PhotoReviewPage so user can add notes/voice recording
            // instead of directly finalizing without user input
            _logger.LogInformation("Processing pending photo captured at {CapturedAtUtc}. Navigating to review page.", pending.CapturedAtUtc);
            await Shell.Current.GoToAsync(nameof(PhotoReviewPage), new Dictionary<string, object>
            {
                ["PendingCapture"] = pending
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to process pending photo capture.");

            if (pending is not null)
            {
                CleanupCaptureFiles(pending);
                await _pendingPhotoStore.ClearAsync();
            }

            await MainThread.InvokeOnMainThreadAsync(async () =>
            {
                await DisplayAlertAsync("Photo Error", "We captured a photo but couldn't import it. Please try again.", "OK");
            });
        }
        finally
        {
            _isProcessingPending = false;
        }
    }

    private PendingPhotoCapture CreatePendingCaptureMetadata()
    {
        string relativeDirectory = Path.Combine("Entries", "Unknown");
        string pendingDir = Path.Combine(FileSystem.AppDataDirectory, relativeDirectory);
        Directory.CreateDirectory(pendingDir);

        string originalFileName = $"{Guid.NewGuid()}.jpg";
        string previewFileName = $"{Path.GetFileNameWithoutExtension(originalFileName)}_preview.jpg";

        var capturedAtUtc = DateTime.UtcNow;
        var (timeZoneId, offsetMinutes) = DateTimeConverter.CaptureTimeZoneMetadata(capturedAtUtc);

        return new PendingPhotoCapture
        {
            OriginalRelativePath = Path.Combine(relativeDirectory, originalFileName),
            PreviewRelativePath = Path.Combine(relativeDirectory, previewFileName),
            CapturedAtUtc = capturedAtUtc,
            CapturedAtTimeZoneId = timeZoneId,
            CapturedAtOffsetMinutes = offsetMinutes
        };
    }

    private static void CleanupCaptureFiles(PendingPhotoCapture capture)
    {
        TryDelete(capture.OriginalAbsolutePath);
        TryDelete(capture.PreviewAbsolutePath);

        static void TryDelete(string path)
        {
            try
            {
                if (File.Exists(path))
                {
                    File.Delete(path);
                }
            }
            catch
            {
                // ignore clean-up failures
            }
        }
    }

    private async void OnEntryStatusChanged(object? sender, EntryStatusChangedEventArgs e)
    {
        if (BindingContext is EntryLogViewModel vm)
        {
            await vm.UpdateEntryStatusAsync(e.EntryId, e.Status);
        }

        if (e.Status == ProcessingStatus.Skipped)
        {
            bool openSettings = await MainThread.InvokeOnMainThreadAsync(() => DisplayAlertAsync(
                "Connect LLM",
                "An API key is required for analysis. Please add one in settings.",
                "Open Settings",
                "Dismiss"));
            if (openSettings)
            {
                await Shell.Current.GoToAsync(nameof(SettingsPage));
            }
        }
    }

    private async Task<bool> EnsureCameraPermissionsAsync()
    {
        PermissionStatus cameraStatus = await Permissions.CheckStatusAsync<Permissions.Camera>();
        if (cameraStatus != PermissionStatus.Granted)
        {
            if (Permissions.ShouldShowRationale<Permissions.Camera>())
            {
                await MainThread.InvokeOnMainThreadAsync(async () =>
                {
                    await DisplayAlertAsync("Permissions", "Camera access is required to take a photo.", "OK");
                });
            }

            cameraStatus = await Permissions.RequestAsync<Permissions.Camera>();
        }

        if (cameraStatus != PermissionStatus.Granted)
        {
            return false;
        }

#if ANDROID
        PermissionStatus storageStatus;
        if (OperatingSystem.IsAndroidVersionAtLeast(33))
        {
            storageStatus = await Permissions.CheckStatusAsync<Permissions.Media>();
            if (storageStatus != PermissionStatus.Granted)
            {
                storageStatus = await Permissions.RequestAsync<Permissions.Media>();
            }
        }
        else
        {
            storageStatus = await Permissions.CheckStatusAsync<Permissions.StorageWrite>();
            if (storageStatus != PermissionStatus.Granted)
            {
                storageStatus = await Permissions.RequestAsync<Permissions.StorageWrite>();
            }
        }

        if (storageStatus != PermissionStatus.Granted)
        {
            return false;
        }
#endif
        return true;
    }

}
