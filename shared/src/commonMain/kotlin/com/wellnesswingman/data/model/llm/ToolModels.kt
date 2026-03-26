package com.wellnesswingman.data.model.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: JsonObject
)

@Serializable
data class ToolCall(
    val id: String? = null,
    val name: String,
    val arguments: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class ToolResult(
    val toolCallId: String? = null,
    val name: String,
    val content: JsonElement,
    val isError: Boolean = false
)
