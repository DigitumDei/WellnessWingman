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
    /// Android emulator name (default: appiumtest)
    /// </summary>
    public static string AndroidDeviceName => ResolveDeviceId();

    /// <summary>
    /// Specific device/emulator identifier (UDID/serial) to target when multiple devices run.
    /// </summary>
    public static string AndroidUdid => Environment.GetEnvironmentVariable("ANDROID_UDID") ?? ResolveDeviceId();

    /// <summary>
    /// Android platform version (default: 14)
    /// </summary>
    public static string AndroidPlatformVersion => Environment.GetEnvironmentVariable("ANDROID_PLATFORM_VERSION") ?? "14";

    /// <summary>
    /// Path to the WellnessWingman APK file
    /// </summary>
    public static string AppPath => ResolveAppPath();

    /// <summary>
    /// App package name
    /// </summary>
    public static string AppPackage => Environment.GetEnvironmentVariable("WELLNESS_WINGMAN_APP_PACKAGE")
        ?? "com.digitumdei.WellnessWingman";

    /// <summary>
    /// App activity name (optional; let Appium resolve from manifest when unset)
    /// </summary>
    public static string? AppActivity => Environment.GetEnvironmentVariable("WELLNESS_WINGMAN_APP_ACTIVITY");

    /// <summary>
    /// Implicit wait timeout in seconds
    /// </summary>
    public static int ImplicitWaitSeconds => 10;

    /// <summary>
    /// Command timeout in seconds
    /// </summary>
    public static int CommandTimeoutSeconds => 120;

    /// <summary>
    /// Android SDK root used by Appium (must be set via ANDROID_SDK_ROOT or ANDROID_HOME environment variable)
    /// </summary>
    public static string? AndroidSdkRoot =>
        Environment.GetEnvironmentVariable("ANDROID_SDK_ROOT") ??
        Environment.GetEnvironmentVariable("ANDROID_HOME");

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

    /// <summary>
    /// Resolve the APK path, preferring env var and falling back to common build output locations.
    /// </summary>
    private static string ResolveAppPath()
    {
        var fromEnv = Environment.GetEnvironmentVariable("WELLNESS_WINGMAN_APK_PATH");
        if (!string.IsNullOrWhiteSpace(fromEnv) && File.Exists(fromEnv))
        {
            return fromEnv;
        }

        var root = GetProjectRoot();
        var candidates = new[]
        {
            Path.Combine(root, "WellnessWingman", "bin", "Debug", "net10.0-android", "com.digitumdei.WellnessWingman-Signed.apk"),
            Path.Combine(root, "WellnessWingman", "bin", "Debug", "net10.0-android", "com.digitumdei.WellnessWingman.apk"),
            Path.Combine(root, "WellnessWingman", "bin", "Debug", "net10.0-android", "publish", "com.digitumdei.WellnessWingman-Signed.apk"),
            Path.Combine(root, "WellnessWingman", "bin", "Debug", "net10.0-android", "publish", "com.digitumdei.WellnessWingman.apk")
        };

        foreach (var candidate in candidates)
        {
            if (File.Exists(candidate))
            {
                return candidate;
            }
        }

        throw new FileNotFoundException(
            $"Could not locate the APK. Set WELLNESS_WINGMAN_APK_PATH or ensure one of these exists:{Environment.NewLine}{string.Join(Environment.NewLine, candidates)}");
    }

    /// <summary>
    /// Determine a device id/udid: prefer env var, otherwise pick the first connected adb device, else a sane default.
    /// </summary>
    private static string ResolveDeviceId()
    {
        var fromEnv = Environment.GetEnvironmentVariable("ANDROID_DEVICE_NAME");
        if (!string.IsNullOrWhiteSpace(fromEnv))
        {
            return fromEnv;
        }

        var firstDevice = GetFirstConnectedDeviceSerial();
        if (!string.IsNullOrWhiteSpace(firstDevice))
        {
            Console.WriteLine($"Using first connected device: {firstDevice}");
            return firstDevice;
        }

        return "emulator-5554";
    }

    /// <summary>
    /// Return the first adb device serial (if any).
    /// </summary>
    private static string? GetFirstConnectedDeviceSerial()
    {
        try
        {
            var startInfo = new System.Diagnostics.ProcessStartInfo
            {
                FileName = "adb",
                Arguments = "devices",
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };

            using var process = System.Diagnostics.Process.Start(startInfo);
            if (process == null) return null;

            var output = process.StandardOutput.ReadToEnd();
            process.WaitForExit(5000);

            var lines = output.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            foreach (var line in lines)
            {
                if (line.Contains("List of devices attached", StringComparison.OrdinalIgnoreCase)) continue;
                var parts = line.Split('\t', StringSplitOptions.RemoveEmptyEntries);
                if (parts.Length >= 2 && parts[1].Equals("device", StringComparison.OrdinalIgnoreCase))
                {
                    return parts[0].Trim();
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Failed to detect adb devices: {ex.Message}");
        }

        return null;
    }
}
