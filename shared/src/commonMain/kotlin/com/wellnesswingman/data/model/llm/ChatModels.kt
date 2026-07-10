package com.wellnesswingman.data.model.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class LlmChatRole {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("tool") TOOL
}

@Serializable
data class LlmChatMessage(
    val role: LlmChatRole,
    val content: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolResultJson: String? = null
)
