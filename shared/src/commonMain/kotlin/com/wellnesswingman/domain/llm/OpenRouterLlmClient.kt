package com.wellnesswingman.domain.llm

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ToolCall as OpenAiToolCall
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.Parameters
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.chat.ToolChoice
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import com.aallam.openai.api.http.Timeout
import com.wellnesswingman.data.model.llm.ToolCall
import com.wellnesswingman.data.model.llm.ToolDefinition
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import io.github.aakira.napier.Napier
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds

/**
 * OpenRouter implementation of LlmClient using the openai-kotlin library for chat
 * and a custom Ktor HTTP call for audio transcription (OpenRouter's STT endpoint
 * requires a JSON body with base64 input_audio, which openai-kotlin's multipart
 * FileSource cannot emit).
 *
 * Chat endpoint: https://openrouter.ai/api/v1/chat/completions
 * STT endpoint: https://openrouter.ai/api/v1/audio/transcriptions
 */
class OpenRouterLlmClient(
    private val apiKey: String,
    private val model: String = "openai/gpt-4o-mini",
    private val client: OpenAI = OpenAI(
        token = apiKey,
        host = OpenAIHost(baseUrl = "https://openrouter.ai/api/v1/"),
        headers = mapOf(
            "HTTP-Referer" to "https://wellnesswingman.com",
            "X-Title" to "WellnessWingman"
        ),
        logging = LoggingConfig(),
        timeout = Timeout(socket = 60.seconds, connect = 60.seconds, request = 60.seconds)
    ),
    private val httpClient: HttpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }
) : LlmClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private companion object {
        const val MAX_TOOL_ROUNDS = 5
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun analyzeImage(
        imageBytes: ByteArray,
        prompt: String,
        jsonSchema: String?,
        tools: List<ToolDefinition>,
        toolExecutor: ToolExecutor?
    ): LlmAnalysisResult {
        val startTime = Clock.System.now()

        val base64Image = Base64.encode(imageBytes)

        Napier.d("OpenRouter analyzeImage called")
        Napier.d("Model: $model")
        Napier.d("Image bytes size: ${imageBytes.size}")
        Napier.d("Base64 image length: ${base64Image.length}")
        Napier.d("Prompt length: ${prompt.length}")

        val messages = mutableListOf(
            ChatMessage(
                role = ChatRole.User,
                content = listOf(
                    TextPart(prompt),
                    ImagePart(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        )
        return runConversation(messages, jsonSchema, tools, toolExecutor, startTime)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String): String {
        val format = when (mimeType) {
            "audio/m4a" -> "m4a"
            "audio/mp3" -> "mp3"
            "audio/wav" -> "wav"
            else -> "m4a"
        }

        val base64Audio = Base64.encode(audioBytes)

        val requestBody = buildJsonObject {
            put("model", JsonPrimitive("openai/whisper-1"))
            put("input_audio", buildJsonObject {
                put("data", JsonPrimitive(base64Audio))
                put("format", JsonPrimitive(format))
            })
        }

        Napier.d("OpenRouter STT request: model=openai/whisper-1, format=$format, audio size=${audioBytes.size} bytes")

        val httpResponse = httpClient.post("https://openrouter.ai/api/v1/audio/transcriptions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            header("HTTP-Referer", "https://wellnesswingman.com")
            header("X-Title", "WellnessWingman")
            setBody(requestBody.toString())
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.bodyAsText()
            Napier.e("OpenRouter transcription API error ${httpResponse.status}: $errorBody")
            throw Exception("OpenRouter transcription failed: ${httpResponse.status}: $errorBody")
        }

        val responseBody = httpResponse.bodyAsText()
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        return responseJson["text"]?.jsonPrimitive?.content
            ?: throw Exception("OpenRouter returned empty transcription")
    }

    override suspend fun generateCompletion(
        prompt: String,
        jsonSchema: String?,
        tools: List<ToolDefinition>,
        toolExecutor: ToolExecutor?
    ): LlmAnalysisResult {
        return runConversation(
            messages = mutableListOf(
                ChatMessage(
                    role = ChatRole.User,
                    content = prompt
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

    private suspend fun runConversation(
        messages: MutableList<ChatMessage>,
        jsonSchema: String?,
        tools: List<ToolDefinition>,
        toolExecutor: ToolExecutor?,
        startTime: kotlinx.datetime.Instant
    ): LlmAnalysisResult {
        var promptTokens = 0
        var completionTokens = 0
        var totalTokens = 0
        var resolvedModel = model

        repeat(MAX_TOOL_ROUNDS) { round ->
            Napier.d("Sending OpenRouter request, round ${round + 1}")

            val completion = client.chatCompletion(
                buildRequest(messages, jsonSchema, tools)
            )

            promptTokens += completion.usage?.promptTokens ?: 0
            completionTokens += completion.usage?.completionTokens ?: 0
            totalTokens += completion.usage?.totalTokens ?: 0
            resolvedModel = completion.model.id

            val message = completion.choices.firstOrNull()?.message
                ?: error("OpenRouter returned no completion choices")

            val toolCalls = message.toolCalls.orEmpty()
            if (toolCalls.isEmpty()) {
                val endTime = Clock.System.now()
                val content = sanitize(message.content.orEmpty())
                return LlmAnalysisResult(
                    content = content,
                    diagnostics = LlmDiagnostics(
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = totalTokens,
                        model = resolvedModel,
                        latencyMs = (endTime - startTime).inWholeMilliseconds
                    )
                )
            }

            val executor = toolExecutor
                ?: error("OpenRouter requested tool calls but no tool executor was provided")

            messages.add(message)

            toolCalls.forEach { toolCall ->
                require(toolCall is OpenAiToolCall.Function) {
                    "Unsupported OpenRouter tool call type: ${toolCall::class.simpleName}"
                }

                val result = runCatching {
                    val arguments = json.parseToJsonElement(toolCall.function.arguments).jsonObject
                    executor(
                        ToolCall(
                            id = toolCall.id.id,
                            name = toolCall.function.name,
                            arguments = arguments
                        )
                    )
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    com.wellnesswingman.data.model.llm.ToolResult(
                        toolCallId = toolCall.id.id,
                        name = toolCall.function.name,
                        content = JsonPrimitive(error.message ?: "Failed to parse tool arguments."),
                        isError = true
                    )
                }

                messages.add(
                    ChatMessage(
                        role = ChatRole.Tool,
                        toolCallId = toolCall.id,
                        name = toolCall.function.name,
                        content = serializeToolResult(result)
                    )
                )
            }
        }

        val completion = client.chatCompletion(
            buildRequest(messages, jsonSchema, tools)
        )

        promptTokens += completion.usage?.promptTokens ?: 0
        completionTokens += completion.usage?.completionTokens ?: 0
        totalTokens += completion.usage?.totalTokens ?: 0
        resolvedModel = completion.model.id

        val message = completion.choices.firstOrNull()?.message
            ?: error("OpenRouter returned no completion choices")

        if (message.toolCalls.orEmpty().isNotEmpty()) {
            error("OpenRouter tool loop exceeded $MAX_TOOL_ROUNDS rounds")
        }

        val endTime = Clock.System.now()
        val content = sanitize(message.content.orEmpty())
        return LlmAnalysisResult(
            content = content,
            diagnostics = LlmDiagnostics(
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                model = resolvedModel,
                latencyMs = (endTime - startTime).inWholeMilliseconds
            )
        )
    }

    private fun buildRequest(
        messages: List<ChatMessage>,
        jsonSchema: String?,
        tools: List<ToolDefinition>
    ): ChatCompletionRequest = chatCompletionRequest {
        model = ModelId(this@OpenRouterLlmClient.model)
        this.messages = messages
        responseFormat = if (jsonSchema != null) ChatResponseFormat.JsonObject else null
        if (tools.isNotEmpty()) {
            tools {
                tools.forEach { tool ->
                    function(
                        name = tool.name,
                        description = tool.description,
                        parameters = copyParameters(tool.parametersSchema)
                    )
                }
            }
            toolChoice = ToolChoice.Auto
        }
    }

    private fun copyParameters(schema: JsonObject): Parameters = Parameters.buildJsonObject {
        schema.forEach { (key, value) -> put(key, value) }
    }

    private fun serializeToolResult(toolResult: com.wellnesswingman.data.model.llm.ToolResult): String {
        val payload = buildJsonObject {
            put("ok", JsonPrimitive(!toolResult.isError))
            put("content", toolResult.content)
        }
        return json.encodeToString(JsonElement.serializer(), payload)
    }
}
