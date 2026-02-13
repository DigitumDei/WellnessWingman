using System.IO;
using CommunityToolkit.Maui;
using WellnessWingman.Data;
using WellnessWingman.PageModels;
using WellnessWingman.Pages;
using WellnessWingman.Services.Analysis;
using WellnessWingman.Services.Logging;
using WellnessWingman.Services.Media;
using WellnessWingman.Services.Navigation;
using WellnessWingman.Services.Llm;
using WellnessWingman.Services.Share;
using WellnessWingman.Services.Platform;
using Microsoft.Maui.Storage;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace WellnessWingman;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder
            .UseMauiApp<App>()
            .UseMauiCommunityToolkit()
            .ConfigureFonts(fonts =>
            {
                fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular");
                fonts.AddFont("OpenSans-Semibold.ttf", "OpenSansSemibold");
                fonts.AddFont("SegoeUI-Semibold.ttf", "SegoeSemibold");
                fonts.AddFont("FluentSystemIcons-Regular.ttf", FluentUI.FontFamily);
            });

        var logsDirectory = Path.Combine(FileSystem.AppDataDirectory, "logs");
        var logFilePath = Path.Combine(logsDirectory, "wellnesswingman.log");
        const long maxLogFileSize = 1024 * 1024; // 1 MB

        builder.Logging.AddConsole();
        builder.Logging.AddProvider(new FileLoggerProvider(logFilePath, maxLogFileSize));

#if DEBUG
        builder.Logging.AddDebug();
#endif

        string dbPath = Path.Combine(FileSystem.AppDataDirectory, "wellnesswingman.db3");
        builder.Services.AddDbContext<WellnessWingmanDbContext>(options => options.UseSqlite($"Filename={dbPath}"));

        builder.Services.AddScoped<ITrackedEntryRepository, SqliteTrackedEntryRepository>();
        builder.Services.AddScoped<IEntryAnalysisRepository, SqliteEntryAnalysisRepository>();
        builder.Services.AddScoped<IDailySummaryRepository, SqliteDailySummaryRepository>();

        builder.Services.AddSingleton<IAppSettingsRepository, SecureStorageAppSettingsRepository>();
        builder.Services.AddSingleton<ILogFileService>(_ => new LogFileService(logFilePath));
        builder.Services.AddSingleton<IBackgroundAnalysisService, BackgroundAnalysisService>();
        builder.Services.AddScoped<IStaleEntryRecoveryService, StaleEntryRecoveryService>();
        builder.Services.AddSingleton<IPendingPhotoStore, FilePendingPhotoStore>();
        builder.Services.AddScoped<IPhotoCaptureFinalizationService, PhotoCaptureFinalizationService>();

        // Enable mock services for UI testing
        // Check environment variable first, then fall back to DEBUG mode check
        var useMockServices = Environment.GetEnvironmentVariable("USE_MOCK_SERVICES") == "true";

#if DEBUG
        // In DEBUG builds, also check for a marker file that UI tests can create
        var appDataDir = FileSystem.AppDataDirectory;
        var mockMarkerFile = Path.Combine(appDataDir, ".use_mock_services");
        if (File.Exists(mockMarkerFile))
        {
            useMockServices = true;
        }
#endif

#if ANDROID
        builder.Services.AddSingleton<IPhotoResizer, AndroidPhotoResizer>();
        if (useMockServices)
        {
            builder.Services.AddSingleton<ICameraCaptureService, MockCameraCaptureService>();
        }
        else
        {
            builder.Services.AddSingleton<ICameraCaptureService, AndroidCameraCaptureService>();
        }
        builder.Services.AddSingleton<IAudioRecordingService, AndroidAudioRecordingService>();
        builder.Services.AddScoped<IShareIntentProcessor, ShareIntentProcessor>();
        builder.Services.AddSingleton<IBackgroundExecutionService, WellnessWingman.Platforms.Android.Services.AndroidBackgroundExecutionService>();
        builder.Services.AddSingleton<INotificationPermissionService, WellnessWingman.Platforms.Android.Services.AndroidNotificationPermissionService>();
#elif IOS
        builder.Services.AddSingleton<IPhotoResizer, NoOpPhotoResizer>();
        if (useMockServices)
        {
            builder.Services.AddSingleton<ICameraCaptureService, MockCameraCaptureService>();
        }
        else
        {
            builder.Services.AddSingleton<ICameraCaptureService, MediaPickerCameraCaptureService>();
        }
        builder.Services.AddSingleton<IAudioRecordingService, IOSAudioRecordingService>();
        builder.Services.AddSingleton<IBackgroundExecutionService, WellnessWingman.Platforms.iOS.Services.IOSBackgroundExecutionService>();
        builder.Services.AddSingleton<INotificationPermissionService, NoOpNotificationPermissionService>();
#else
        builder.Services.AddSingleton<IPhotoResizer, NoOpPhotoResizer>();
        if (useMockServices)
        {
            builder.Services.AddSingleton<ICameraCaptureService, MockCameraCaptureService>();
        }
        else
        {
            builder.Services.AddSingleton<ICameraCaptureService, MediaPickerCameraCaptureService>();
        }
        builder.Services.AddSingleton<IAudioRecordingService, NoOpAudioRecordingService>();
        builder.Services.AddSingleton<IBackgroundExecutionService, NoOpBackgroundExecutionService>();
        builder.Services.AddSingleton<INotificationPermissionService, NoOpNotificationPermissionService>();
#endif

        builder.Services.AddSingleton<ISharedImageDraftStore, InMemorySharedImageDraftStore>();
        builder.Services.AddSingleton<IShareNavigationService, ShareNavigationService>();
        builder.Services.AddScoped<ISharedImageImportService, SharedImageImportService>();

        builder.Services.AddSingleton<HistoricalNavigationContext>();
        builder.Services.AddSingleton<IHistoricalNavigationService, HistoricalNavigationService>();
        
        builder.Services.AddScoped<Services.Migration.IDataMigrationService, Services.Migration.DataMigrationService>();


        builder.Services.AddTransient<EntryLogViewModel>();
        builder.Services.AddTransient<WeekViewModel>();
        builder.Services.AddTransient<MainPage>();
        builder.Services.AddTransient<WeekViewPage>();
        builder.Services.AddTransient<MonthViewPage>();
        builder.Services.AddTransient<YearViewPage>();
        builder.Services.AddTransient<DayDetailPage>();

        builder.Services.AddTransient<SettingsViewModel>();
        builder.Services.AddTransient<SettingsPage>();

        builder.Services.AddTransient<MealDetailViewModel>();
        builder.Services.AddTransient<MealDetailPage>();
        builder.Services.AddTransient<SleepDetailViewModel>();
        builder.Services.AddTransient<SleepDetailPage>();

        builder.Services.AddTransient<DailySummaryViewModel>();
        builder.Services.AddTransient<DailySummaryPage>();
        builder.Services.AddTransient<ExerciseDetailViewModel>();
        builder.Services.AddTransient<ExerciseDetailPage>();
        builder.Services.AddTransient<ShareEntryViewModel>();
        builder.Services.AddTransient<ShareEntryPage>();
        builder.Services.AddTransient<PhotoReviewPageViewModel>();
        builder.Services.AddTransient<PhotoReviewPage>();

        builder.Services.AddTransient<IAnalysisOrchestrator, AnalysisOrchestrator>();
        builder.Services.AddTransient<IDailySummaryService, DailySummaryService>();
        builder.Services.AddTransient<DailyTotalsCalculator>();
        builder.Services.AddTransient<UnifiedAnalysisHelper>(); // New service
        if (useMockServices)
        {
            builder.Services.AddTransient<MockLlmClient>();
            builder.Services.AddTransient<ILlmClientFactory, MockLlmClientFactory>();
            builder.Services.AddTransient<MockAudioTranscriptionService>();
            builder.Services.AddTransient<IAudioTranscriptionServiceFactory, MockAudioTranscriptionServiceFactory>();
        }
        else
        {
            builder.Services.AddTransient<OpenAiLlmClient>();
            builder.Services.AddTransient<GeminiLlmClient>();
            builder.Services.AddTransient<ILlmClientFactory, LlmClientFactory>();
            builder.Services.AddTransient<OpenAiAudioTranscriptionService>();
            builder.Services.AddTransient<GeminiAudioTranscriptionService>();
            builder.Services.AddTransient<IAudioTranscriptionServiceFactory, AudioTranscriptionServiceFactory>();
        }
        builder.Services.AddSingleton<MealAnalysisValidator>();
        builder.Services.AddTransient<WeekSummaryBuilder>();

        var app = builder.Build();

        // Run migrations
        using (var scope = app.Services.CreateScope())
        {
            var dbContext = scope.ServiceProvider.GetRequiredService<WellnessWingmanDbContext>();
            dbContext.Database.Migrate();
        }

        return app;
    }
}
