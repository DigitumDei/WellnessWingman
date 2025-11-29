using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.Controls;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Storage;
using WellnessWingman.Models;
using WellnessWingman.Services.Media;
using WellnessWingman.Services.Llm;
using WellnessWingman.Utilities;

namespace WellnessWingman.PageModels;

[QueryProperty(nameof(PendingCapture), "PendingCapture")]
public partial class PhotoReviewPageViewModel : ObservableObject
{
    private readonly IPhotoCaptureFinalizationService _finalizationService;
    private readonly IPendingPhotoStore _pendingPhotoStore;
    private readonly IAudioRecordingService _audioRecordingService;
    private readonly IAudioTranscriptionService _audioTranscriptionService;
    private readonly ILogger<PhotoReviewPageViewModel> _logger;

    private PendingPhotoCapture? _pendingCapture;
    private Timer? _recordingTimer;
    private DateTime _recordingStartTime;
    private bool _hasCheckedPermission;

    public PhotoReviewPageViewModel(
        IPhotoCaptureFinalizationService finalizationService,
        IPendingPhotoStore pendingPhotoStore,
        IAudioRecordingService audioRecordingService,
        IAudioTranscriptionService audioTranscriptionService,
        ILogger<PhotoReviewPageViewModel> logger)
    {
        _finalizationService = finalizationService;
        _pendingPhotoStore = pendingPhotoStore;
        _audioRecordingService = audioRecordingService;
        _audioTranscriptionService = audioTranscriptionService;
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

    [ObservableProperty]
    private bool isRecording;

    [ObservableProperty]
    private bool isTranscribing;

    [ObservableProperty]
    private TimeSpan recordingDuration;

    public bool IsRecordingButtonEnabled => !IsSubmitting && !IsTranscribing;

    public string RecordingButtonIcon => IsRecording ? "⏹" : "🎤";

    public string RecordingButtonText => IsRecording ? "Tap to stop recording" : "Tap to record voice note";

    public Color RecordingButtonColor => IsRecording ? Colors.Red : Color.FromArgb("#512BD4"); // Primary color

    private void LoadPreview()
    {
        if (PendingCapture is null)
        {
            _logger.LogWarning("LoadPreview: PendingCapture is null");
            return;
        }

        try
        {
            // Use original photo for preview (preview file doesn't exist yet)
            var originalPath = PendingCapture.OriginalAbsolutePath;
            if (File.Exists(originalPath))
            {
                PreviewImage = ImageSource.FromFile(originalPath);
            }
            else
            {
                _logger.LogWarning("LoadPreview: Original photo file not found at {OriginalPath}", originalPath);
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

            _logger.LogInformation("SubmitAsync: Finalizing photo capture with description: '{Description}'",
                Description ?? "(null)");

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
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "CancelAsync: Failed to cancel");
        }
        finally
        {
            // Navigate back to MainPage
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

    [RelayCommand]
    private async Task ToggleRecordingAsync()
    {
        if (IsRecording)
        {
            await StopRecordingInternalAsync();
        }
        else
        {
            await StartRecordingInternalAsync();
        }
    }

    private async Task StartRecordingInternalAsync()
    {
        if (IsTranscribing || IsSubmitting)
        {
            return;
        }

        try
        {
            // Check and request permission only once
            if (!_hasCheckedPermission || !await _audioRecordingService.CheckPermissionAsync())
            {
                _hasCheckedPermission = true;
                var granted = await _audioRecordingService.RequestPermissionAsync();
                if (!granted)
                {
                    await Shell.Current.DisplayAlertAsync("Permission Required",
                        "Microphone access is required for voice notes. Please grant permission in settings.", "OK");
                    return;
                }
            }

            // Generate audio file path
            var audioDirectory = Path.Combine(FileSystem.CacheDirectory, "Audio", "Pending");
            Directory.CreateDirectory(audioDirectory);
            var audioFilePath = Path.Combine(audioDirectory, $"{Guid.NewGuid():N}.m4a");

            // Start recording
            var started = await _audioRecordingService.StartRecordingAsync(audioFilePath);
            if (!started)
            {
                _logger.LogWarning("StartRecordingInternalAsync: Failed to start recording");
                await Shell.Current.DisplayAlertAsync("Error", "Failed to start recording. Please try again.", "OK");
                return;
            }

            IsRecording = true;
            OnPropertyChanged(nameof(IsRecordingButtonEnabled));
            OnPropertyChanged(nameof(RecordingButtonIcon));
            OnPropertyChanged(nameof(RecordingButtonText));
            OnPropertyChanged(nameof(RecordingButtonColor));

            _recordingStartTime = DateTime.UtcNow;
            RecordingDuration = TimeSpan.Zero;

            // Start timer to update duration
            _recordingTimer = new Timer(_ =>
            {
                MainThread.BeginInvokeOnMainThread(() =>
                {
                    RecordingDuration = DateTime.UtcNow - _recordingStartTime;
                });
            }, null, TimeSpan.Zero, TimeSpan.FromMilliseconds(100));

            _logger.LogInformation("StartRecordingInternalAsync: Recording started");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "StartRecordingInternalAsync: Failed to start recording");
            await Shell.Current.DisplayAlertAsync("Error", "An error occurred while starting recording.", "OK");
        }
    }

    private async Task StopRecordingInternalAsync()
    {
        try
        {
            // Stop timer
            _recordingTimer?.Dispose();
            _recordingTimer = null;

            IsRecording = false;
            OnPropertyChanged(nameof(IsRecordingButtonEnabled));
            OnPropertyChanged(nameof(RecordingButtonIcon));
            OnPropertyChanged(nameof(RecordingButtonText));
            OnPropertyChanged(nameof(RecordingButtonColor));

            _logger.LogInformation("StopRecordingInternalAsync: Stopping recording");

            // Stop recording
            var result = await _audioRecordingService.StopRecordingAsync();

            if (result.Status != AudioRecordingStatus.Success || string.IsNullOrEmpty(result.AudioFilePath))
            {
                _logger.LogError("StopRecordingInternalAsync: Recording failed - {ErrorMessage}", result.ErrorMessage);
                await Shell.Current.DisplayAlertAsync("Error",
                    result.ErrorMessage ?? "Failed to save recording. Please try again.", "OK");
                return;
            }

            _logger.LogInformation("StopRecordingInternalAsync: Recording saved, starting transcription");

            // Transcribe audio
            IsTranscribing = true;
            OnPropertyChanged(nameof(IsRecordingButtonEnabled));

            var transcriptionResult = await _audioTranscriptionService.TranscribeAsync(result.AudioFilePath);

            IsTranscribing = false;
            OnPropertyChanged(nameof(IsRecordingButtonEnabled));

            if (!transcriptionResult.Success || string.IsNullOrWhiteSpace(transcriptionResult.TranscribedText))
            {
                _logger.LogError("StopRecordingInternalAsync: Transcription failed - {ErrorMessage}", transcriptionResult.ErrorMessage);
                await Shell.Current.DisplayAlertAsync("Transcription Error",
                    transcriptionResult.ErrorMessage ?? "Failed to transcribe audio. You can type manually instead.", "OK");
                return;
            }

            // Append transcribed text to description
            if (string.IsNullOrWhiteSpace(Description))
            {
                Description = transcriptionResult.TranscribedText;
            }
            else
            {
                Description = $"{Description}\n{transcriptionResult.TranscribedText}";
            }

            _logger.LogInformation("StopRecordingInternalAsync: Transcription successful");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "StopRecordingInternalAsync: Failed to stop recording or transcribe");
            IsRecording = false;
            IsTranscribing = false;
            OnPropertyChanged(nameof(IsRecordingButtonEnabled));
            OnPropertyChanged(nameof(RecordingButtonIcon));
            OnPropertyChanged(nameof(RecordingButtonText));
            OnPropertyChanged(nameof(RecordingButtonColor));

            _recordingTimer?.Dispose();
            _recordingTimer = null;

            await Shell.Current.DisplayAlertAsync("Error", "An error occurred during transcription.", "OK");
        }
    }
}
