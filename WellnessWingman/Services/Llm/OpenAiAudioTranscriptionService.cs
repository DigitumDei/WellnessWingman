using Microsoft.Extensions.Logging;
using OpenAI;
using OpenAI.Audio;
using WellnessWingman.Data;

namespace WellnessWingman.Services.Llm;

public sealed class OpenAiAudioTranscriptionService : IAudioTranscriptionService
{
    private readonly IAppSettingsRepository _appSettingsRepository;
    private readonly ILogger<OpenAiAudioTranscriptionService> _logger;

    public OpenAiAudioTranscriptionService(
        IAppSettingsRepository appSettingsRepository,
        ILogger<OpenAiAudioTranscriptionService> logger)
    {
        _appSettingsRepository = appSettingsRepository;
        _logger = logger;
    }

    public async Task<AudioTranscriptionResult> TranscribeAsync(string audioFilePath, CancellationToken cancellationToken = default)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(audioFilePath))
            {
                return AudioTranscriptionResult.Failed("Audio file path is empty");
            }

            if (!File.Exists(audioFilePath))
            {
                _logger.LogError("Audio file not found at {AudioFilePath}", audioFilePath);
                return AudioTranscriptionResult.Failed("Audio file not found");
            }

            var fileInfo = new FileInfo(audioFilePath);
            if (fileInfo.Length == 0)
            {
                _logger.LogWarning("Audio file is empty: {AudioFilePath}", audioFilePath);
                return AudioTranscriptionResult.Failed("Audio file is empty");
            }

            var appSettings = await _appSettingsRepository.GetAppSettingsAsync();
            if (!appSettings.ApiKeys.TryGetValue(appSettings.SelectedProvider, out var apiKey) || string.IsNullOrWhiteSpace(apiKey))
            {
                _logger.LogError("API key not configured for provider {Provider}", appSettings.SelectedProvider);
                return AudioTranscriptionResult.Failed("API key not configured");
            }

            var client = new OpenAIClient(apiKey);
            var audioClient = client.GetAudioClient("gpt-4o-transcribe");

            _logger.LogInformation("Transcribing audio file: {AudioFilePath} ({Size} bytes)", audioFilePath, fileInfo.Length);

            using var audioFileStream = File.OpenRead(audioFilePath);
            var transcriptionOptions = new AudioTranscriptionOptions
            {
                ResponseFormat = AudioTranscriptionFormat.Simple,
                Temperature = 0.0f, // More deterministic transcriptions
                Language = "en" // English language hint
            };

            var response = await audioClient.TranscribeAudioAsync(audioFileStream, Path.GetFileName(audioFilePath), transcriptionOptions, cancellationToken);

            if (response?.Value is null)
            {
                _logger.LogError("Transcription response was null");
                return AudioTranscriptionResult.Failed("Transcription service returned no result");
            }

            var transcribedText = response.Value.Text?.Trim();
            if (string.IsNullOrWhiteSpace(transcribedText))
            {
                _logger.LogWarning("Transcription returned empty text");
                return AudioTranscriptionResult.Failed("No speech detected in audio");
            }

            _logger.LogInformation("Transcription successful: '{Text}' ({Length} characters)", transcribedText, transcribedText.Length);
            return AudioTranscriptionResult.Succeeded(transcribedText);
        }
        catch (OperationCanceledException)
        {
            _logger.LogInformation("Transcription canceled");
            return AudioTranscriptionResult.Failed("Transcription canceled");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to transcribe audio file: {AudioFilePath}", audioFilePath);
            return AudioTranscriptionResult.Failed($"Transcription failed: {ex.Message}");
        }
        finally
        {
            // Clean up audio file after transcription attempt
            SafeDeleteFile(audioFilePath);
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
                _logger.LogInformation("Deleted audio file after transcription: {Path}", path);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to delete audio file: {Path}", path);
        }
    }
}
