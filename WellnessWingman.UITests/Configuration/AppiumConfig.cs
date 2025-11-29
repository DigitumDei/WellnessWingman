namespace WellnessWingman.UITests.Configuration;

/// <summary>
/// Configuration settings for Appium test execution
/// </summary>
public class AppiumConfig
{
    /// <summary>
    /// Appium server URL (default: http://127.0.0.1:4723)
    /// </summary>
    public static string AppiumServerUrl => Environment.GetEnvironmentVariable("APPIUM_SERVER_URL") ?? "http://127.0.0.1:4723";

    /// <summary>
    /// Android emulator name (default: emulator-5554)
    /// </summary>
    public static string AndroidDeviceName => Environment.GetEnvironmentVariable("ANDROID_DEVICE_NAME") ?? "emulator-5554";

    /// <summary>
    /// Android platform version (default: 14.0)
    /// </summary>
    public static string AndroidPlatformVersion => Environment.GetEnvironmentVariable("ANDROID_PLATFORM_VERSION") ?? "14.0";

    /// <summary>
    /// Path to the WellnessWingman APK file
    /// </summary>
    public static string AppPath => Environment.GetEnvironmentVariable("WELLNESS_WINGMAN_APK_PATH")
        ?? Path.Combine(GetProjectRoot(), "WellnessWingman", "bin", "Debug", "net10.0-android", "com.digitumDei.wellnesswingman-Signed.apk");

    /// <summary>
    /// App package name
    /// </summary>
    public static string AppPackage => "com.digitumDei.wellnesswingman";

    /// <summary>
    /// App activity name (MAUI default)
    /// </summary>
    public static string AppActivity => "crc6452ffdc5b34af3a0f.MainActivity";

    /// <summary>
    /// Implicit wait timeout in seconds
    /// </summary>
    public static int ImplicitWaitSeconds => 10;

    /// <summary>
    /// Command timeout in seconds
    /// </summary>
    public static int CommandTimeoutSeconds => 120;

    /// <summary>
    /// Gets the project root directory
    /// </summary>
    private static string GetProjectRoot()
    {
        var currentDirectory = Directory.GetCurrentDirectory();
        while (currentDirectory != null && !File.Exists(Path.Combine(currentDirectory, "WellnessWingman.slnx")))
        {
            currentDirectory = Directory.GetParent(currentDirectory)?.FullName;
        }
        return currentDirectory ?? Directory.GetCurrentDirectory();
    }
}
