using WellnessWingman.Models;

namespace WellnessWingman.Services.Llm;

public interface IAudioTranscriptionServiceFactory
{
    Task<IAudioTranscriptionService> GetServiceAsync();
    IAudioTranscriptionService GetService(LlmProvider provider);
}
