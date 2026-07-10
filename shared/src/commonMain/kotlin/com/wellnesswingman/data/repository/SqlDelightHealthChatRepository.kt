package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.ChatMessageStatus
import com.wellnesswingman.data.model.ChatRole
import com.wellnesswingman.data.model.HealthChatConversation
import com.wellnesswingman.data.model.HealthChatMessage
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

class SqlDelightHealthChatRepository(
    private val database: WellnessWingmanDatabase,
) : HealthChatRepository {

    private val queries = database.chatConversationQueries

    override suspend fun createConversation(
        externalId: String,
        title: String,
        provider: String?,
        model: String?,
        createdAt: Instant,
        updatedAt: Instant,
    ): Long = withContext(Dispatchers.IO) {
        queries.insertConversation(
            externalId = externalId,
            title = title,
            provider = provider,
            model = model,
            createdAt = createdAt.toEpochMilliseconds(),
            updatedAt = updatedAt.toEpochMilliseconds(),
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun getAllConversations(): List<HealthChatConversation> =
        withContext(Dispatchers.IO) {
            queries.getAllConversations().executeAsList().map { conversation ->
                val lastMessage = queries.getLatestMessageForConversation(conversation.conversationId)
                    .executeAsOneOrNull()
                conversation.toHealthChatConversation(lastMessage)
            }
        }

    override suspend fun getConversationById(id: Long): HealthChatConversation? =
        withContext(Dispatchers.IO) {
            queries.getConversationById(id).executeAsOneOrNull()?.toHealthChatConversation()
        }

    override suspend fun getConversationByExternalId(externalId: String): HealthChatConversation? =
        withContext(Dispatchers.IO) {
            queries.getConversationByExternalId(externalId)
                .executeAsOneOrNull()?.toHealthChatConversation()
        }

    override suspend fun renameConversation(id: Long, title: String, updatedAt: Instant) =
        withContext(Dispatchers.IO) {
            queries.updateConversationTitle(
                title = title,
                updatedAt = updatedAt.toEpochMilliseconds(),
                conversationId = id,
            )
        }

    override suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        queries.deleteConversation(id)
    }

    override suspend fun touchConversation(id: Long, updatedAt: Instant) =
        withContext(Dispatchers.IO) {
            queries.touchConversation(
                updatedAt = updatedAt.toEpochMilliseconds(),
                conversationId = id,
            )
        }

    override suspend fun getMessagesForConversation(conversationId: Long): List<HealthChatMessage> =
        withContext(Dispatchers.IO) {
            queries.getMessagesForConversation(conversationId).executeAsList()
                .map { it.toHealthChatMessage() }
        }

    override suspend fun insertMessage(
        conversationId: Long,
        role: ChatRole,
        content: String,
        createdAt: Instant,
        provider: String?,
        model: String?,
        toolCallsJson: String?,
        toolResultJson: String?,
        status: ChatMessageStatus,
    ): Long = withContext(Dispatchers.IO) {
        queries.insertMessage(
            conversationId = conversationId,
            role = ChatRole.toStorageString(role),
            content = content,
            createdAt = createdAt.toEpochMilliseconds(),
            provider = provider,
            model = model,
            toolCallsJson = toolCallsJson,
            toolResultJson = toolResultJson,
            status = ChatMessageStatus.toString(status),
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun updateMessageStatus(messageId: Long, status: ChatMessageStatus) =
        withContext(Dispatchers.IO) {
            queries.updateMessageStatus(
                status = ChatMessageStatus.toString(status),
                messageId = messageId,
            )
        }

    override suspend fun updateAssistantMessage(
        messageId: Long,
        content: String,
        toolCallsJson: String?,
        model: String?,
        status: ChatMessageStatus,
    ) = withContext(Dispatchers.IO) {
        queries.updateMessageContent(
            content = content,
            toolCallsJson = toolCallsJson,
            model = model,
            status = ChatMessageStatus.toString(status),
            messageId = messageId,
        )
    }

    override suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        queries.deleteMessage(messageId)
    }

    override suspend fun getConversationCount(): Long = withContext(Dispatchers.IO) {
        queries.getConversationCount().executeAsOne()
    }

    override suspend fun getMessageCountForConversation(conversationId: Long): Long =
        withContext(Dispatchers.IO) {
            queries.getMessageCountForConversation(conversationId).executeAsOne()
        }

    private fun com.wellnesswingman.db.ChatConversation.toHealthChatConversation(
        lastMessage: com.wellnesswingman.db.ChatMessage? = null,
    ): HealthChatConversation {
        return HealthChatConversation(
            conversationId = conversationId,
            externalId = externalId,
            title = title,
            provider = provider,
            model = model,
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            updatedAt = Instant.fromEpochMilliseconds(updatedAt),
            lastMessageContent = lastMessage?.content,
            lastMessageRole = lastMessage?.let { ChatRole.fromStorageString(it.role) },
            lastMessageCreatedAt = lastMessage?.createdAt?.let { Instant.fromEpochMilliseconds(it) },
        )
    }

    private fun com.wellnesswingman.db.ChatMessage.toHealthChatMessage(): HealthChatMessage {
        return HealthChatMessage(
            messageId = messageId,
            conversationId = conversationId,
            role = ChatRole.fromStorageString(role),
            content = content,
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            provider = provider,
            model = model,
            toolCallsJson = toolCallsJson,
            toolResultJson = toolResultJson,
            status = ChatMessageStatus.fromString(status),
        )
    }
}
