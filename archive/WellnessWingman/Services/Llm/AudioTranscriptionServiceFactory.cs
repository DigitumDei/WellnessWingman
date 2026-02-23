using WellnessWingman.Data;
using WellnessWingman.Models;

namespace WellnessWingman.Services.Llm;

public class AudioTranscriptionServiceFactory : IAudioTranscriptionServiceFactory
{
    private readonly IAppSettingsRepository _appSettingsRepository;
    private readonly OpenAiAudioTranscriptionService _openAiService;
    private readonly GeminiAudioTranscriptionService _geminiService;

    public AudioTranscriptionServiceFactory(
        IAppSettingsRepository appSettingsRepository,
        OpenAiAudioTranscriptionService openAiService,
        GeminiAudioTranscriptionService geminiService)
    {
        _appSettingsRepository = appSettingsRepository;
        _openAiService = openAiService;
        _geminiService = geminiService;
    }

    public async Task<IAudioTranscriptionService> GetServiceAsync()
    {
        var settings = await _appSettingsRepository.GetAppSettingsAsync().ConfigureAwait(false);
        return GetService(settings.SelectedProvider);
    }

    public IAudioTranscriptionService GetService(LlmProvider provider)
    {
        return provider switch
        {
            LlmProvider.OpenAI => _openAiService,
            LlmProvider.Gemini => _geminiService,
            _ => throw new NotSupportedException($"Audio transcription provider {provider} is not supported.")
        };
    }
}
