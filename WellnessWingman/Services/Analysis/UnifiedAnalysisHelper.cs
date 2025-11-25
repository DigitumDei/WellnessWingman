using System.Collections.Generic;
using System.Text.Json;
using System.Threading.Tasks;
using WellnessWingman.Data;
using WellnessWingman.Models;
using Microsoft.Extensions.Logging;
using System.Linq; // For .ToList()

namespace WellnessWingman.Services.Analysis;

public class UnifiedAnalysisHelper
{
    private readonly IEntryAnalysisRepository _entryAnalysisRepository;
    private readonly ILogger<UnifiedAnalysisHelper> _logger;

    public UnifiedAnalysisHelper(IEntryAnalysisRepository entryAnalysisRepository, ILogger<UnifiedAnalysisHelper> logger)
    {
        _entryAnalysisRepository = entryAnalysisRepository;
        _logger = logger;
    }

    /// <summary>
    /// Fetches and deserializes UnifiedAnalysisResult for a given set of tracked entries.
    /// Only processes entries that are marked as completed meals.
    /// </summary>
    /// <param name="entries">The collection of tracked entries.</param>
    /// <returns>A list of deserialized UnifiedAnalysisResult objects. Null entries are included if deserialization fails or analysis is missing.</returns>
    public async Task<List<UnifiedAnalysisResult?>> GetUnifiedAnalysisResultsForCompletedMealsAsync(IEnumerable<TrackedEntry> entries)
    {
        var unifiedAnalyses = new List<UnifiedAnalysisResult?>();

        var completedMeals = entries
            .Where(e => e.EntryType == EntryType.Meal && e.ProcessingStatus == ProcessingStatus.Completed)
            .ToList();

        foreach (var meal in completedMeals)
        {
            var mealAnalysis = await _entryAnalysisRepository.GetByTrackedEntryIdAsync(meal.EntryId);
            if (mealAnalysis != null && !string.IsNullOrEmpty(mealAnalysis.InsightsJson))
            {
                try
                {
                    var analysis = JsonSerializer.Deserialize<UnifiedAnalysisResult>(mealAnalysis.InsightsJson);
                    unifiedAnalyses.Add(analysis);
                }
                catch (JsonException ex)
                {
                    _logger.LogWarning(ex, "Failed to deserialize analysis for meal entry {EntryId}.", meal.EntryId);
                }
            }
            else
            {
                // Optionally add null or log that analysis was missing for a completed meal.
                unifiedAnalyses.Add(null);
            }
        }
        return unifiedAnalyses;
    }

    /// <summary>
    /// Fetches and deserializes UnifiedAnalysisResult for a given set of tracked entry cards.
    /// Only processes entries that are marked as completed meals.
    /// </summary>
    /// <param name="entryCards">The collection of tracked entry cards.</param>
    /// <returns>A list of deserialized UnifiedAnalysisResult objects. Null entries are included if deserialization fails or analysis is missing.</returns>
    public async Task<List<UnifiedAnalysisResult?>> GetUnifiedAnalysisResultsForCompletedMealCardsAsync(IEnumerable<TrackedEntryCard> entryCards)
    {
        var unifiedAnalyses = new List<UnifiedAnalysisResult?>();

        var completedMealCards = entryCards
            .Where(e => e.EntryType == EntryType.Meal && e.ProcessingStatus == ProcessingStatus.Completed)
            .ToList();

        foreach (var mealCard in completedMealCards)
        {
            var mealAnalysis = await _entryAnalysisRepository.GetByTrackedEntryIdAsync(mealCard.EntryId);
            if (mealAnalysis != null && !string.IsNullOrEmpty(mealAnalysis.InsightsJson))
            {
                try
                {
                    var analysis = JsonSerializer.Deserialize<UnifiedAnalysisResult>(mealAnalysis.InsightsJson);
                    unifiedAnalyses.Add(analysis);
                }
                catch (JsonException ex)
                {
                    _logger.LogWarning(ex, "Failed to deserialize analysis for meal entry card {EntryId}.", mealCard.EntryId);
                }
            }
            else
            {
                // Optionally add null or log that analysis was missing for a completed meal.
                unifiedAnalyses.Add(null);
            }
        }
        return unifiedAnalyses;
    }
}
