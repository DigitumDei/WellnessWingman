using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Extensions.Logging;
using System.Text;
using System.Text.Json;
using WellnessWingman.Data;
using WellnessWingman.Models;
using WellnessWingman.Services.Analysis;
using WellnessWingman.Services.Llm;
using WellnessWingman.Services.Media;

namespace WellnessWingman.PageModels;

[QueryProperty(nameof(Meal), "Meal")]
public partial class MealDetailViewModel : ObservableObject
{
    private readonly ITrackedEntryRepository _trackedEntryRepository;
    private readonly IEntryAnalysisRepository _entryAnalysisRepository;
    private readonly IBackgroundAnalysisService _backgroundAnalysisService;
    private readonly IAudioRecordingService _audioRecordingService;
    private readonly IAudioTranscriptionServiceFactory _audioTranscriptionServiceFactory;
    private readonly ILogger<MealDetailViewModel> _logger;

    private Timer? _recordingTimer;
    private DateTime _recordingStartTime;
    private bool _hasCheckedPermission;

    [ObservableProperty]
    private MealPhoto? meal;

    [ObservableProperty]
    private string editableDescription = string.Empty;

    [ObservableProperty]
    private string analysisText = string.Empty;

    [ObservableProperty]
    private bool isCorrectionMode;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SubmitCorrectionCommand))]
    private string correctionText = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SubmitCorrectionCommand))]
    private bool isSubmittingCorrection;

    [ObservableProperty]
    private bool isRecording;

    [ObservableProperty]
    private bool isTranscribing;

    [ObservableProperty]
    private TimeSpan recordingDuration;

    public bool IsRecordingButtonEnabled => IsCorrectionMode && !IsSubmittingCorrection && !IsTranscribing;

    public string RecordingButtonIcon => IsRecording ? "⏹" : "🎤";

    public string RecordingButtonText => IsRecording ? "Tap to stop recording" : "Use your voice to update the analysis";

    public Color RecordingButtonColor => IsRecording ? Colors.Red : Color.FromArgb("#512BD4"); // Primary color

    public MealDetailViewModel(
        ITrackedEntryRepository trackedEntryRepository,
        IEntryAnalysisRepository entryAnalysisRepository,
        IBackgroundAnalysisService backgroundAnalysisService,
        IAudioRecordingService audioRecordingService,
        IAudioTranscriptionServiceFactory audioTranscriptionServiceFactory,
        ILogger<MealDetailViewModel> logger)
    {
        _trackedEntryRepository = trackedEntryRepository;
        _entryAnalysisRepository = entryAnalysisRepository;
        _backgroundAnalysisService = backgroundAnalysisService;
        _audioRecordingService = audioRecordingService;
        _audioTranscriptionServiceFactory = audioTranscriptionServiceFactory;
        _logger = logger;
    }

    public void SubscribeToStatusChanges()
    {
        _backgroundAnalysisService.StatusChanged += OnAnalysisStatusChanged;
    }

    public void UnsubscribeFromStatusChanges()
    {
        _backgroundAnalysisService.StatusChanged -= OnAnalysisStatusChanged;
    }

    private async void OnAnalysisStatusChanged(object? sender, EntryStatusChangedEventArgs e)
    {
        // Only reload if this is for the current meal and analysis completed
        if (Meal != null && e.EntryId == Meal.EntryId && e.Status == ProcessingStatus.Completed)
        {
            _logger.LogInformation("Analysis completed for entry {EntryId}, reloading analysis.", e.EntryId);
            await LoadAnalysisAsync().ConfigureAwait(false);
        }
    }

    [RelayCommand]
    private async Task Delete()
    {
        if (Meal is null)
        {
            _logger.LogWarning("Delete command invoked without a selected meal.");
            return;
        }

        try
        {
            _logger.LogInformation("Deleting meal entry {EntryId}.", Meal.EntryId);

            var pathsToDelete = await ResolveFilePathsAsync(Meal).ConfigureAwait(false);

            foreach (var path in pathsToDelete)
            {
                TryDeleteFile(path, Meal.EntryId);
            }

            await _trackedEntryRepository.DeleteAsync(Meal.EntryId).ConfigureAwait(false);

            await MainThread.InvokeOnMainThreadAsync(() => Shell.Current.GoToAsync(".."));
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to delete meal entry {EntryId}.", Meal.EntryId);
            await MainThread.InvokeOnMainThreadAsync(() => Shell.Current.DisplayAlertAsync("Delete failed", "We couldn't delete this meal. Try again later.", "OK"));
        }
    }

    private async Task<HashSet<string>> ResolveFilePathsAsync(MealPhoto meal)
    {
        var paths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        if (!string.IsNullOrWhiteSpace(meal.OriginalPath))
        {
            paths.Add(meal.OriginalPath);
        }

        if (!string.IsNullOrWhiteSpace(meal.FullPath))
        {
            paths.Add(meal.FullPath);
        }

        try
        {
            var trackedEntry = await _trackedEntryRepository.GetByIdAsync(meal.EntryId).ConfigureAwait(false);
            if (trackedEntry is not null)
            {
                if (!string.IsNullOrWhiteSpace(trackedEntry.BlobPath))
                {
                    var originalAbsolutePath = Path.Combine(FileSystem.AppDataDirectory, trackedEntry.BlobPath);
                    paths.Add(originalAbsolutePath);
                }

                if (trackedEntry.Payload is MealPayload trackedPayload && !string.IsNullOrWhiteSpace(trackedPayload.PreviewBlobPath))
                {
                    var previewAbsolutePath = Path.Combine(FileSystem.AppDataDirectory, trackedPayload.PreviewBlobPath);
                    paths.Add(previewAbsolutePath);
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Unable to resolve persisted file paths for entry {EntryId} during deletion.", meal.EntryId);
        }

        return paths;
    }

    private void TryDeleteFile(string path, int entryId)
    {
        if (string.IsNullOrWhiteSpace(path))
        {
            return;
        }

        try
        {
            if (File.Exists(path))
            {
                File.Delete(path);
                _logger.LogDebug("Deleted file {Path} for entry {EntryId}.", path, entryId);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to delete file {Path} for entry {EntryId}.", path, entryId);
        }
    }

    partial void OnMealChanged(MealPhoto? value)
    {
        if (value is not null)
        {
            EditableDescription = value.Description;
        }
        _ = LoadAnalysisAsync();
    }

    [RelayCommand]
    private async Task SaveDescriptionAsync()
    {
        if (Meal is null)
        {
            _logger.LogWarning("SaveDescription invoked without a selected meal.");
            return;
        }

        try
        {
            var trackedEntry = await _trackedEntryRepository.GetByIdAsync(Meal.EntryId);
            if (trackedEntry is not null && trackedEntry.Payload is MealPayload mealPayload)
            {
                mealPayload.Description = EditableDescription;
                trackedEntry.Payload = mealPayload; // Ensure the payload is marked as updated
                await _trackedEntryRepository.UpdateAsync(trackedEntry);

                // Update the UI
                Meal.Description = EditableDescription;

                await ShowAlertOnMainThreadAsync("Success", "The description has been updated.");
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to save description for entry {EntryId}.", Meal.EntryId);
            await ShowAlertOnMainThreadAsync("Error", "Could not save the new description.");
        }
    }

    private async Task LoadAnalysisAsync()
    {
        if (Meal is null)
        {
            return;
        }
        try
        {
            _logger.LogDebug("Loading analysis for entry {EntryId}.", Meal.EntryId);
            var analysis = await _entryAnalysisRepository.GetByTrackedEntryIdAsync(Meal.EntryId).ConfigureAwait(false);
            if (analysis is not null)
            {
                AnalysisText = FormatStructuredAnalysis(analysis);
            }
            else
            {
                AnalysisText = "No analysis available for this entry.";
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to load analysis for entry {EntryId}.", Meal.EntryId);
            AnalysisText = "We couldn't load the analysis for this meal.";
        }
    }

    public string CorrectionToggleButtonText => IsCorrectionMode ? "Cancel correction" : "Update analysis";

    [RelayCommand]
    private void ToggleCorrection()
    {
        if (IsSubmittingCorrection)
        {
            return;
        }

        IsCorrectionMode = !IsCorrectionMode;
    }

    [RelayCommand]
    private async Task ToggleRecordingAsync()
    {
        if (!IsCorrectionMode)
        {
            return;
        }

        if (IsRecording)
        {
            await StopRecordingInternalAsync();
        }
        else
        {
            await StartRecordingInternalAsync();
        }
    }

    [RelayCommand(CanExecute = nameof(CanSubmitCorrection))]
    private async Task SubmitCorrectionAsync()
    {
        if (Meal is null)
        {
            _logger.LogWarning("SubmitCorrection invoked without a selected meal.");
            return;
        }

        var trimmedCorrection = CorrectionText?.Trim();
        if (string.IsNullOrWhiteSpace(trimmedCorrection))
        {
            return;
        }

        try
        {
            IsSubmittingCorrection = true;
            _logger.LogInformation("Submitting correction for entry {EntryId}.", Meal.EntryId);

            var trackedEntry = await _trackedEntryRepository.GetByIdAsync(Meal.EntryId);
            if (trackedEntry is null)
            {
                _logger.LogWarning("Tracked entry {EntryId} not found when submitting correction.", Meal.EntryId);
                await ShowAlertOnMainThreadAsync("Update failed", "We couldn't find this meal entry anymore. Try refreshing.");
                return;
            }

            var existingAnalysis = await _entryAnalysisRepository.GetByTrackedEntryIdAsync(Meal.EntryId);
            if (existingAnalysis is null)
            {
                _logger.LogWarning("No analysis exists for entry {EntryId}; cannot apply correction.", Meal.EntryId);
                await ShowAlertOnMainThreadAsync("No analysis yet", "We need an existing analysis before you can submit corrections.");
                return;
            }

            // Update UserNotes with the combined/updated correction text
            // This allows notes to grow over time with each correction
            trackedEntry.UserNotes = trimmedCorrection;
            await _trackedEntryRepository.UpdateAsync(trackedEntry);
            _logger.LogDebug("Updated UserNotes for entry {EntryId}.", Meal.EntryId);

            // Queue the correction for background processing
            await _backgroundAnalysisService.QueueCorrectionAsync(Meal.EntryId, trimmedCorrection!);

            _logger.LogInformation("Successfully queued correction for entry {EntryId}.", Meal.EntryId);
            await ShowAlertOnMainThreadAsync("Processing correction", "Thanks! We're updating the analysis with your feedback. This may take a few moments.");
            IsCorrectionMode = false;
            CorrectionText = string.Empty;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to submit correction for entry {EntryId}.", Meal.EntryId);
            await ShowAlertOnMainThreadAsync("Update failed", "We couldn't update this analysis. Try again later.");
        }
        finally
        {
            IsSubmittingCorrection = false;
        }
    }

    private bool CanSubmitCorrection()
    {
        return !IsSubmittingCorrection && !string.IsNullOrWhiteSpace(CorrectionText);
    }

    private async Task StartRecordingInternalAsync()
    {
        if (IsTranscribing || IsSubmittingCorrection || !IsCorrectionMode)
        {
            return;
        }

        try
        {
            if (!_hasCheckedPermission || !await _audioRecordingService.CheckPermissionAsync())
            {
                _hasCheckedPermission = true;
                var granted = await _audioRecordingService.RequestPermissionAsync();
                if (!granted)
                {
                    await ShowAlertOnMainThreadAsync("Permission Required",
                        "Microphone access is required for voice corrections. Please grant permission in settings.");
                    return;
                }
            }

            var audioDirectory = Path.Combine(FileSystem.CacheDirectory, "Audio", "Corrections");
            Directory.CreateDirectory(audioDirectory);
            var audioFilePath = Path.Combine(audioDirectory, $"{Guid.NewGuid():N}.m4a");

            var started = await _audioRecordingService.StartRecordingAsync(audioFilePath);
            if (!started)
            {
                _logger.LogWarning("Failed to start correction recording.");
                await ShowAlertOnMainThreadAsync("Error", "Failed to start recording. Please try again.");
                return;
            }

            IsRecording = true;
            _recordingStartTime = DateTime.UtcNow;
            RecordingDuration = TimeSpan.Zero;

            _recordingTimer = new Timer(_ =>
            {
                MainThread.BeginInvokeOnMainThread(() =>
                {
                    RecordingDuration = DateTime.UtcNow - _recordingStartTime;
                });
            }, null, TimeSpan.Zero, TimeSpan.FromMilliseconds(100));

            _logger.LogInformation("Voice correction recording started.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to start correction recording.");
            await ShowAlertOnMainThreadAsync("Error", "An error occurred while starting recording.");
        }
    }

    private async Task StopRecordingInternalAsync(bool transcribe = true)
    {
        try
        {
            _recordingTimer?.Dispose();
            _recordingTimer = null;

            IsRecording = false;

            var result = await _audioRecordingService.StopRecordingAsync();

            if (result.Status != AudioRecordingStatus.Success || string.IsNullOrEmpty(result.AudioFilePath))
            {
                _logger.LogError("Correction recording failed: {Error}", result.ErrorMessage);
                if (transcribe)
                {
                    await ShowAlertOnMainThreadAsync("Error",
                        result.ErrorMessage ?? "Failed to save recording. Please try again.");
                }
                return;
            }

            if (!transcribe)
            {
                TryDeleteRecordingFile(result.AudioFilePath);
                RecordingDuration = TimeSpan.Zero;
                return;
            }

            IsTranscribing = true;

            var transcriptionService = await _audioTranscriptionServiceFactory.GetServiceAsync();
            var transcriptionResult = await transcriptionService.TranscribeAsync(result.AudioFilePath);

            IsTranscribing = false;

            if (!transcriptionResult.Success || string.IsNullOrWhiteSpace(transcriptionResult.TranscribedText))
            {
                _logger.LogError("Correction transcription failed: {Error}", transcriptionResult.ErrorMessage);
                await ShowAlertOnMainThreadAsync("Transcription Error",
                    transcriptionResult.ErrorMessage ?? "Failed to transcribe audio. You can type the correction instead.");
                return;
            }

            if (!IsCorrectionMode)
            {
                TryDeleteRecordingFile(result.AudioFilePath);
                RecordingDuration = TimeSpan.Zero;
                return;
            }

            if (string.IsNullOrWhiteSpace(CorrectionText))
            {
                CorrectionText = transcriptionResult.TranscribedText;
            }
            else
            {
                CorrectionText = $"{CorrectionText}\n{transcriptionResult.TranscribedText}";
            }

            RecordingDuration = TimeSpan.Zero;
            _logger.LogInformation("Correction transcription succeeded.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to stop correction recording or transcribe.");
            IsRecording = false;
            IsTranscribing = false;
            RecordingDuration = TimeSpan.Zero;
            _recordingTimer?.Dispose();
            _recordingTimer = null;

            await ShowAlertOnMainThreadAsync("Error", "An error occurred while processing the recording.");
        }
    }

    private async Task ResetRecordingStateAsync()
    {
        try
        {
            _recordingTimer?.Dispose();
            _recordingTimer = null;

            if (IsRecording)
            {
                await StopRecordingInternalAsync(transcribe: false);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to reset recording state for corrections.");
        }
        finally
        {
            IsRecording = false;
            IsTranscribing = false;
            RecordingDuration = TimeSpan.Zero;
        }
    }

    private void TryDeleteRecordingFile(string audioFilePath)
    {
        try
        {
            if (File.Exists(audioFilePath))
            {
                File.Delete(audioFilePath);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to delete recording at {Path}", audioFilePath);
        }
    }

    private static async Task ShowAlertOnMainThreadAsync(string title, string message)
    {
        await MainThread.InvokeOnMainThreadAsync(() => Shell.Current.DisplayAlertAsync(title, message, "OK"));
    }

    private string FormatStructuredAnalysis(EntryAnalysis analysis)
    {
        try
        {
            var unified = JsonSerializer.Deserialize<UnifiedAnalysisResult>(analysis.InsightsJson);
            if (unified is null)
            {
                return analysis.InsightsJson;
            }

            if (!string.Equals(unified.EntryType, "Meal", StringComparison.OrdinalIgnoreCase) || unified.MealAnalysis is null)
            {
                return "This entry was classified as a different type. Meal-specific analysis is not available.";
            }

            return FormatMealAnalysis(unified.MealAnalysis, unified.Warnings, unified.Confidence);
        }
        catch (JsonException)
        {
            return analysis.InsightsJson;
        }
    }

    private static string FormatValue(double? value, string suffix)
    {
        return value.HasValue ? $"{value:0.##} {suffix}" : "unknown";
    }

    private string FormatMealAnalysis(MealAnalysisResult result, IEnumerable<string>? unifiedWarnings, double confidence)
    {
        var sb = new StringBuilder();

        var combinedWarnings = new List<string>();
        if (unifiedWarnings is not null)
        {
            combinedWarnings.AddRange(unifiedWarnings.Where(w => !string.IsNullOrWhiteSpace(w)));
        }
        if (result.Warnings?.Any() == true)
        {
            combinedWarnings.AddRange(result.Warnings.Where(w => !string.IsNullOrWhiteSpace(w)));
        }

        if (combinedWarnings.Any())
        {
            sb.AppendLine("ℹ️ Analysis Notes:");
            foreach (var warning in combinedWarnings)
            {
                sb.AppendLine($"  {warning}");
            }
            sb.AppendLine();
        }

        if (confidence > 0)
        {
            sb.AppendLine($"Confidence: {(confidence * 100):0.#}%");
            sb.AppendLine();
        }

        if (result.FoodItems?.Any() != true)
        {
            sb.AppendLine("🔍 No Food Detected");
            sb.AppendLine();
            sb.AppendLine("This image doesn't appear to contain any food items.");
            sb.AppendLine();

            if (!string.IsNullOrEmpty(result.HealthInsights?.Summary))
            {
                sb.AppendLine(result.HealthInsights.Summary);
                sb.AppendLine();
            }

            if (string.IsNullOrEmpty(result.HealthInsights?.Summary) && !combinedWarnings.Any())
            {
                sb.AppendLine("💡 Tip: This app analyzes photos of meals. Try capturing a photo of your food for nutritional insights!");
            }

            return sb.ToString();
        }

        if (result.FoodItems is not null)
        {
            sb.AppendLine("🍽️ Food Items:");
            foreach (var item in result.FoodItems)
            {
                var caloriesText = item.Calories.HasValue ? $"{item.Calories} kcal" : "calories unknown";
                var portionText = item.PortionSize ?? "portion unknown";
                sb.AppendLine($"• {item.Name} ({portionText}) - {caloriesText} (confidence {(item.Confidence * 100):0.#}%)");
            }
            sb.AppendLine();
        }

        if (result.Nutrition is not null)
        {
            sb.AppendLine("⚖️ Nutrition Estimate:");
            sb.AppendLine($"• Calories: {FormatValue(result.Nutrition.TotalCalories, "kcal")}");
            sb.AppendLine($"• Protein: {FormatValue(result.Nutrition.Protein, "g")}");
            sb.AppendLine($"• Carbs: {FormatValue(result.Nutrition.Carbohydrates, "g")}");
            sb.AppendLine($"• Fat: {FormatValue(result.Nutrition.Fat, "g")}");
            sb.AppendLine($"• Fiber: {FormatValue(result.Nutrition.Fiber, "g")}");
            sb.AppendLine($"• Sugar: {FormatValue(result.Nutrition.Sugar, "g")}");
            sb.AppendLine($"• Sodium: {FormatValue(result.Nutrition.Sodium, "mg")}");
            sb.AppendLine();
        }

        if (result.HealthInsights is not null)
        {
            sb.AppendLine("🩺 Health Insights:");
            if (result.HealthInsights.HealthScore.HasValue)
            {
                sb.AppendLine($"• Health Score: {result.HealthInsights.HealthScore:0.0}/10");
            }
            if (!string.IsNullOrWhiteSpace(result.HealthInsights.Summary))
            {
                sb.AppendLine($"• Summary: {result.HealthInsights.Summary}");
            }

            if (result.HealthInsights.Positives?.Any() == true)
            {
                sb.AppendLine("• Positives:");
                foreach (var positive in result.HealthInsights.Positives)
                {
                    sb.AppendLine($"   - {positive}");
                }
            }

            if (result.HealthInsights.Improvements?.Any() == true)
            {
                sb.AppendLine("• Improvements:");
                foreach (var improvement in result.HealthInsights.Improvements)
                {
                    sb.AppendLine($"   - {improvement}");
                }
            }

            if (result.HealthInsights.Recommendations?.Any() == true)
            {
                sb.AppendLine("• Recommendations:");
                foreach (var recommendation in result.HealthInsights.Recommendations)
                {
                    sb.AppendLine($"   - {recommendation}");
                }
            }
            sb.AppendLine();
        }

        return sb.ToString();
    }

    partial void OnIsCorrectionModeChanged(bool value)
    {
        OnPropertyChanged(nameof(CorrectionToggleButtonText));
        OnPropertyChanged(nameof(IsRecordingButtonEnabled));

        if (value)
        {
            // Prepopulate with existing UserNotes so user can build upon them
            _ = LoadUserNotesIntoCorrectionTextAsync();
        }
        else
        {
            _ = ResetRecordingStateAsync();
            CorrectionText = string.Empty;
        }
    }

    private async Task LoadUserNotesIntoCorrectionTextAsync()
    {
        if (Meal is null)
        {
            return;
        }

        try
        {
            var trackedEntry = await _trackedEntryRepository.GetByIdAsync(Meal.EntryId);
            if (trackedEntry is not null && !string.IsNullOrWhiteSpace(trackedEntry.UserNotes))
            {
                CorrectionText = trackedEntry.UserNotes;
                _logger.LogDebug("Prepopulated correction text with UserNotes for entry {EntryId}.", Meal.EntryId);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to load UserNotes for entry {EntryId}.", Meal.EntryId);
        }
    }

    partial void OnIsRecordingChanged(bool value)
    {
        OnPropertyChanged(nameof(IsRecordingButtonEnabled));
        OnPropertyChanged(nameof(RecordingButtonIcon));
        OnPropertyChanged(nameof(RecordingButtonText));
        OnPropertyChanged(nameof(RecordingButtonColor));
    }

    partial void OnIsTranscribingChanged(bool value)
    {
        OnPropertyChanged(nameof(IsRecordingButtonEnabled));
    }

    partial void OnIsSubmittingCorrectionChanged(bool value)
    {
        OnPropertyChanged(nameof(IsRecordingButtonEnabled));
    }
}
