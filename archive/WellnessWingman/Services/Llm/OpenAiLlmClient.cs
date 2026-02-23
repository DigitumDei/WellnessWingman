using Microsoft.Extensions.Logging;
using OpenAI;
using OpenAI.Chat;
using System.ClientModel;
using System.Text;
using System.Text.Json;
using WellnessWingman.Models;
using WellnessWingman.Utilities;

namespace WellnessWingman.Services.Llm;

public class OpenAiLlmClient : ILLmClient
{
    private readonly ILogger<OpenAiLlmClient> _logger;

    public OpenAiLlmClient(ILogger<OpenAiLlmClient> logger)
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
            throw new InvalidOperationException("OpenAI API key is not configured.");
        }

        var client = new OpenAIClient(context.ApiKey);
        var chatClient = client.GetChatClient(context.ModelId);

        var messages = await CreateUnifiedChatRequest(entry, existingAnalysisJson, userProvidedDetails).ConfigureAwait(false);

        try
        {
            var options = new ChatCompletionOptions
            {
                ResponseFormat = ChatResponseFormat.CreateJsonObjectFormat()
            };

            var response = await CompleteChatWithFallbackAsync(chatClient, messages, options).ConfigureAwait(false);

            var insights = ExtractTextContent(response.Value.Content);

            UnifiedAnalysisResult? parsedResult = null;
            try
            {
                parsedResult = JsonSerializer.Deserialize<UnifiedAnalysisResult>(insights);
                _logger.LogInformation(
                    "Parsed unified analysis for entry {EntryId} detected as {EntryType}.",
                    entry.EntryId,
                    parsedResult?.EntryType ?? "Unknown");
            }
            catch (JsonException jsonEx)
            {
                _logger.LogWarning(jsonEx, "Failed to parse unified analysis response. Storing raw JSON. Response: {Response}", insights);
            }

            var analysis = new EntryAnalysis
            {
                EntryId = entry.EntryId,
                ProviderId = context.Provider.ToString(),
                Model = context.ModelId,
                CapturedAt = DateTime.UtcNow,
                InsightsJson = insights,
                SchemaVersion = parsedResult?.SchemaVersion ?? "unknown"
            };

            var diagnostics = new LlmDiagnostics
            {
                PromptTokenCount = response.Value.Usage.InputTokenCount,
                CompletionTokenCount = response.Value.Usage.OutputTokenCount,
                TotalTokenCount = response.Value.Usage.TotalTokenCount,
            };

            return new LlmAnalysisResult
            {
                Analysis = analysis,
                Diagnostics = diagnostics
            };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OpenAI unified analysis request failed.");
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
            throw new InvalidOperationException("OpenAI API key is not configured.");
        }

        var client = new OpenAIClient(context.ApiKey);
        var chatClient = client.GetChatClient(context.ModelId);

        var messages = await CreateDailySummaryChatRequestAsync(summaryRequest, existingSummaryJson).ConfigureAwait(false);

        try
        {
            var options = new ChatCompletionOptions
            {
                ResponseFormat = ChatResponseFormat.CreateJsonObjectFormat()
            };

            var response = await CompleteChatWithFallbackAsync(chatClient, messages, options).ConfigureAwait(false);

            var insights = ExtractTextContent(response.Value.Content);

            DailySummaryResult? parsedResult = null;
            try
            {
                parsedResult = JsonSerializer.Deserialize<DailySummaryResult>(insights);
                _logger.LogInformation(
                    "Parsed structured daily summary for {SummaryDate} covering {EntryCount} entries.",
                    summaryRequest.SummaryDate.ToString("yyyy-MM-dd"),
                    summaryRequest.Entries.Count);
            }
            catch (JsonException jsonEx)
            {
                _logger.LogWarning(jsonEx, "Failed to parse structured daily summary response. Storing raw JSON. Response: {Response}", insights);
            }

            var analysis = new EntryAnalysis
            {
                EntryId = summaryRequest.SummaryEntryId,
                ProviderId = context.Provider.ToString(),
                Model = context.ModelId,
                CapturedAt = DateTime.UtcNow,
                InsightsJson = insights,
                SchemaVersion = parsedResult?.SchemaVersion ?? "unknown"
            };

            var diagnostics = new LlmDiagnostics
            {
                PromptTokenCount = response.Value.Usage.InputTokenCount,
                CompletionTokenCount = response.Value.Usage.OutputTokenCount,
                TotalTokenCount = response.Value.Usage.TotalTokenCount,
            };

            return new LlmAnalysisResult
            {
                Analysis = analysis,
                Diagnostics = diagnostics
            };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OpenAI daily summary request failed.");
            throw;
        }
    }

    private async Task<ClientResult<ChatCompletion>> CompleteChatWithFallbackAsync(
        ChatClient chatClient,
        IReadOnlyList<ChatMessage> messages,
        ChatCompletionOptions options)
    {
        try
        {
            return await chatClient.CompleteChatAsync(messages, options).ConfigureAwait(false);
        }
        catch (InvalidOperationException ex) when (IsResponseFormatSerializationBug(ex))
        {
            _logger.LogWarning(ex, "Response format serialization failed; retrying without explicit format.");
            return await chatClient.CompleteChatAsync(messages).ConfigureAwait(false);
        }
    }

    private static bool IsResponseFormatSerializationBug(InvalidOperationException ex)
    {
        return ex.Message?.Contains("WriteCore method", StringComparison.OrdinalIgnoreCase) == true;
    }

    private async Task<List<ChatMessage>> CreateUnifiedChatRequest(
        TrackedEntry entry,
        string? existingAnalysisJson,
        string? userProvidedDetails)
    {
        var schema = await PromptSchemas.GetUnifiedAnalysisSchemaAsync().ConfigureAwait(false);

        var systemPrompt = $@"You are a helpful assistant that analyzes images to track health and wellness.

First, determine the entry type based on the image contents:
- ""Meal"": Food, beverages, nutrition labels, meal prep scenes.
- ""Exercise"": Workout screenshots or photos showing fitness data (runs, rides, gym tracking, heart rate charts).
- ""Sleep"": Sleep tracking screenshots, bedroom environments, beds indicating rest.
- ""Other"": Anything else that doesn't fit the categories above.

Then provide a detailed analysis for the detected type:
- Meals: identify foods, estimate portions and nutrition, note health insights and recommendations.
- Exercise: extract displayed metrics (distance, duration, pace, calories, heart rate, etc.) and offer performance feedback.
- Sleep: summarise sleep duration/quality metrics, environment observations, improvement tips.
- Other: briefly describe the content and provide any helpful observations.

Return JSON that exactly matches this schema:
{schema}

Important rules:
- Only populate the analysis object that matches the detected entryType; set the others to null.
- Always include a confidence score between 0.0 and 1.0 reflecting how certain you are about the classification.
- Include warnings when information is missing, unclear, or potentially incorrect.
- If the user provides a correction, incorporate it and regenerate the full JSON.";

        var messages = new List<ChatMessage>
        {
            new SystemChatMessage(systemPrompt)
        };

        // Build the initial prompt text
        var promptText = "Analyze this image and return the unified JSON response.";

        var userContent = new List<ChatMessageContentPart>
        {
            ChatMessageContentPart.CreateTextPart(promptText)
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
                userContent.Add(ChatMessageContentPart.CreateImagePart(new BinaryData(imageBytes), mimeType));
            }
        }

        messages.Add(new UserChatMessage(userContent));

        if (!string.IsNullOrWhiteSpace(existingAnalysisJson))
        {
            messages.Add(new AssistantChatMessage(existingAnalysisJson));
            messages.Add(new UserChatMessage("The previous message is the earlier JSON response. Update it to reflect the latest instructions."));
        }

        if (!string.IsNullOrWhiteSpace(userProvidedDetails))
        {
            messages.Add(new UserChatMessage($"User correction:\n{userProvidedDetails.Trim()}"));
        }

        return messages;
    }

    private static string ExtractTextContent(IReadOnlyList<ChatMessageContentPart> contentParts)
    {
        if (contentParts is null || contentParts.Count == 0)
        {
            return string.Empty;
        }

        var builder = new StringBuilder();

        foreach (var part in contentParts)
        {
            if (part.Kind == ChatMessageContentPartKind.Text && !string.IsNullOrWhiteSpace(part.Text))
            {
                if (builder.Length > 0)
                {
                    builder.AppendLine();
                }

                builder.Append(part.Text);
            }
        }

        return builder.ToString();
    }

    private static async Task<List<ChatMessage>> CreateDailySummaryChatRequestAsync(
        DailySummaryRequest summaryRequest,
        string? existingSummaryJson)
    {
        var schema = await PromptSchemas.GetDailySummarySchemaAsync().ConfigureAwait(false);

        var systemPrompt = $@"You are a helpful wellness coach generating a daily summary from the day's tracked activities.
Entries may include meals, exercise sessions, sleep logs, and other health-related items.
The user will provide pre-calculated nutritional totals. Use these numbers for your analysis; do not recalculate them.
Focus your response on qualitative insights, timing, balance, and specific recommendations.
Do not request or expect images – only use the supplied analysis data.

You MUST return a JSON object matching this exact schema:
{schema}

Important rules:
- Always include every required property from the schema
- Provide empty arrays when there are no insights or recommendations
- Use null for unknown numeric values
- Ensure schemaVersion is ""1.0""
- Summaries should reference entryId values when describing insights
- If no entries are available, still return a valid JSON object with empty collections and explanations
";

        var messages = new List<ChatMessage>
        {
            new SystemChatMessage(systemPrompt)
        };

        var builder = new StringBuilder();
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

        messages.Add(new UserChatMessage(builder.ToString()));

        if (!string.IsNullOrWhiteSpace(existingSummaryJson))
        {
            messages.Add(new AssistantChatMessage(existingSummaryJson));
            messages.Add(new UserChatMessage("Regenerate the complete daily summary JSON considering any new data."));
        }

        return messages;
    }
}
