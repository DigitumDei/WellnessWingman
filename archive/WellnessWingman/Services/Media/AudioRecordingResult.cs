namespace WellnessWingman.Services.Media;

public sealed class AudioRecordingResult
{
    private AudioRecordingResult(AudioRecordingStatus status, string? audioFilePath = null, string? errorMessage = null)
    {
        Status = status;
        AudioFilePath = audioFilePath;
        ErrorMessage = errorMessage;
    }

    public AudioRecordingStatus Status { get; }

    public string? AudioFilePath { get; }

    public string? ErrorMessage { get; }

    public static AudioRecordingResult Success(string audioFilePath) => new(AudioRecordingStatus.Success, audioFilePath);

    public static AudioRecordingResult Canceled() => new(AudioRecordingStatus.Canceled);

    public static AudioRecordingResult PermissionDenied() => new(AudioRecordingStatus.PermissionDenied, errorMessage: "Microphone permission denied");

    public static AudioRecordingResult MicrophoneInUse() => new(AudioRecordingStatus.MicrophoneInUse, errorMessage: "Microphone is being used by another app");

    public static AudioRecordingResult DiskFull() => new(AudioRecordingStatus.DiskFull, errorMessage: "Not enough storage space for recording");

    public static AudioRecordingResult HardwareFailure() => new(AudioRecordingStatus.HardwareFailure, errorMessage: "Microphone not available");

    public static AudioRecordingResult Failed(string errorMessage) => new(AudioRecordingStatus.Failed, errorMessage: errorMessage);
}
