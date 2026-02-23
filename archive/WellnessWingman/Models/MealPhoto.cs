using CommunityToolkit.Mvvm.ComponentModel;

namespace WellnessWingman.Models;

public partial class MealPhoto : TrackedEntryCard
{
    public MealPhoto(
        int entryId,
        string fullPath,
        string originalPath,
        string description,
        DateTime capturedAtUtc,
        string? capturedAtTimeZoneId,
        int? capturedAtOffsetMinutes,
        ProcessingStatus processingStatus,
        EntryType entryType = EntryType.Meal)
        : base(
            entryId,
            entryType,
            capturedAtUtc,
            capturedAtTimeZoneId,
            capturedAtOffsetMinutes,
            processingStatus)
    {
        FullPath = fullPath;
        OriginalPath = originalPath;
        this.description = description;
    }

    public string FullPath { get; }
    public string OriginalPath { get; }

    [ObservableProperty]
    private string description;
}
