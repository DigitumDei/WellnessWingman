package com.wellnesswingman.domain.llm

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.audio.TranscriptionRequest
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.LoggingConfig
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Clock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * OpenAI implementation of LlmClient using openai-kotlin library.
 */
class OpenAiLlmClient(
    apiKey: String,
    private val model: String = "gpt-4o-mini"
) : LlmClient {

    private val client = OpenAI(
        token = apiKey,
        logging = LoggingConfig()
    )

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun analyzeImage(
        imageBytes: ByteArray,
        prompt: String,
        jsonSchema: String?
    ): LlmAnalysisResult {
        val startTime = Clock.System.now()

        // Encode image as base64
        val base64Image = Base64.encode(imageBytes)

        val request = ChatCompletionRequest(
            model = ModelId(model),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.User,
                    content = listOf(
                        TextPart(prompt),
                        ImagePart(url = "data:image/jpeg;base64,$base64Image")
                    )
                )
            )
        )

        val completion = client.chatCompletion(request)
        val endTime = Clock.System.now()

        val content = completion.choices.firstOrNull()?.message?.content ?: ""

        return LlmAnalysisResult(
            content = content,
            diagnostics = LlmDiagnostics(
                promptTokens = completion.usage?.promptTokens ?: 0,
                completionTokens = completion.usage?.completionTokens ?: 0,
                totalTokens = completion.usage?.totalTokens ?: 0,
                model = completion.model.id,
                latencyMs = (endTime - startTime).inWholeMilliseconds
            )
        )
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
                source = audioBytes
            ),
            model = ModelId("whisper-1")
        )

        val transcription = client.transcription(request)
        return transcription.text
    }

    override suspend fun generateCompletion(
        prompt: String,
        jsonSchema: String?
    ): LlmAnalysisResult {
        val startTime = Clock.System.now()

        val request = ChatCompletionRequest(
            model = ModelId(model),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.User,
                    content = prompt
                )
            )
        )

        val completion = client.chatCompletion(request)
        val endTime = Clock.System.now()

        val content = completion.choices.firstOrNull()?.message?.content ?: ""

        return LlmAnalysisResult(
            content = content,
            diagnostics = LlmDiagnostics(
                promptTokens = completion.usage?.promptTokens ?: 0,
                completionTokens = completion.usage?.completionTokens ?: 0,
                totalTokens = completion.usage?.totalTokens ?: 0,
                model = completion.model.id,
                latencyMs = (endTime - startTime).inWholeMilliseconds
            )
        )
    }
}
