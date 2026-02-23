using WellnessWingman.Models;

namespace WellnessWingman.Services.Llm;

public class LlmClientFactory : ILlmClientFactory
{
    private readonly OpenAiLlmClient _openAiClient;
    private readonly GeminiLlmClient _geminiClient;

    public LlmClientFactory(OpenAiLlmClient openAiClient, GeminiLlmClient geminiClient)
    {
        _openAiClient = openAiClient;
        _geminiClient = geminiClient;
    }

    public ILLmClient GetClient(LlmProvider provider)
    {
        return provider switch
        {
            LlmProvider.OpenAI => _openAiClient,
            LlmProvider.Gemini => _geminiClient,
            _ => throw new NotSupportedException($"LLM provider {provider} is not supported.")
        };
    }
}
