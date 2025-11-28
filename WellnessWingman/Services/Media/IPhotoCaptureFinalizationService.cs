using System.Threading.Tasks;
using WellnessWingman.Models;

namespace WellnessWingman.Services.Media;

/// <summary>
/// Service responsible for finalizing photo captures by creating database entries
/// and queueing them for AI analysis.
/// </summary>
public interface IPhotoCaptureFinalizationService
{
    /// <summary>
    /// Finalizes a pending photo capture by creating a TrackedEntry and queuing it for analysis.
    /// </summary>
    /// <param name="capture">The pending photo capture metadata.</param>
    /// <param name="description">Optional user-provided description for the photo.</param>
    /// <returns>The created TrackedEntry, or null if finalization failed.</returns>
    Task<TrackedEntry?> FinalizeAsync(PendingPhotoCapture capture, string? description);
}
