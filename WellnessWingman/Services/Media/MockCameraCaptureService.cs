namespace WellnessWingman.Services.Media;

/// <summary>
/// Mock camera capture service for E2E testing. Copies a test image instead of using the actual camera.
/// </summary>
public class MockCameraCaptureService : ICameraCaptureService
{
    private const string TestImageResourceName = "sample-meal.png";

    public async Task<CameraCaptureOutcome> CaptureAsync(PendingPhotoCapture capture, CancellationToken cancellationToken = default)
    {
        try
        {
            // Ensure destination directories exist
            var originalDir = Path.GetDirectoryName(capture.OriginalAbsolutePath);
            var previewDir = Path.GetDirectoryName(capture.PreviewAbsolutePath);

            if (!string.IsNullOrEmpty(originalDir))
            {
                Directory.CreateDirectory(originalDir);
            }

            if (!string.IsNullOrEmpty(previewDir))
            {
                Directory.CreateDirectory(previewDir);
            }

            // Copy test image from app package to the expected locations
            await using var sourceStream = await FileSystem.OpenAppPackageFileAsync(TestImageResourceName).ConfigureAwait(false);

            // Write to original path
            await using (var originalFile = File.Create(capture.OriginalAbsolutePath))
            {
                await sourceStream.CopyToAsync(originalFile, cancellationToken).ConfigureAwait(false);
            }

            // Reset stream position and copy to preview path
            sourceStream.Position = 0;
            await using (var previewFile = File.Create(capture.PreviewAbsolutePath))
            {
                await sourceStream.CopyToAsync(previewFile, cancellationToken).ConfigureAwait(false);
            }

            return CameraCaptureOutcome.Success();
        }
        catch (Exception ex)
        {
            return CameraCaptureOutcome.Failed($"Mock camera capture failed: {ex.Message}");
        }
    }
}
