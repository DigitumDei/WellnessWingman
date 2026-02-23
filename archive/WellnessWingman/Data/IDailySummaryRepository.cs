
using WellnessWingman.Models;

namespace WellnessWingman.Data;

public interface IDailySummaryRepository
{
    Task AddOrUpdateAsync(DailySummary summary);
    Task<DailySummary?> GetLatestAsync();
}
