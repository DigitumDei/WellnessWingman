namespace WellnessWingman.Services.Llm;

public sealed class AudioTranscriptionResult
{
    private AudioTranscriptionResult(bool success, string? transcribedText = null, string? errorMessage = null)
    {
        Success = success;
        TranscribedText = transcribedText;
        ErrorMessage = errorMessage;
    }

    public bool Success { get; }

    public string? TranscribedText { get; }

    public string? ErrorMessage { get; }

    public static AudioTranscriptionResult Succeeded(string transcribedText) => new(true, transcribedText);

    public static AudioTranscriptionResult Failed(string errorMessage) => new(false, errorMessage: errorMessage);
}
