using WellnessWingman.Models;

namespace WellnessWingman.Services.Llm;

/// <summary>
/// Mock LLM client for E2E testing. Returns predefined responses without calling external APIs.
/// </summary>
public class MockLlmClient : ILLmClient
{
    private const string MockMealAnalysisJson = """
        {
          "schemaVersion": "1.0",
          "entryType": "Meal",
          "confidence": 0.92,
          "mealAnalysis": {
            "schemaVersion": "1.0",
            "foodItems": [
              {
                "name": "grilled chicken breast",
                "portionSize": "150g",
                "calories": 248,
                "confidence": 0.90
              },
              {
                "name": "mixed salad",
                "portionSize": "1 cup",
                "calories": 45,
                "confidence": 0.85
              }
            ],
            "nutrition": {
              "totalCalories": 293,
              "protein": 35,
              "carbohydrates": 12,
              "fat": 10,
              "fiber": 4,
              "sugar": 3,
              "sodium": 380
            },
            "healthInsights": {
              "healthScore": 8.5,
              "summary": "Healthy meal with lean protein and vegetables.",
              "positives": [
                "High in protein",
                "Low in calories",
                "Contains fresh vegetables"
              ],
              "improvements": [
                "Consider adding whole grains for sustained energy"
              ],
              "recommendations": [
                "Add quinoa or brown rice as a side"
              ]
            },
            "confidence": 0.92,
            "warnings": []
          },
          "exerciseAnalysis": null,
          "sleepAnalysis": null,
          "otherAnalysis": null,
          "warnings": []
        }
        """;

    private const string MockDailySummaryJson = """
        {
          "schemaVersion": "1.0",
          "totals": {
            "calories": 1850,
            "protein": 95,
            "carbohydrates": 180,
            "fat": 65,
            "fiber": 28,
            "sugar": 45,
            "sodium": 2100
          },
          "balance": {
            "overall": "Good nutritional balance with adequate protein intake.",
            "macroBalance": "Macros are well-distributed with emphasis on protein.",
            "timing": "Meals were spaced appropriately throughout the day.",
            "variety": "Good variety of food groups represented."
          },
          "insights": [
            "Protein intake is on target for active individuals.",
            "Fiber intake meets daily recommendations.",
            "Consider reducing sodium intake slightly."
          ],
          "recommendations": [
            "Maintain current protein levels.",
            "Include more potassium-rich foods to balance sodium.",
            "Continue with vegetable-rich meals."
          ],
          "entriesIncluded": []
        }
        """;

    public Task<LlmAnalysisResult> InvokeAnalysisAsync(
        TrackedEntry entry,
        LlmRequestContext context,
        string? existingAnalysisJson = null,
        string? userProvidedDetails = null)
    {
        var analysis = new EntryAnalysis
        {
            EntryId = entry.EntryId,
            ProviderId = "Mock",
            Model = "mock-model",
            CapturedAt = DateTime.UtcNow,
            InsightsJson = MockMealAnalysisJson,
            SchemaVersion = "1.0"
        };

        var diagnostics = new LlmDiagnostics
        {
            PromptTokenCount = 100,
            CompletionTokenCount = 200,
            TotalTokenCount = 300
        };

        return Task.FromResult(new LlmAnalysisResult
        {
            Analysis = analysis,
            Diagnostics = diagnostics
        });
    }

    public Task<LlmAnalysisResult> InvokeDailySummaryAsync(
        DailySummaryRequest summaryRequest,
        LlmRequestContext context,
        string? existingSummaryJson = null)
    {
        var analysis = new EntryAnalysis
        {
            EntryId = summaryRequest.SummaryEntryId,
            ProviderId = "Mock",
            Model = "mock-model",
            CapturedAt = DateTime.UtcNow,
            InsightsJson = MockDailySummaryJson,
            SchemaVersion = "1.0"
        };

        var diagnostics = new LlmDiagnostics
        {
            PromptTokenCount = 150,
            CompletionTokenCount = 250,
            TotalTokenCount = 400
        };

        return Task.FromResult(new LlmAnalysisResult
        {
            Analysis = analysis,
            Diagnostics = diagnostics
        });
    }
}
