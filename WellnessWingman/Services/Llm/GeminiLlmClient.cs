using System.Text;
using System.Text.Json;
using Google.GenAI;
using Google.GenAI.Types;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.Storage;
using WellnessWingman.Models;
using WellnessWingman.Utilities;
using File = System.IO.File;

namespace WellnessWingman.Services.Llm;

public class GeminiLlmClient : ILLmClient
{
    private readonly ILogger<GeminiLlmClient> _logger;

    public GeminiLlmClient(ILogger<GeminiLlmClient> logger)
    {
        _logger = logger;
    }

    public async Task<LlmAnalysisResult> InvokeAnalysisAsync(
        TrackedEntry entry,
        LlmRequestContext context,
        string? existingAnalysisJson = null,
        string? userProvidedDetails = null)
    {
        if (string.IsNullOrWhiteSpace(context.ApiKey))
        {
            throw new InvalidOperationException("Gemini API key is not configured.");
        }

        var prompt = await BuildUnifiedPromptAsync(entry, existingAnalysisJson, userProvidedDetails).ConfigureAwait(false);
        var parts = new List<Part>
        {
            new() { Text = prompt }
        };

        if (!string.IsNullOrWhiteSpace(entry.BlobPath))
        {
            var absolutePath = Path.Combine(FileSystem.AppDataDirectory, entry.BlobPath);
            if (File.Exists(absolutePath))
            {
                var mimeType = Path.GetExtension(entry.BlobPath).ToLowerInvariant() switch
                {
                    ".jpg" => "image/jpeg",
                    ".jpeg" => "image/jpeg",
                    ".png" => "image/png",
                    ".gif" => "image/gif",
                    _ => "image/jpeg"
                };

                var imageBytes = await File.ReadAllBytesAsync(absolutePath).ConfigureAwait(false);
                parts.Add(new Part
                {
                    InlineData = new Blob
                    {
                        MimeType = mimeType,
                        Data = imageBytes
                    }
                });
            }
        }

        try
        {
            var client = new Client(apiKey: context.ApiKey);
            var response = await client.Models.GenerateContentAsync(
                model: context.ModelId,
                contents: [new Content { Role = "user", Parts = parts }],
                config: new GenerateContentConfig
                {
                    ResponseMimeType = "application/json"
                }).ConfigureAwait(false);

            var insights = GeminiResponseParser.ExtractText(response);
            var normalizedInsights = GeminiResponseParser.ExtractFirstJsonObject(insights) ?? insights;
            if (!string.Equals(insights, normalizedInsights, StringComparison.Ordinal))
            {
                _logger.LogInformation("Trimmed Gemini response to first JSON object for entry {EntryId}.", entry.EntryId);
            }

            UnifiedAnalysisResult? parsedResult = null;
            try
            {
                parsedResult = JsonSerializer.Deserialize<UnifiedAnalysisResult>(normalizedInsights);
                _logger.LogInformation(
                    "Parsed unified analysis for entry {EntryId} detected as {EntryType}.",
                    entry.EntryId,
                    parsedResult?.EntryType ?? "Unknown");
            }
            catch (JsonException jsonEx)
            {
                _logger.LogWarning(jsonEx, "Failed to parse unified analysis response. Storing raw JSON. Response: {Response}", normalizedInsights);
            }

            var analysis = new EntryAnalysis
            {
                EntryId = entry.EntryId,
                ProviderId = context.Provider.ToString(),
                Model = context.ModelId,
                CapturedAt = DateTime.UtcNow,
                InsightsJson = normalizedInsights,
                SchemaVersion = parsedResult?.SchemaVersion ?? "unknown"
            };

            return new LlmAnalysisResult
            {
                Analysis = analysis,
                Diagnostics = CreateDiagnostics(response)
            };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Gemini unified analysis request failed.");
            throw;
        }
    }

    public async Task<LlmAnalysisResult> InvokeDailySummaryAsync(
        DailySummaryRequest summaryRequest,
        LlmRequestContext context,
        string? existingSummaryJson = null)
    {
        if (string.IsNullOrWhiteSpace(context.ApiKey))
        {
            throw new InvalidOperationException("Gemini API key is not configured.");
        }

        var prompt = await BuildDailySummaryPromptAsync(summaryRequest, existingSummaryJson).ConfigureAwait(false);

        try
        {
            var client = new Client(apiKey: context.ApiKey);
            var response = await client.Models.GenerateContentAsync(
                model: context.ModelId,
                contents: [new Content { Role = "user", Parts = [new Part { Text = prompt }] }],
                config: new GenerateContentConfig
                {
                    ResponseMimeType = "application/json"
                }).ConfigureAwait(false);

            var insights = GeminiResponseParser.ExtractText(response);
            var normalizedInsights = GeminiResponseParser.ExtractFirstJsonObject(insights) ?? insights;
            if (!string.Equals(insights, normalizedInsights, StringComparison.Ordinal))
            {
                _logger.LogInformation("Trimmed Gemini response to first JSON object for summary entry {EntryId}.", summaryRequest.SummaryEntryId);
            }

            DailySummaryResult? parsedResult = null;
            try
            {
                parsedResult = JsonSerializer.Deserialize<DailySummaryResult>(normalizedInsights);
                _logger.LogInformation(
                    "Parsed structured daily summary for {SummaryDate} covering {EntryCount} entries.",
                    summaryRequest.SummaryDate.ToString("yyyy-MM-dd"),
                    summaryRequest.Entries.Count);
            }
            catch (JsonException jsonEx)
            {
                _logger.LogWarning(jsonEx, "Failed to parse structured daily summary response. Storing raw JSON. Response: {Response}", normalizedInsights);
            }

            var analysis = new EntryAnalysis
            {
                EntryId = summaryRequest.SummaryEntryId,
                ProviderId = context.Provider.ToString(),
                Model = context.ModelId,
                CapturedAt = DateTime.UtcNow,
                InsightsJson = normalizedInsights,
                SchemaVersion = parsedResult?.SchemaVersion ?? "unknown"
            };

            return new LlmAnalysisResult
            {
                Analysis = analysis,
                Diagnostics = CreateDiagnostics(response)
            };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Gemini daily summary request failed.");
            throw;
        }
    }

    private static LlmDiagnostics? CreateDiagnostics(GenerateContentResponse response)
    {
        if (response.UsageMetadata is null)
        {
            return null;
        }

        return new LlmDiagnostics
        {
            PromptTokenCount = response.UsageMetadata.PromptTokenCount,
            CompletionTokenCount = response.UsageMetadata.CandidatesTokenCount,
            TotalTokenCount = response.UsageMetadata.TotalTokenCount
        };
    }

    private static async Task<string> BuildUnifiedPromptAsync(
        TrackedEntry entry,
        string? existingAnalysisJson,
        string? userProvidedDetails)
    {
        var schema = await PromptSchemas.GetUnifiedAnalysisSchemaAsync().ConfigureAwait(false);

        var builder = new StringBuilder();
        builder.AppendLine("You are a helpful assistant that analyzes images to track health and wellness.");
        builder.AppendLine();
        builder.AppendLine("First, determine the entry type based on the image contents:");
        builder.AppendLine("- \"Meal\": Food, beverages, nutrition labels, meal prep scenes.");
        builder.AppendLine("- \"Exercise\": Workout screenshots or photos showing fitness data (runs, rides, gym tracking, heart rate charts).");
        builder.AppendLine("- \"Sleep\": Sleep tracking screenshots, bedroom environments, beds indicating rest.");
        builder.AppendLine("- \"Other\": Anything else that doesn't fit the categories above.");
        builder.AppendLine();
        builder.AppendLine("Then provide a detailed analysis for the detected type:");
        builder.AppendLine("- Meals: identify foods, estimate portions and nutrition, note health insights and recommendations.");
        builder.AppendLine("- Exercise: extract displayed metrics (distance, duration, pace, calories, heart rate, etc.) and offer performance feedback.");
        builder.AppendLine("- Sleep: summarise sleep duration/quality metrics, environment observations, improvement tips.");
        builder.AppendLine("- Other: briefly describe the content and provide any helpful observations.");
        builder.AppendLine();
        builder.AppendLine("Return JSON that exactly matches this schema:");
        builder.AppendLine(schema);
        builder.AppendLine();
        builder.AppendLine("Important rules:");
        builder.AppendLine("- Only populate the analysis object that matches the detected entryType; set the others to null.");
        builder.AppendLine("- Always include a confidence score between 0.0 and 1.0 reflecting how certain you are about the classification.");
        builder.AppendLine("- Include warnings when information is missing, unclear, or potentially incorrect.");
        builder.AppendLine("- If the user provides a correction, incorporate it and regenerate the full JSON.");
        builder.AppendLine("- Respond with JSON only, no markdown.");

        if (!string.IsNullOrWhiteSpace(existingAnalysisJson))
        {
            builder.AppendLine();
            builder.AppendLine("PreviousAnalysisJson:");
            builder.AppendLine(existingAnalysisJson);
            builder.AppendLine("Update the JSON to reflect the latest instructions.");
        }

        if (!string.IsNullOrWhiteSpace(userProvidedDetails))
        {
            builder.AppendLine();
            builder.AppendLine("User correction:");
            builder.AppendLine(userProvidedDetails.Trim());
        }

        if (entry.EntryType != EntryType.Unknown && entry.EntryType != EntryType.DailySummary)
        {
            builder.AppendLine();
            builder.AppendLine($"ExpectedEntryTypeHint: {entry.EntryType}");
        }

        return builder.ToString();
    }

    private static async Task<string> BuildDailySummaryPromptAsync(DailySummaryRequest summaryRequest, string? existingSummaryJson)
    {
        var schema = await PromptSchemas.GetDailySummarySchemaAsync().ConfigureAwait(false);

        var builder = new StringBuilder();
        builder.AppendLine("You are a helpful wellness coach generating a daily summary from the day's tracked activities.");
        builder.AppendLine("Entries may include meals, exercise sessions, sleep logs, and other health-related items.");
        builder.AppendLine("The user will provide pre-calculated nutritional totals. Use these numbers for your analysis; do not recalculate them.");
        builder.AppendLine("Focus your response on qualitative insights, timing, balance, and specific recommendations.");
        builder.AppendLine("Do not request or expect images – only use the supplied analysis data.");
        builder.AppendLine();
        builder.AppendLine("You MUST return a JSON object matching this exact schema:");
        builder.AppendLine(schema);
        builder.AppendLine();
        builder.AppendLine("Important rules:");
        builder.AppendLine("- Always include every required property from the schema");
        builder.AppendLine("- Provide empty arrays when there are no insights or recommendations");
        builder.AppendLine("- Use null for unknown numeric values");
        builder.AppendLine("- Ensure schemaVersion is \"1.0\"");
        builder.AppendLine("- Summaries should reference entryId values when describing insights");
        builder.AppendLine("- If no entries are available, still return a valid JSON object with empty collections and explanations");
        builder.AppendLine("- Respond with JSON only, no markdown.");
        builder.AppendLine();

        builder.AppendLine($"SummaryDate: {summaryRequest.SummaryDate:yyyy-MM-dd}");

        if (!string.IsNullOrWhiteSpace(summaryRequest.SummaryTimeZoneId) || summaryRequest.SummaryUtcOffsetMinutes is not null)
        {
            var offsetText = summaryRequest.SummaryUtcOffsetMinutes is int offset
                ? DateTimeConverter.FormatOffset(offset)
                : "unknown";
            builder.AppendLine($"SummaryTimeZone: {summaryRequest.SummaryTimeZoneId ?? "unknown"} (UTC{offsetText})");
        }

        builder.AppendLine("Calculated Nutritional Totals:");
        builder.AppendLine(JsonSerializer.Serialize(summaryRequest.CalculatedTotals));
        builder.AppendLine();

        builder.AppendLine($"EntriesCaptured: {summaryRequest.Entries.Count}");
        builder.AppendLine("Entries:");

        foreach (var (entry, index) in summaryRequest.Entries.Select((item, i) => (item, i + 1)))
        {
            builder.AppendLine($"- Entry {index} (EntryId: {entry.EntryId}, EntryType: {entry.EntryType})");
            builder.AppendLine($"  CapturedAtUtc: {entry.CapturedAt:O}");

            if (entry.CapturedAtLocal != default)
            {
                builder.AppendLine($"  CapturedAtLocal: {entry.CapturedAtLocal:O}");
            }
            else
            {
                builder.AppendLine("  CapturedAtLocal: unknown");
            }

            var entryOffsetText = entry.UtcOffsetMinutes is int entryOffset
                ? DateTimeConverter.FormatOffset(entryOffset)
                : "unknown";
            builder.AppendLine($"  TimeZone: {entry.TimeZoneId ?? "unknown"} (UTC{entryOffsetText})");
            if (!string.IsNullOrWhiteSpace(entry.Description))
            {
                builder.AppendLine($"  Description: {entry.Description}");
            }

            if (entry.Analysis is not null)
            {
                var json = JsonSerializer.Serialize(entry.Analysis);
                builder.AppendLine("  UnifiedAnalysisJson:");
                builder.AppendLine(json);
            }
            else
            {
                builder.AppendLine("  UnifiedAnalysisJson: null");
            }

            builder.AppendLine();
        }

        if (!string.IsNullOrWhiteSpace(existingSummaryJson))
        {
            builder.AppendLine("PreviousSummaryJson:");
            builder.AppendLine(existingSummaryJson);
            builder.AppendLine("Regenerate the complete daily summary JSON considering any new data.");
        }

        return builder.ToString();
    }
}
