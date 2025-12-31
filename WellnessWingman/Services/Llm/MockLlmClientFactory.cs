using WellnessWingman.Models;

namespace WellnessWingman.Services.Llm;

/// <summary>
/// Mock LLM client factory for E2E testing. Always returns the mock client regardless of provider.
/// </summary>
public class MockLlmClientFactory : ILlmClientFactory
{
    private readonly MockLlmClient _mockClient;

    public MockLlmClientFactory(MockLlmClient mockClient)
    {
        _mockClient = mockClient;
    }

    public ILLmClient GetClient(LlmProvider provider)
    {
        // Always return mock client regardless of provider
        return _mockClient;
    }
}
