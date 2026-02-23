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

    public static string? ExtractFirstJsonObject(string text)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            return null;
        }

        var start = -1;
        var depth = 0;
        var inString = false;
        var escape = false;

        for (var i = 0; i < text.Length; i++)
        {
            var ch = text[i];

            if (start >= 0)
            {
                if (escape)
                {
                    escape = false;
                    continue;
                }

                if (ch == '\\')
                {
                    escape = true;
                    continue;
                }

                if (ch == '"')
                {
                    inString = !inString;
                    continue;
                }

                if (inString)
                {
                    continue;
                }

                if (ch == '{')
                {
                    depth++;
                }
                else if (ch == '}')
                {
                    depth--;
                    if (depth == 0)
                    {
                        return text.Substring(start, i - start + 1);
                    }
                }

                continue;
            }

            if (ch == '{')
            {
                start = i;
                depth = 1;
            }
        }

        return null;
    }
}
