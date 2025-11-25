using System;
using System.Collections.Generic;
using System.Linq;
using WellnessWingman.Models;

namespace WellnessWingman.Services.Analysis;

public class DailyTotalsCalculator
{
    public NutritionTotals Calculate(IEnumerable<UnifiedAnalysisResult?> analyses)
    {
        var totals = new NutritionTotals
        {
            Calories = 0,
            Protein = 0,
            Carbohydrates = 0,
            Fat = 0,
            Fiber = 0,
            Sugar = 0,
            Sodium = 0
        };

        foreach (var analysis in analyses)
        {
            if (analysis?.MealAnalysis?.Nutrition == null) continue;

            var nutrition = analysis.MealAnalysis.Nutrition;

            totals.Calories += nutrition.TotalCalories ?? 0;
            totals.Protein += nutrition.Protein ?? 0;
            totals.Carbohydrates += nutrition.Carbohydrates ?? 0;
            totals.Fat += nutrition.Fat ?? 0;
            totals.Fiber += nutrition.Fiber ?? 0;
            totals.Sugar += nutrition.Sugar ?? 0;
            totals.Sodium += nutrition.Sodium ?? 0;
        }

        return totals;
    }
}
