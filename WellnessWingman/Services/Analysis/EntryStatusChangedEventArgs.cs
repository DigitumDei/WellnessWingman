using WellnessWingman.Models;

namespace WellnessWingman.Services.Analysis;

public class EntryStatusChangedEventArgs : EventArgs
{
    public int EntryId { get; }
    public ProcessingStatus Status { get; }

    public EntryStatusChangedEventArgs(int entryId, ProcessingStatus status)
    {
        EntryId = entryId;
        Status = status;
    }
}
