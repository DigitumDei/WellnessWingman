package com.wellnesswingman.domain.llm

/**
 * Result of an LLM analysis operation.
 */
data class LlmAnalysisResult(
    val content: String,
    val diagnostics: LlmDiagnostics
)

/**
 * Diagnostic information from LLM API calls.
 */
data class LlmDiagnostics(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val model: String = "",
    val latencyMs: Long = 0
)

/**
 * Interface for LLM client implementations.
 */
interface LlmClient {
    /**
     * Analyzes an image with a text prompt.
     */
    suspend fun analyzeImage(
        imageBytes: ByteArray,
        prompt: String,
        jsonSchema: String? = null
    ): LlmAnalysisResult

    /**
     * Transcribes audio to text.
     */
    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/m4a"
    ): String

    /**
     * Generates a text completion from a prompt.
     */
    suspend fun generateCompletion(
        prompt: String,
        jsonSchema: String? = null
    ): LlmAnalysisResult
}
