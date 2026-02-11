using System.Collections.Generic;
using WellnessWingman.Models;

namespace WellnessWingman.Models.Export;

public class ExportData
{
    public int Version { get; set; } = 1;
    public DateTime ExportedAt { get; set; } = DateTime.UtcNow;
    public List<TrackedEntry> Entries { get; set; } = new();
    public List<EntryAnalysis> Analyses { get; set; } = new();
    public List<DailySummary> Summaries { get; set; } = new();
    public List<DailySummaryEntryAnalyses> SummariesAnalyses { get; set; } = new();
}
