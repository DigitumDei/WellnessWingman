#if ANDROID
using Android.Content;
#endif

namespace WellnessWingman.Services.Share;

public interface IShareIntentProcessor
{
#if ANDROID
    Task HandleAndroidShareAsync(Intent intent, CancellationToken cancellationToken = default);
#endif
}
