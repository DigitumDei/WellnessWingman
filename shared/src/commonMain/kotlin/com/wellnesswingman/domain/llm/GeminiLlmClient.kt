package com.wellnesswingman.domain.llm

import com.wellnesswingman.data.model.llm.ToolCall
import com.wellnesswingman.data.model.llm.ToolDefinition
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Google Gemini implementation of LlmClient using Ktor HTTP client.
 * Uses direct API calls since the official SDK is Android-only.
 */
class GeminiLlmClient(
    private val apiKey: String,
    private val model: String = "gemini-1.5-flash",
    private val httpClient: HttpClient = createDefaultHttpClient()
) : LlmClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MAX_TOOL_ROUNDS = 5

        fun createDefaultHttpClient(): HttpClient {
            return HttpClient {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 30_000
                    socketTimeoutMillis = 120_000
                }
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun analyzeImage(
        imageBytes: ByteArray,
        prompt: String,
        jsonSchema: String?,
        tools: List<ToolDefinition>,
        toolExecutor: ToolExecutor?
    ): LlmAnalysisResult {
        val base64Image = Base64.encode(imageBytes)
        return runConversation(
            contents = mutableListOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(
                            inlineData = GeminiPart.InlineData(
                                mimeType = "image/jpeg",
                                data = base64Image
                            )
                        )
                    )
                )
            ),
            jsonSchema = jsonSchema,
            tools = tools,
            toolExecutor = toolExecutor,
            startTime = Clock.System.now()
        )
    }

    private fun sanitize(content: String): String {
        return content.trim().removePrefix("```json").removeSuffix("```").trim()
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String): String {
        val resolvedMimeType = when (mimeType) {
            "audio/m4a" -> "audio/mp4"
            "audio/mp3" -> "audio/mpeg"
            else -> mimeType
        }

        val base64Audio = Base64.encode(audioBytes)

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = "Transcribe the audio. Respond with plain text only."),
                        GeminiPart(inlineData = GeminiPart.InlineData(
                            mimeType = resolvedMimeType,
                            data = base64Audio
                        ))
                    )
                )
            )
        )

        val httpResponse = httpClient.post(
            "$BASE_URL/models/$model:generateContent"
        ) {
            contentType(ContentType.Application.Json)
            header("x-goog-api-key", apiKey)
            setBody(request)
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.bodyAsText()
            Napier.e("Gemini transcription API error ${httpResponse.status}: $errorBody")
            throw Exception("Gemini transcription failed: ${httpResponse.status}")
        }

        val response: GeminiResponse = httpResponse.body()

        return response.candidates.firstOrNull()
            ?.content?.parts?.firstNotNullOfOrNull { it.text }
            ?.trim()
            ?: throw Exception("Gemini returned empty transcription")
    }

    override suspend fun generateCompletion(
        prompt: String,
        jsonSchema: String?,
        tools: List<ToolDefinition>,
        toolExecutor: ToolExecutor?
    ): LlmAnalysisResult {
        return runConversation(
            contents = mutableListOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            jsonSchema = jsonSchema,
            tools = tools,
            toolExecutor = toolExecutor,
            startTime = Clock.System.now()
        )
    }

    private suspend fun runConversation(
        contents: MutableList<GeminiContent>,
        jsonSchema: String?,
        tools: List<ToolDefinition>,
        toolExecutor: ToolExecutor?,
        startTime: kotlinx.datetime.Instant
    ): LlmAnalysisResult {
        var promptTokens = 0
        var completionTokens = 0
        var totalTokens = 0

        repeat(MAX_TOOL_ROUNDS) { round ->
            val response = executeRequest(
                GeminiRequest(
                    contents = contents,
                    tools = tools.takeIf { it.isNotEmpty() }?.let(::geminiTools),
                    toolConfig = toolConfig(tools),
                    systemInstruction = systemInstruction(tools),
                    generationConfig = GenerationConfig(
                        responseMimeType = if (jsonSchema != null) "application/json" else null
                    )
                )
            )

            promptTokens += response.usageMetadata?.promptTokenCount ?: 0
            completionTokens += response.usageMetadata?.candidatesTokenCount ?: 0
            totalTokens += response.usageMetadata?.totalTokenCount ?: 0

            val candidate = response.candidates.firstOrNull()
                ?: error("Gemini returned no completion candidates")
            val content = candidate.content
            val functionCalls = content.parts.mapNotNull { it.functionCall }

            if (functionCalls.isEmpty()) {
                val endTime = Clock.System.now()
                val text = sanitize(
                    content.parts.mapNotNull { it.text }
                        .joinToString("\n")
                )
                return LlmAnalysisResult(
                    content = text,
                    diagnostics = LlmDiagnostics(
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = totalTokens,
                        model = model,
                        latencyMs = (endTime - startTime).inWholeMilliseconds
                    )
                )
            }

            val executor = toolExecutor
                ?: error("Gemini requested tool calls but no tool executor was provided")

            Napier.d("Sending Gemini tool response, round ${round + 1}")
            contents.add(content.copy(role = "model"))
            contents.add(
                GeminiContent(
                    role = "user",
                    parts = functionCalls.map { functionCall ->
                        val result = runCatching {
                            val arguments = functionCall.args.asJsonObjectOrNull()
                                ?: throw IllegalArgumentException("Tool arguments must be a JSON object.")
                            executor(
                                ToolCall(
                                    id = functionCall.id,
                                    name = functionCall.name,
                                    arguments = arguments
                                )
                            )
                        }.getOrElse { error ->
                            if (error is CancellationException) throw error
                            com.wellnesswingman.data.model.llm.ToolResult(
                                toolCallId = functionCall.id,
                                name = functionCall.name,
                                content = JsonPrimitive(error.message ?: "Tool execution failed."),
                                isError = true
                            )
                        }
                        GeminiPart(
                            functionResponse = GeminiFunctionResponse(
                                id = functionCall.id,
                                name = functionCall.name,
                                response = buildJsonObject {
                                    put("ok", JsonPrimitive(!result.isError))
                                    put("content", result.content)
                                }
                            )
                        )
                    }
                )
            )
        }

        val response = executeRequest(
            GeminiRequest(
                contents = contents,
                tools = tools.takeIf { it.isNotEmpty() }?.let(::geminiTools),
                toolConfig = toolConfig(tools),
                systemInstruction = systemInstruction(tools),
                generationConfig = GenerationConfig(
                    responseMimeType = if (jsonSchema != null) "application/json" else null
                )
            )
        )

        promptTokens += response.usageMetadata?.promptTokenCount ?: 0
        completionTokens += response.usageMetadata?.candidatesTokenCount ?: 0
        totalTokens += response.usageMetadata?.totalTokenCount ?: 0

        val candidate = response.candidates.firstOrNull()
            ?: error("Gemini returned no completion candidates")
        val content = candidate.content
        if (content.parts.any { it.functionCall != null }) {
            error("Gemini tool loop exceeded $MAX_TOOL_ROUNDS rounds")
        }

        val endTime = Clock.System.now()
        val text = sanitize(
            content.parts.mapNotNull { it.text }
                .joinToString("\n")
        )
        return LlmAnalysisResult(
            content = text,
            diagnostics = LlmDiagnostics(
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                model = model,
                latencyMs = (endTime - startTime).inWholeMilliseconds
            )
        )
    }

    private suspend fun executeRequest(request: GeminiRequest): GeminiResponse {
        val httpResponse = httpClient.post(
            "$BASE_URL/models/$model:generateContent"
        ) {
            contentType(ContentType.Application.Json)
            header("x-goog-api-key", apiKey)
            setBody(request)
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.bodyAsText()
            Napier.e("Gemini API error ${httpResponse.status}: $errorBody")
            throw Exception("Gemini API error ${httpResponse.status}: $errorBody")
        }

        return json.decodeFromString(httpResponse.bodyAsText())
    }

    private fun geminiTools(tools: List<ToolDefinition>): List<GeminiTool> = listOf(
        GeminiTool(
            functionDeclarations = tools.map { tool ->
                GeminiFunctionDeclaration(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parametersSchema
                )
            }
        )
    )

    private fun toolConfig(tools: List<ToolDefinition>): GeminiToolConfig? =
        tools.takeIf { it.isNotEmpty() }?.let { GeminiToolConfig(FunctionCallingConfig("AUTO")) }

    private fun systemInstruction(tools: List<ToolDefinition>): GeminiContent? =
        tools.takeIf { it.isNotEmpty() }?.let {
            GeminiContent(
                parts = listOf(
                    GeminiPart(
                        text = "You have tool access for user profile, weight history, recent tracked entries, and saved nutritional profiles. Call relevant tools proactively before generating your analysis, and use the nutritional profile list-then-get flow when packaged foods may match saved profiles."
                    )
                )
            )
        }
}

// Gemini API request/response models

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val tools: List<GeminiTool>? = null,
    @SerialName("tool_config")
    val toolConfig: GeminiToolConfig? = null,
    @SerialName("system_instruction")
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class GeminiToolConfig(
    @SerialName("function_calling_config")
    val functionCallingConfig: FunctionCallingConfig
)

@Serializable
data class FunctionCallingConfig(
    val mode: String
)

@Serializable
data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String = "user"
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: InlineData? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null,
    @SerialName("thoughtSignature")
    val thoughtSignature: String? = null
) {
    @Serializable
    data class InlineData(
        val mimeType: String,
        val data: String
    )
}

@Serializable
data class GeminiFunctionCall(
    val id: String? = null,
    val name: String,
    val args: JsonElement = JsonObject(emptyMap())
)

@Serializable
data class GeminiFunctionResponse(
    val id: String? = null,
    val name: String,
    val response: JsonObject
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val usageMetadata: UsageMetadata? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent,
    val finishReason: String? = null
)

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

@Serializable
data class UsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0
)
