
using System.ComponentModel.DataAnnotations.Schema;
using System.Text.Json.Serialization;
using WellnessWingman.Utilities;

namespace WellnessWingman.Models;

public class TrackedEntry
{
    public int EntryId { get; set; }
    public Guid? ExternalId { get; set; }
    public EntryType EntryType { get; set; } = EntryType.Unknown;
    public DateTime CapturedAt { get; set; }
    public string? CapturedAtTimeZoneId { get; set; }
    public int? CapturedAtOffsetMinutes { get; set; }
    public string? BlobPath { get; set; }
    public string DataPayload { get; set; } = string.Empty;
    public int DataSchemaVersion { get; set; }
    public ProcessingStatus ProcessingStatus { get; set; } = ProcessingStatus.Pending;

    /// <summary>
    /// User-provided notes (text or voice transcription) captured at time of photo submission.
    /// This field persists independently of LLM analysis and remains available for corrections.
    /// </summary>
    public string? UserNotes { get; set; }

    [NotMapped]
    [JsonIgnore]
    public IEntryPayload Payload { get; set; } = new PendingEntryPayload();

    [NotMapped]
    [JsonIgnore]
    public DateTime CapturedAtLocal => DateTimeConverter.ToOriginalLocal(
        CapturedAt,
        CapturedAtTimeZoneId,
        CapturedAtOffsetMinutes);
}
