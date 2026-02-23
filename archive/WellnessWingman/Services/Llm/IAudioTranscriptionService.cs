using System.Threading;
using System.Threading.Tasks;

namespace WellnessWingman.Services.Llm;

public interface IAudioTranscriptionService
{
    /// <summary>
    /// Transcribes an audio file using OpenAI Whisper API.
    /// </summary>
    /// <param name="audioFilePath">The absolute path to the audio file to transcribe.</param>
    /// <param name="cancellationToken">Cancellation token for the async operation.</param>
    /// <returns>Result containing the transcribed text if successful.</returns>
    Task<AudioTranscriptionResult> TranscribeAsync(string audioFilePath, CancellationToken cancellationToken = default);
}
