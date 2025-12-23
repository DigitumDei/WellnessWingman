using System.Text;
using Google.GenAI.Types;

namespace WellnessWingman.Utilities;

/// <summary>
/// Utility class for parsing Gemini API responses.
/// </summary>
public static class GeminiResponseParser
{
    /// <summary>
    /// Extracts concatenated text from a Gemini GenerateContentResponse.
    /// </summary>
    public static string ExtractText(GenerateContentResponse response)
    {
        if (response.Candidates is null || response.Candidates.Count == 0)
        {
            return string.Empty;
        }

        var builder = new StringBuilder();
        foreach (var candidate in response.Candidates)
        {
            if (candidate.Content?.Parts is null)
            {
                continue;
            }

            foreach (var part in candidate.Content.Parts)
            {
                if (!string.IsNullOrWhiteSpace(part.Text))
                {
                    if (builder.Length > 0)
                    {
                        builder.AppendLine();
                    }
                    builder.Append(part.Text);
                }
            }
        }

        return builder.ToString();
    }
}
