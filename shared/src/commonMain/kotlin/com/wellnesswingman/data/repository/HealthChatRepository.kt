package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.ChatMessageStatus
import com.wellnesswingman.data.model.ChatRole
import com.wellnesswingman.data.model.HealthChatConversation
import com.wellnesswingman.data.model.HealthChatMessage
import kotlinx.datetime.Instant

interface HealthChatRepository {
    suspend fun createConversation(
        externalId: String,
        title: String = "",
        provider: String? = null,
        model: String? = null,
        createdAt: Instant,
        updatedAt: Instant,
    ): Long

    suspend fun getAllConversations(): List<HealthChatConversation>

    suspend fun getConversationById(id: Long): HealthChatConversation?

    suspend fun getConversationByExternalId(externalId: String): HealthChatConversation?

    suspend fun renameConversation(id: Long, title: String, updatedAt: Instant)

    suspend fun deleteConversation(id: Long)

    suspend fun touchConversation(id: Long, updatedAt: Instant)

    suspend fun getMessagesForConversation(conversationId: Long): List<HealthChatMessage>

    suspend fun insertMessage(
        conversationId: Long,
        role: ChatRole,
        content: String,
        createdAt: Instant,
        provider: String? = null,
        model: String? = null,
        toolCallsJson: String? = null,
        toolResultJson: String? = null,
    ): Long

    suspend fun deleteMessage(messageId: Long)

    suspend fun getConversationCount(): Long

    suspend fun getMessageCountForConversation(conversationId: Long): Long
}
