using System.Threading;
using System.Threading.Tasks;

namespace WellnessWingman.Services.Media;

public interface IAudioRecordingService
{
    /// <summary>
    /// Starts recording audio to the specified file path.
    /// </summary>
    /// <param name="outputFilePath">The absolute path where the audio file should be saved.</param>
    /// <param name="cancellationToken">Cancellation token for the async operation.</param>
    /// <returns>True if recording started successfully, false otherwise.</returns>
    Task<bool> StartRecordingAsync(string outputFilePath, CancellationToken cancellationToken = default);

    /// <summary>
    /// Stops the current recording.
    /// </summary>
    /// <param name="cancellationToken">Cancellation token for the async operation.</param>
    /// <returns>Result containing the status and audio file path if successful.</returns>
    Task<AudioRecordingResult> StopRecordingAsync(CancellationToken cancellationToken = default);

    /// <summary>
    /// Checks if microphone permission is granted.
    /// </summary>
    /// <returns>True if permission is granted, false otherwise.</returns>
    Task<bool> CheckPermissionAsync();

    /// <summary>
    /// Requests microphone permission from the user.
    /// </summary>
    /// <returns>True if permission was granted, false otherwise.</returns>
    Task<bool> RequestPermissionAsync();
}
