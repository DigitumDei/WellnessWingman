using System;
using System.IO;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using WellnessWingman.Data;
using WellnessWingman.Models;
using WellnessWingman.Services.Analysis;
using WellnessWingman.Services.Platform;
using WellnessWingman.Utilities;

namespace WellnessWingman.Services.Media;

public class PhotoCaptureFinalizationService : IPhotoCaptureFinalizationService
{
    private readonly ITrackedEntryRepository _trackedEntryRepository;
    private readonly IBackgroundAnalysisService _backgroundAnalysisService;
    private readonly INotificationPermissionService _notificationPermissionService;
    private readonly IPhotoResizer _photoResizer;
    private readonly ILogger<PhotoCaptureFinalizationService> _logger;

    public PhotoCaptureFinalizationService(
        ITrackedEntryRepository trackedEntryRepository,
        IBackgroundAnalysisService backgroundAnalysisService,
        INotificationPermissionService notificationPermissionService,
        IPhotoResizer photoResizer,
        ILogger<PhotoCaptureFinalizationService> logger)
    {
        _trackedEntryRepository = trackedEntryRepository;
        _backgroundAnalysisService = backgroundAnalysisService;
        _notificationPermissionService = notificationPermissionService;
        _photoResizer = photoResizer;
        _logger = logger;
    }

    public async Task<TrackedEntry?> FinalizeAsync(PendingPhotoCapture capture, string? description)
    {
        try
        {
            string originalPath = capture.OriginalAbsolutePath;
            string previewPath = capture.PreviewAbsolutePath;

            if (!File.Exists(originalPath))
            {
                _logger.LogError("FinalizeAsync: Captured photo file is missing at {OriginalPath}", originalPath);
                return null;
            }

            if (new FileInfo(originalPath).Length == 0)
            {
                _logger.LogError("FinalizeAsync: Captured photo file is empty at {OriginalPath}", originalPath);
                return null;
            }

            Directory.CreateDirectory(Path.GetDirectoryName(previewPath)!);

            File.Copy(originalPath, previewPath, overwrite: true);
            _logger.LogInformation("FinalizeAsync: Preview copy refreshed at {PreviewPath}", previewPath);

            await _photoResizer.ResizeAsync(previewPath, 1280, 1280);
            _logger.LogInformation("FinalizeAsync: Preview resized");

            var timeZoneId = capture.CapturedAtTimeZoneId;
            var offsetMinutes = capture.CapturedAtOffsetMinutes;

            if (timeZoneId is null || offsetMinutes is null)
            {
                var metadata = DateTimeConverter.CaptureTimeZoneMetadata(capture.CapturedAtUtc);
                timeZoneId ??= metadata.TimeZoneId;
                offsetMinutes ??= metadata.OffsetMinutes;
            }

            // Use the provided description, or null if not provided
            var finalDescription = string.IsNullOrWhiteSpace(description) ? null : description.Trim();

            var newEntry = new TrackedEntry
            {
                EntryType = EntryType.Unknown,
                CapturedAt = capture.CapturedAtUtc,
                CapturedAtTimeZoneId = timeZoneId,
                CapturedAtOffsetMinutes = offsetMinutes,
                BlobPath = capture.OriginalRelativePath,
                Payload = new PendingEntryPayload
                {
                    Description = finalDescription,
                    PreviewBlobPath = capture.PreviewRelativePath
                },
                DataSchemaVersion = 0,
                ProcessingStatus = ProcessingStatus.Pending
            };

            bool entryPersisted = false;

            try
            {
                await _trackedEntryRepository.AddAsync(newEntry);
                entryPersisted = true;
                _logger.LogInformation("FinalizeAsync: Database entry created with ID {EntryId}", newEntry.EntryId);

                try
                {
                    // Request notification permission on first photo capture (Android 13+)
                    // This enables foreground service to keep analysis running when screen is locked
                    await _notificationPermissionService.EnsurePermissionAsync();

                    await _backgroundAnalysisService.QueueEntryAsync(newEntry.EntryId);
                    _logger.LogInformation("FinalizeAsync: Entry queued for background analysis");
                }
                catch (Exception queueEx)
                {
                    _logger.LogError(queueEx, "FinalizeAsync: Failed to queue background analysis for entry {EntryId}.", newEntry.EntryId);
                    // Note: Caller should handle displaying error to user if needed
                }

                return newEntry;
            }
            catch
            {
                if (entryPersisted)
                {
                    _logger.LogWarning("FinalizeAsync: Rolling back database entry {EntryId} due to failure.", newEntry.EntryId);
                    await _trackedEntryRepository.DeleteAsync(newEntry.EntryId);
                }

                throw;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "FinalizeAsync: Failed to finalize photo capture");
            return null;
        }
    }
}
