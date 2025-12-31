namespace WellnessWingman.Services.Llm;

/// <summary>
/// Mock audio transcription service for E2E testing. Returns predefined transcription without calling external APIs.
/// </summary>
public class MockAudioTranscriptionService : IAudioTranscriptionService
{
    public Task<AudioTranscriptionResult> TranscribeAsync(string audioFilePath, CancellationToken cancellationToken = default)
    {
        // Return a predefined transcription for testing
        const string mockTranscription = "This is a mock voice correction. The meal contained grilled chicken with vegetables.";

        return Task.FromResult(AudioTranscriptionResult.Succeeded(mockTranscription));
    }
}
