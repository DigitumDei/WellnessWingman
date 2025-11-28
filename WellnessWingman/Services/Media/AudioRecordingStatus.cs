namespace WellnessWingman.Services.Media;

public enum AudioRecordingStatus
{
    Success,
    Canceled,
    PermissionDenied,
    MicrophoneInUse,
    DiskFull,
    HardwareFailure,
    Failed
}
