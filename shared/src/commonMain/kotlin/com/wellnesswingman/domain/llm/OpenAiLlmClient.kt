package com.wellnesswingman.domain.llm

import com.aallam.openai.api.audio.TranscriptionRequest
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ToolCall as OpenAiToolCall
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.Parameters
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.chat.ToolChoice
import com.aallam.openai.api.chat.argumentsAsJson
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.api.http.Timeout
import com.wellnesswingman.data.model.llm.ToolCall
import com.wellnesswingman.data.model.llm.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.datetime.Clock
import io.github.aakira.napier.Napier
import okio.Buffer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds

/**
 * OpenAI implementation of LlmClient using openai-kotlin library.
 */
class OpenAiLlmClient(
    apiKey: String,
    private val model: String = "gpt-4o-mini"
) : LlmClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OpenAI(
        token = apiKey,
        logging = LoggingConfig(),
        timeout = Timeout(socket = 60.seconds, connect = 60.seconds, request = 60.seconds)
    )

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

        // Encode image as base64
        val base64Image = Base64.encode(imageBytes)

        Napier.d("OpenAI analyzeImage called")
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

    override suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String): String {
        val extension = when (mimeType) {
            "audio/m4a" -> "m4a"
            "audio/mp3" -> "mp3"
            "audio/wav" -> "wav"
            else -> "m4a"
        }

        val request = TranscriptionRequest(
            audio = FileSource(
                name = "audio.$extension",
                source = Buffer().write(audioBytes)
            ),
            model = ModelId("whisper-1")
        )

        val transcription = client.transcription(request)
        return transcription.text
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

        repeat(MAX_TOOL_ROUNDS + 1) { round ->
            Napier.d("Sending OpenAI request, round ${round + 1}")

            val completion = client.chatCompletion(
                buildRequest(messages, jsonSchema, tools)
            )

            promptTokens += completion.usage?.promptTokens ?: 0
            completionTokens += completion.usage?.completionTokens ?: 0
            totalTokens += completion.usage?.totalTokens ?: 0
            resolvedModel = completion.model.id

            val message = completion.choices.firstOrNull()?.message
                ?: error("OpenAI returned no completion choices")

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
                ?: error("OpenAI requested tool calls but no tool executor was provided")

            messages.add(
                ChatMessage(
                    role = message.role,
                    content = message.content.orEmpty(),
                    toolCalls = message.toolCalls,
                    toolCallId = message.toolCallId
                )
            )

            toolCalls.forEach { toolCall ->
                require(toolCall is OpenAiToolCall.Function) {
                    "Unsupported OpenAI tool call type: ${toolCall::class.simpleName}"
                }

                val result = executor(
                    ToolCall(
                        id = toolCall.id,
                        name = toolCall.function.name,
                        arguments = toolCall.function.argumentsAsJson()
                    )
                )

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

        error("OpenAI tool loop exceeded $MAX_TOOL_ROUNDS rounds")
    }

    private fun buildRequest(
        messages: List<ChatMessage>,
        jsonSchema: String?,
        tools: List<ToolDefinition>
    ): ChatCompletionRequest = chatCompletionRequest {
        model = ModelId(this@OpenAiLlmClient.model)
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
