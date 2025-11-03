using WellnessWingman.Models;

namespace WellnessWingman.Data;

public interface IAppSettingsRepository
{
    Task<AppSettings> GetAppSettingsAsync();
    Task SaveAppSettingsAsync(AppSettings settings);
}
