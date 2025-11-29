using System;
using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using WellnessWingman.Services.Share;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Maui.Controls;

namespace WellnessWingman;

[Activity(
    Theme = "@style/Maui.SplashTheme",
    MainLauncher = true,
    LaunchMode = LaunchMode.SingleTask,
    Exported = true,
    ConfigurationChanges = ConfigChanges.ScreenSize |
                           ConfigChanges.Orientation |
                           ConfigChanges.UiMode |
                           ConfigChanges.ScreenLayout |
                           ConfigChanges.SmallestScreenSize |
                           ConfigChanges.Density |
                           ConfigChanges.KeyboardHidden |
                           ConfigChanges.LayoutDirection)]
[IntentFilter(
    new[] { Intent.ActionSend },
    Categories = new[] { Intent.CategoryDefault },
    DataMimeType = "image/*")]
public class MainActivity : MauiAppCompatActivity
{
    internal const int TakePhotoRequestCode = 9001;

    internal static MainActivity? Instance { get; private set; }

    internal event EventHandler<ActivityResultEventArgs>? ActivityResultReceived;

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        Instance = this;
        ProcessShareIntent(Intent);
    }

    protected override void OnDestroy()
    {
        if (ReferenceEquals(Instance, this))
        {
            Instance = null;
        }

        try
        {
            base.OnDestroy();
        }
        catch (ObjectDisposedException ex)
        {
            // Ignore disposal races during shutdown; Maui may tear down the service provider before fragments finish.
            Android.Util.Log.Warn(nameof(MainActivity), $"OnDestroy ignored ObjectDisposedException: {ex.Message}");
        }
    }

    protected override void OnActivityResult(int requestCode, Result resultCode, Intent? data)
    {
        base.OnActivityResult(requestCode, resultCode, data);
        ActivityResultReceived?.Invoke(this, new ActivityResultEventArgs(requestCode, resultCode, data));
    }

    protected override void OnNewIntent(Intent? intent)
    {
        base.OnNewIntent(intent);
        ProcessShareIntent(intent);
    }

    private void ProcessShareIntent(Intent? intent)
    {
        if (intent is null)
        {
            return;
        }

        if (Microsoft.Maui.Controls.Application.Current is not App app)
        {
            return;
        }

        using var scope = app.Services.CreateScope();
        var processor = scope.ServiceProvider.GetService<IShareIntentProcessor>();
        if (processor is null)
        {
            return;
        }

        var clonedIntent = new Intent(intent);
        _ = processor.HandleAndroidShareAsync(clonedIntent);
    }
}

public sealed class ActivityResultEventArgs : EventArgs
{
    public ActivityResultEventArgs(int requestCode, Result resultCode, Intent? data)
    {
        RequestCode = requestCode;
        ResultCode = resultCode;
        Data = data;
    }

    public int RequestCode { get; }
    public Result ResultCode { get; }
    public Intent? Data { get; }
}
