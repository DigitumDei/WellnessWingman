using System.Threading;
using System.Threading.Tasks;

namespace WellnessWingman.Services.Media;

public sealed class NoOpAudioRecordingService : IAudioRecordingService
{
    public Task<bool> StartRecordingAsync(string outputFilePath, CancellationToken cancellationToken = default)
    {
        return Task.FromResult(false);
    }

    public Task<AudioRecordingResult> StopRecordingAsync(CancellationToken cancellationToken = default)
    {
        return Task.FromResult(AudioRecordingResult.Failed("Audio recording not supported on this platform"));
    }

    public Task<bool> CheckPermissionAsync()
    {
        return Task.FromResult(false);
    }

    public Task<bool> RequestPermissionAsync()
    {
        return Task.FromResult(false);
    }
}
