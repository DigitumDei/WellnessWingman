package com.wellnesswingman.data.model

import kotlinx.datetime.Instant

enum class ChatRole {
    USER,
    ASSISTANT,
    TOOL;

    companion object {
        fun fromStorageString(value: String): ChatRole = when (value) {
            "user" -> USER
            "assistant" -> ASSISTANT
            "tool" -> TOOL
            else -> throw IllegalArgumentException("Unknown ChatRole: $value")
        }

        fun toStorageString(role: ChatRole): String = role.name.lowercase()
    }
}

enum class ChatMessageStatus {
    PENDING,
    COMPLETED,
    ERROR;

    companion object {
        fun fromString(value: String): ChatMessageStatus = when (value) {
            "pending" -> PENDING
            "completed" -> COMPLETED
            "error" -> ERROR
            else -> ERROR
        }

        fun toString(status: ChatMessageStatus): String = status.name.lowercase()
    }
}

data class HealthChatConversation(
    val conversationId: Long = 0,
    val externalId: String,
    val title: String = "",
    val provider: String? = null,
    val model: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastMessageContent: String? = null,
    val lastMessageRole: ChatRole? = null,
    val lastMessageCreatedAt: Instant? = null,
)

data class HealthChatMessage(
    val messageId: Long = 0,
    val conversationId: Long,
    val role: ChatRole,
    val content: String,
    val createdAt: Instant,
    val provider: String? = null,
    val model: String? = null,
    val toolCallsJson: String? = null,
    val toolResultJson: String? = null,
    val status: ChatMessageStatus = ChatMessageStatus.COMPLETED,
)
