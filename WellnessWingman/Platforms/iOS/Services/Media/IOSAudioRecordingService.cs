using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using AVFoundation;
using Foundation;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.ApplicationModel;

namespace WellnessWingman.Services.Media;

public sealed class IOSAudioRecordingService : IAudioRecordingService
{
    private readonly ILogger<IOSAudioRecordingService> _logger;
    private AVAudioRecorder? _recorder;
    private string? _currentOutputPath;

    public IOSAudioRecordingService(ILogger<IOSAudioRecordingService> logger)
    {
        _logger = logger;
    }

    public async Task<bool> CheckPermissionAsync()
    {
        try
        {
            var status = await Permissions.CheckStatusAsync<Permissions.Microphone>();
            return status == PermissionStatus.Granted;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to check microphone permission");
            return false;
        }
    }

    public async Task<bool> RequestPermissionAsync()
    {
        try
        {
            var status = await Permissions.RequestAsync<Permissions.Microphone>();
            return status == PermissionStatus.Granted;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to request microphone permission");
            return false;
        }
    }

    public async Task<bool> StartRecordingAsync(string outputFilePath, CancellationToken cancellationToken = default)
    {
        await Task.CompletedTask; // Make method async-compatible

        try
        {
            if (!await CheckPermissionAsync())
            {
                _logger.LogWarning("Microphone permission not granted");
                return false;
            }

            if (_recorder != null)
            {
                _logger.LogWarning("Recording already in progress");
                return false;
            }

            var parentDirectory = Path.GetDirectoryName(outputFilePath);
            if (!string.IsNullOrEmpty(parentDirectory))
            {
                Directory.CreateDirectory(parentDirectory);
            }

            // Configure audio session
            var audioSession = AVAudioSession.SharedInstance();
            var sessionError = audioSession.SetCategory(AVAudioSessionCategory.Record);
            if (sessionError != null)
            {
                _logger.LogError("Failed to set audio session category: {Error}", sessionError.LocalizedDescription);
                return false;
            }

            sessionError = audioSession.SetActive(true);
            if (sessionError != null)
            {
                _logger.LogError("Failed to activate audio session: {Error}", sessionError.LocalizedDescription);
                return false;
            }

            // Configure recording settings
            var audioSettings = new AudioSettings
            {
                Format = AudioToolbox.AudioFormatType.MPEG4AAC,
                SampleRate = 16000.0f, // 16 kHz
                NumberChannels = 1, // Mono
                AudioQuality = AVAudioQuality.High
            };

            var url = NSUrl.FromFilename(outputFilePath);
            NSError? error;
            _recorder = AVAudioRecorder.Create(url, audioSettings, out error);

            if (error != null || _recorder == null)
            {
                _logger.LogError("Failed to create AVAudioRecorder: {Error}", error?.LocalizedDescription ?? "Unknown error");
                return false;
            }

            _currentOutputPath = outputFilePath;

            if (!_recorder.Record())
            {
                _logger.LogError("Failed to start recording");
                CleanupRecorder();
                return false;
            }

            _logger.LogInformation("Audio recording started: {OutputPath}", outputFilePath);
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to start audio recording");
            CleanupRecorder();
            return false;
        }
    }

    public async Task<AudioRecordingResult> StopRecordingAsync(CancellationToken cancellationToken = default)
    {
        await Task.CompletedTask; // Make method async-compatible

        try
        {
            if (_recorder == null)
            {
                _logger.LogWarning("No active recording to stop");
                return AudioRecordingResult.Failed("No active recording");
            }

            var outputPath = _currentOutputPath;

            try
            {
                _recorder.Stop();
                _logger.LogInformation("Audio recording stopped: {OutputPath}", outputPath);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error stopping AVAudioRecorder");
                CleanupRecorder();
                SafeDeleteFile(outputPath);
                return AudioRecordingResult.Failed("Failed to stop recording");
            }

            // Deactivate audio session
            try
            {
                var audioSession = AVAudioSession.SharedInstance();
                audioSession.SetActive(false);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error deactivating audio session");
            }

            CleanupRecorder();

            if (string.IsNullOrEmpty(outputPath) || !File.Exists(outputPath))
            {
                _logger.LogError("Recording file not found at {OutputPath}", outputPath);
                return AudioRecordingResult.Failed("Recording file not created");
            }

            var fileInfo = new FileInfo(outputPath);
            if (fileInfo.Length == 0)
            {
                _logger.LogWarning("Recording file is empty: {OutputPath}", outputPath);
                SafeDeleteFile(outputPath);
                return AudioRecordingResult.Failed("Recording file is empty");
            }

            _logger.LogInformation("Audio recording successful: {OutputPath} ({Size} bytes)", outputPath, fileInfo.Length);
            return AudioRecordingResult.Success(outputPath);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to stop audio recording");
            CleanupRecorder();
            SafeDeleteFile(_currentOutputPath);
            return AudioRecordingResult.Failed(ex.Message);
        }
    }

    private void CleanupRecorder()
    {
        try
        {
            if (_recorder != null)
            {
                _recorder.Dispose();
                _recorder = null;
            }

            _currentOutputPath = null;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error during AVAudioRecorder cleanup");
        }
    }

    private void SafeDeleteFile(string? path)
    {
        if (string.IsNullOrEmpty(path))
        {
            return;
        }

        try
        {
            if (File.Exists(path))
            {
                File.Delete(path);
                _logger.LogInformation("Deleted audio file: {Path}", path);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to delete audio file: {Path}", path);
        }
    }
}
