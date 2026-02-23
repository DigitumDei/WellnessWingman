using System.Collections.Generic;
using WellnessWingman.Models;
using WellnessWingman.Services.Analysis;
using Xunit;

namespace WellnessWingman.Tests.Services.Analysis;

public class DailyTotalsCalculatorTests
{
    private readonly DailyTotalsCalculator _calculator;

    public DailyTotalsCalculatorTests()
    {
        _calculator = new DailyTotalsCalculator();
    }

    [Fact]
    public void Calculate_ReturnsZeros_WhenListIsEmpty()
    {
        var result = _calculator.Calculate(new List<UnifiedAnalysisResult>());

        Assert.Equal(0, result.Calories);
        Assert.Equal(0, result.Protein);
        Assert.Equal(0, result.Carbohydrates);
        Assert.Equal(0, result.Fat);
    }

    [Fact]
    public void Calculate_SumsValuesCorrectly()
    {
        var analyses = new List<UnifiedAnalysisResult>
        {
            new()
            {
                MealAnalysis = new MealAnalysisResult
                {
                    Nutrition = new NutritionEstimate
                    {
                        TotalCalories = 500,
                        Protein = 30,
                        Carbohydrates = 50,
                        Fat = 20
                    }
                }
            },
            new()
            {
                MealAnalysis = new MealAnalysisResult
                {
                    Nutrition = new NutritionEstimate
                    {
                        TotalCalories = 300,
                        Protein = 20,
                        Carbohydrates = 30,
                        Fat = 10
                    }
                }
            }
        };

        var result = _calculator.Calculate(analyses);

        Assert.Equal(800, result.Calories);
        Assert.Equal(50, result.Protein);
        Assert.Equal(80, result.Carbohydrates);
        Assert.Equal(30, result.Fat);
    }

    [Fact]
    public void Calculate_IgnoresNullNutrition()
    {
        var analyses = new List<UnifiedAnalysisResult>
        {
            new()
            {
                MealAnalysis = new MealAnalysisResult
                {
                    Nutrition = new NutritionEstimate
                    {
                        TotalCalories = 100,
                        Protein = 10
                    }
                }
            },
            new()
            {
                MealAnalysis = null // Should be ignored
            }
        };

        var result = _calculator.Calculate(analyses);

        Assert.Equal(100, result.Calories);
        Assert.Equal(10, result.Protein);
    }
}
