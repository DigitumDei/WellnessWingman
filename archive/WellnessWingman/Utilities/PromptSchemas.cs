namespace WellnessWingman.Utilities;

/// <summary>
/// Provides cached access to LLM prompt schema files from embedded resources.
/// </summary>
public static class PromptSchemas
{
    private static string? _unifiedAnalysisSchema;
    private static string? _dailySummarySchema;

    /// <summary>
    /// Gets the unified analysis schema examples for LLM prompts.
    /// </summary>
    public static async Task<string> GetUnifiedAnalysisSchemaAsync()
    {
        if (_unifiedAnalysisSchema is not null)
        {
            return _unifiedAnalysisSchema;
        }

        _unifiedAnalysisSchema = await LoadResourceAsync("Prompts/UnifiedAnalysisSchema.txt").ConfigureAwait(false);
        return _unifiedAnalysisSchema;
    }

    /// <summary>
    /// Gets the daily summary JSON schema for LLM prompts.
    /// </summary>
    public static async Task<string> GetDailySummarySchemaAsync()
    {
        if (_dailySummarySchema is not null)
        {
            return _dailySummarySchema;
        }

        _dailySummarySchema = await LoadResourceAsync("Prompts/DailySummarySchema.json").ConfigureAwait(false);
        return _dailySummarySchema;
    }

    private static async Task<string> LoadResourceAsync(string resourceName)
    {
        using var stream = await FileSystem.OpenAppPackageFileAsync(resourceName).ConfigureAwait(false);
        using var reader = new StreamReader(stream);
        return await reader.ReadToEndAsync().ConfigureAwait(false);
    }
}
