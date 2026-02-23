using System.Threading.Tasks;

namespace WellnessWingman.Services.Migration;

public interface IDataMigrationService
{
    Task<string> ExportDataAsync();
    Task ImportDataAsync(string zipFilePath);
}
