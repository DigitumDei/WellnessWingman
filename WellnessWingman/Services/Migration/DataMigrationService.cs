using System.IO.Compression;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.EntityFrameworkCore;
using WellnessWingman.Data;
using WellnessWingman.Models.Export;

namespace WellnessWingman.Services.Migration;

public class DataMigrationService : IDataMigrationService
{
    private readonly WellnessWingmanDbContext _dbContext;

    public DataMigrationService(WellnessWingmanDbContext dbContext)
    {
        _dbContext = dbContext;
    }

    public async Task<string> ExportDataAsync()
    {
        // 1. Gather Data
        var entries = await _dbContext.TrackedEntries.AsNoTracking().ToListAsync();
        var analyses = await _dbContext.EntryAnalyses.AsNoTracking().ToListAsync();
        var summaries = await _dbContext.DailySummaries.AsNoTracking().ToListAsync();

        var exportData = new ExportData
        {
            Entries = entries,
            Analyses = analyses,
            Summaries = summaries
        };

        // 2. Serialize Data
        var jsonOptions = new JsonSerializerOptions
        {
            WriteIndented = true,
            ReferenceHandler = ReferenceHandler.IgnoreCycles,
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
        };
        var json = JsonSerializer.Serialize(exportData, jsonOptions);

        // 3. Create Zip
        var fileName = $"wellnesswingman_export_{DateTime.Now:yyyyMMdd_HHmmss}.zip";
        var zipPath = Path.Combine(FileSystem.CacheDirectory, fileName);

        if (File.Exists(zipPath))
            File.Delete(zipPath);

        using (var zipArchive = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            // Add JSON
            var jsonEntry = zipArchive.CreateEntry("data.json");
            using (var writer = new StreamWriter(jsonEntry.Open()))
            {
                await writer.WriteAsync(json);
            }

            // Add Images
            foreach (var entry in entries)
            {
                if (!string.IsNullOrEmpty(entry.BlobPath))
                {
                    var fullPath = Path.Combine(FileSystem.AppDataDirectory, entry.BlobPath);
                    if (File.Exists(fullPath))
                    {
                        // Use the relative path as the entry name in the zip to preserve structure
                        // Normalize slashes to forward slashes for zip compatibility
                        var entryName = entry.BlobPath.Replace('', '/');
                        zipArchive.CreateEntryFromFile(fullPath, entryName);
                    }
                }
            }
        }

        return zipPath;
    }

    public async Task ImportDataAsync(string zipFilePath)
    {
        var tempDir = Path.Combine(FileSystem.CacheDirectory, "import_temp_" + Guid.NewGuid());
        Directory.CreateDirectory(tempDir);

        try
        {
            ZipFile.ExtractToDirectory(zipFilePath, tempDir);

            var jsonPath = Path.Combine(tempDir, "data.json");
            if (!File.Exists(jsonPath))
            {
                throw new FileNotFoundException("data.json not found in import file.");
            }

            var json = await File.ReadAllTextAsync(jsonPath);
             var jsonOptions = new JsonSerializerOptions
            {
                ReferenceHandler = ReferenceHandler.IgnoreCycles,
                PropertyNameCaseInsensitive = true
            };
            var exportData = JsonSerializer.Deserialize<ExportData>(json, jsonOptions);

            if (exportData == null)
            {
                throw new InvalidOperationException("Failed to deserialize import data.");
            }

            // Import Images
            // Iterate through files in tempDir recursively (excluding data.json)
            foreach (var file in Directory.GetFiles(tempDir, "*", SearchOption.AllDirectories))
            {
                var relativePath = Path.GetRelativePath(tempDir, file);
                if (relativePath.Equals("data.json", StringComparison.OrdinalIgnoreCase))
                    continue;

                var destPath = Path.Combine(FileSystem.AppDataDirectory, relativePath);
                var destDir = Path.GetDirectoryName(destPath);
                if (destDir != null)
                    Directory.CreateDirectory(destDir);

                File.Copy(file, destPath, overwrite: true);
            }

            // Import Data (Upsert)
            // We need to detach tracked entities to avoid conflicts if we are using the same context context
            // But here we are just adding them.
            // Since we are likely importing into a fresh install or overwriting, we should handle IDs.
            // If the ID exists, Update. If not, Add.
            
            // NOTE: Since these are unrelated to the current context tracking, we can just fetch existing IDs to check.

            // TrackedEntries
            foreach (var entry in exportData.Entries)
            {
                var existing = await _dbContext.TrackedEntries.FindAsync(entry.EntryId);
                if (existing != null)
                {
                    _dbContext.Entry(existing).CurrentValues.SetValues(entry);
                }
                else
                {
                    await _dbContext.TrackedEntries.AddAsync(entry);
                }
            }

            // EntryAnalyses
            foreach (var analysis in exportData.Analyses)
            {
                var existing = await _dbContext.EntryAnalyses.FindAsync(analysis.AnalysisId);
                if (existing != null)
                {
                    _dbContext.Entry(existing).CurrentValues.SetValues(analysis);
                }
                else
                {
                    await _dbContext.EntryAnalyses.AddAsync(analysis);
                }
            }

            // DailySummaries
            foreach (var summary in exportData.Summaries)
            {
                 var existing = await _dbContext.DailySummaries.FindAsync(summary.SummaryId);
                if (existing != null)
                {
                    _dbContext.Entry(existing).CurrentValues.SetValues(summary);
                }
                else
                {
                    await _dbContext.DailySummaries.AddAsync(summary);
                }
            }

            await _dbContext.SaveChangesAsync();
        }
        finally
        {
            if (Directory.Exists(tempDir))
                Directory.Delete(tempDir, true);
        }
    }
}
