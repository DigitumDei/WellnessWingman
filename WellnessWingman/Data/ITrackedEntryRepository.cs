
using System;
using System.Collections.Generic;
using WellnessWingman.Models;

namespace WellnessWingman.Data;

public interface ITrackedEntryRepository
{
    Task AddAsync(TrackedEntry entry);
    Task<IEnumerable<TrackedEntry>> GetByDayAsync(DateTime date, TimeZoneInfo? timeZone = null);
    Task<IEnumerable<TrackedEntry>> GetByEntryTypeAndDayAsync(EntryType entryType, DateTime date, TimeZoneInfo? timeZone = null);
    Task<IReadOnlyList<DaySummary>> GetDaySummariesForWeekAsync(DateTime weekStart, TimeZoneInfo? timeZone = null);
    Task DeleteAsync(int entryId);
    Task UpdateProcessingStatusAsync(int entryId, ProcessingStatus status);
    Task<TrackedEntry?> GetByIdAsync(int entryId);
    Task UpdateAsync(TrackedEntry entry);
    Task UpdateEntryTypeAsync(int entryId, EntryType entryType);
}
