using WellnessWingman.Models;

namespace WellnessWingman.Services.Llm;

/// <summary>
/// Mock audio transcription service factory for E2E testing. Always returns the mock service regardless of provider.
/// </summary>
public class MockAudioTranscriptionServiceFactory : IAudioTranscriptionServiceFactory
{
    private readonly MockAudioTranscriptionService _mockService;

    public MockAudioTranscriptionServiceFactory(MockAudioTranscriptionService mockService)
    {
        _mockService = mockService;
    }

    public Task<IAudioTranscriptionService> GetServiceAsync()
    {
        return Task.FromResult<IAudioTranscriptionService>(_mockService);
    }

    public IAudioTranscriptionService GetService(LlmProvider provider)
    {
        // Always return mock service regardless of provider
        return _mockService;
    }
}
