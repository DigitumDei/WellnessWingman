using Google.GenAI;
using Google.GenAI.Types;
using Microsoft.Extensions.Logging;
using WellnessWingman.Data;
using WellnessWingman.Utilities;
using File = System.IO.File;

namespace WellnessWingman.Services.Llm;

public sealed class GeminiAudioTranscriptionService : IAudioTranscriptionService
{
    private const string DefaultGeminiAudioModel = "gemini-2.5-flash";

    private readonly IAppSettingsRepository _appSettingsRepository;
    private readonly ILogger<GeminiAudioTranscriptionService> _logger;

    public GeminiAudioTranscriptionService(
        IAppSettingsRepository appSettingsRepository,
        ILogger<GeminiAudioTranscriptionService> logger)
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

            var appSettings = await _appSettingsRepository.GetAppSettingsAsync().ConfigureAwait(false);
            if (!appSettings.ApiKeys.TryGetValue(appSettings.SelectedProvider, out var apiKey) || string.IsNullOrWhiteSpace(apiKey))
            {
                _logger.LogError("API key not configured for provider {Provider}", appSettings.SelectedProvider);
                return AudioTranscriptionResult.Failed("API key not configured");
            }

            var modelId = appSettings.GetModelPreference(appSettings.SelectedProvider) ?? DefaultGeminiAudioModel;

            var audioBytes = await File.ReadAllBytesAsync(audioFilePath, cancellationToken).ConfigureAwait(false);
            var mimeType = ResolveAudioMimeType(audioFilePath);

            var parts = new List<Part>
            {
                new() { Text = "Transcribe the audio. Respond with plain text only." },
                new()
                {
                    InlineData = new Blob
                    {
                        MimeType = mimeType,
                        Data = audioBytes
                    }
                }
            };

            var client = new Client(apiKey: apiKey);
            var response = await client.Models.GenerateContentAsync(
                model: modelId,
                contents: [new Content { Role = "user", Parts = parts }]).ConfigureAwait(false);

            var transcription = GeminiResponseParser.ExtractText(response).Trim();

            if (string.IsNullOrWhiteSpace(transcription))
            {
                _logger.LogWarning("Transcription returned empty text");
                return AudioTranscriptionResult.Failed("No speech detected in audio");
            }

            _logger.LogInformation("Transcription successful: '{Text}' ({Length} characters)", transcription, transcription.Length);
            return AudioTranscriptionResult.Succeeded(transcription);
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
            SafeDeleteFile(audioFilePath);
        }
    }

    private static string ResolveAudioMimeType(string audioFilePath)
    {
        return Path.GetExtension(audioFilePath).ToLowerInvariant() switch
        {
            ".wav" => "audio/wav",
            ".mp3" => "audio/mpeg",
            ".m4a" => "audio/mp4",
            ".aac" => "audio/aac",
            ".ogg" => "audio/ogg",
            _ => "audio/wav"
        };
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
