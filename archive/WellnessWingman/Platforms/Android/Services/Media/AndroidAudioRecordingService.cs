using Android.Media;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.ApplicationModel;

namespace WellnessWingman.Services.Media;

public sealed class AndroidAudioRecordingService : IAudioRecordingService
{
    private readonly ILogger<AndroidAudioRecordingService> _logger;
    private MediaRecorder? _recorder;
    private string? _currentOutputPath;

    public AndroidAudioRecordingService(ILogger<AndroidAudioRecordingService> logger)
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

            _recorder = new MediaRecorder();
            _currentOutputPath = outputFilePath;

            _recorder.SetAudioSource(AudioSource.Mic);
            _recorder.SetOutputFormat(OutputFormat.Mpeg4);
            _recorder.SetAudioEncoder(AudioEncoder.Aac);
            _recorder.SetAudioSamplingRate(16000); // 16 kHz
            _recorder.SetAudioChannels(1); // Mono
            _recorder.SetAudioEncodingBitRate(64000); // 64 kbps
            _recorder.SetOutputFile(outputFilePath);

            _recorder.Prepare();
            _recorder.Start();

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
                _logger.LogWarning(ex, "Error stopping MediaRecorder, recording may be too short");
                CleanupRecorder();
                SafeDeleteFile(outputPath);
                return AudioRecordingResult.Failed("Recording too short or invalid");
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
                try
                {
                    _recorder.Release();
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex, "Error releasing MediaRecorder");
                }

                _recorder.Dispose();
                _recorder = null;
            }

            _currentOutputPath = null;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error during MediaRecorder cleanup");
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
