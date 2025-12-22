using WellnessWingman.Models;

namespace WellnessWingman.Services.Llm;

public interface ILlmClientFactory
{
    ILLmClient GetClient(LlmProvider provider);
}
