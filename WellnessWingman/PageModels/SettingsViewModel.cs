using System;
using System.Collections.ObjectModel;
using System.IO;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Storage;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WellnessWingman.Data;
using WellnessWingman.Models;
using WellnessWingman.Services.Logging;
using Microsoft.Extensions.Logging;

namespace WellnessWingman.PageModels;

public partial class SettingsViewModel : ObservableObject
{
    private readonly IAppSettingsRepository _appSettingsRepository;
    private readonly ILogFileService _logFileService;
    private readonly Services.Migration.IDataMigrationService _migrationService;
    private readonly ILogger<SettingsViewModel> _logger;
    private AppSettings _appSettings;

    [ObservableProperty]
    private LlmProvider selectedProvider;

    [ObservableProperty]
    private string apiKey = string.Empty;

    [ObservableProperty]
    private string modelId = string.Empty;

    [ObservableProperty]
    private bool isApiKeyMasked = true;

    public ObservableCollection<LlmProvider> Providers { get; }

    public string ToggleIconGlyph => IsApiKeyMasked ? FluentUI.eye_24_regular : FluentUI.eye_off_24_regular;

    public SettingsViewModel(
        IAppSettingsRepository appSettingsRepository,
        ILogFileService logFileService,
        Services.Migration.IDataMigrationService migrationService,
        ILogger<SettingsViewModel> logger)
    {
        _appSettingsRepository = appSettingsRepository;
        _logFileService = logFileService;
        _migrationService = migrationService;
        _logger = logger;
        Providers = new ObservableCollection<LlmProvider>(Enum.GetValues<LlmProvider>());
        _appSettings = new AppSettings();
    }

    [RelayCommand]
    private async Task ExportDataAsync()
    {
        string? zipPath = null;
        try
        {
            zipPath = await _migrationService.ExportDataAsync();

            await using var exportStream = File.OpenRead(zipPath);
            var suggestedFileName = Path.GetFileName(zipPath);
            var saveResult = await FileSaver.Default.SaveAsync(suggestedFileName, exportStream);

            if (saveResult.IsSuccessful)
            {
                await Application.Current!.MainPage!.DisplayAlert(
                    "Export Complete",
                    $"Backup saved to:\n{saveResult.FilePath}",
                    "OK");
                return;
            }

            if (saveResult.Exception is null || saveResult.Exception is OperationCanceledException)
            {
                return;
            }

            _logger.LogWarning(
                saveResult.Exception,
                "File save picker did not complete successfully. Falling back to share sheet.");

            await Share.Default.RequestAsync(new ShareFileRequest
            {
                Title = "Export WellnessWingman Data",
                File = new ShareFile(zipPath)
            });
        }
        catch (Exception ex)
        {
            await Application.Current!.MainPage!.DisplayAlert("Export Failed", ex.Message, "OK");
        }
        finally
        {
            if (zipPath != null)
            {
                try { File.Delete(zipPath); } catch { /* best effort */ }
            }
        }
    }

    [RelayCommand]
    private async Task ImportDataAsync()
    {
        try
        {
            var result = await FilePicker.Default.PickAsync(new PickOptions
            {
                PickerTitle = "Select Backup File",
                FileTypes = new FilePickerFileType(new Dictionary<DevicePlatform, IEnumerable<string>>
                {
                    { DevicePlatform.iOS, new[] { "public.zip-archive" } },
                    { DevicePlatform.Android, new[] { "application/zip" } },
                    { DevicePlatform.WinUI, new[] { ".zip" } }
                })
            });

            if (result != null)
            {
                bool confirm = await Application.Current!.MainPage!.DisplayAlert(
                    "Confirm Import", 
                    "This will merge data from the backup into your current data. Existing entries with the same ID will be updated. Continue?", 
                    "Yes", "No");

                if (confirm)
                {
                    await _migrationService.ImportDataAsync(result.FullPath);
                    await Application.Current!.MainPage!.DisplayAlert("Success", "Data imported successfully.", "OK");
                }
            }
        }
        catch (Exception ex)
        {
            await Application.Current!.MainPage!.DisplayAlert("Import Failed", ex.Message, "OK");
        }
    }

    [RelayCommand]
    private async Task LoadSettingsAsync()
    {
        try
        {
            _appSettings = await _appSettingsRepository.GetAppSettingsAsync();
            SelectedProvider = _appSettings.SelectedProvider;
            if (_appSettings.ApiKeys.TryGetValue(SelectedProvider, out var key))
            {
                ApiKey = key;
            }
            else
            {
                ApiKey = string.Empty;
            }

            if (_appSettings.ModelPreferences.TryGetValue(SelectedProvider, out var model))
            {
                ModelId = model;
            }
            else
            {
                ModelId = string.Empty;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to load app settings.");
            await Shell.Current.DisplayAlertAsync("Error", "Unable to load settings.", "OK");
        }
    }

    [RelayCommand]
    private async Task SaveSettings()
    {
        try
        {
            if (string.IsNullOrWhiteSpace(ApiKey))
            {
                await Shell.Current.DisplayAlertAsync("Error", "API Key cannot be empty.", "OK");
                return;
            }

            _appSettings.SelectedProvider = SelectedProvider;

            await _appSettingsRepository.SaveAppSettingsAsync(_appSettings);
            _logger.LogInformation("Settings saved for provider {Provider}.", SelectedProvider);
            await Shell.Current.DisplayAlertAsync("Success", "Settings saved.", "OK");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to save app settings.");
            await Shell.Current.DisplayAlertAsync("Error", "Unable to save settings.", "OK");
        }
    }

    [RelayCommand]
    private void ToggleApiKeyVisibility()
    {
        IsApiKeyMasked = !IsApiKeyMasked;
    }

    [RelayCommand]
    private async Task ShareDiagnosticsLogAsync()
    {
        try
        {
            await _logFileService.ShareAsync();
            _logger.LogInformation("Diagnostics log share initiated.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to share diagnostics log.");
            await Shell.Current.DisplayAlertAsync("Error", "Unable to share diagnostics log.", "OK");
        }
    }

    partial void OnSelectedProviderChanged(LlmProvider value)
    {
        IsApiKeyMasked = true;

        if (_appSettings.ApiKeys.TryGetValue(value, out var key))
        {
            ApiKey = key;
        }
        else
        {
            ApiKey = string.Empty;
        }

        if (_appSettings.ModelPreferences.TryGetValue(value, out var model))
        {
            ModelId = model;
        }
        else
        {
            ModelId = string.Empty;
        }
    }

    partial void OnApiKeyChanged(string value)
    {
        if (string.IsNullOrEmpty(value))
        {
            _appSettings.ApiKeys.Remove(SelectedProvider);
        }
        else
        {
            _appSettings.ApiKeys[SelectedProvider] = value;
        }
    }

    partial void OnModelIdChanged(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            _appSettings.ModelPreferences.Remove(SelectedProvider);
        }
        else
        {
            _appSettings.ModelPreferences[SelectedProvider] = value.Trim();
        }
    }

    partial void OnIsApiKeyMaskedChanged(bool value)
    {
        OnPropertyChanged(nameof(ToggleIconGlyph));
    }
}
