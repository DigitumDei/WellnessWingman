package com.wellnesswingman.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.ChatMessageStatus
import com.wellnesswingman.data.model.ChatRole
import com.wellnesswingman.data.model.HealthChatConversation
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*
import kotlin.time.Duration.Companion.hours

class SqlDelightHealthChatRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: WellnessWingmanDatabase
    private lateinit var repository: SqlDelightHealthChatRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        database = WellnessWingmanDatabase(driver)
        repository = SqlDelightHealthChatRepository(database)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun `createConversation inserts and returns id`() = runTest {
        val now = Clock.System.now()
        val id = repository.createConversation(
            externalId = "conv-1",
            title = "Health Chat",
            createdAt = now,
            updatedAt = now
        )
        assertTrue(id > 0)
    }

    @Test
    fun `getConversationById returns correct conversation`() = runTest {
        val now = Clock.System.now()
        val id = repository.createConversation(
            externalId = "conv-1",
            title = "Test Chat",
            createdAt = now,
            updatedAt = now
        )
        val retrieved = repository.getConversationById(id)

        assertNotNull(retrieved)
        assertEquals(id, retrieved.conversationId)
        assertEquals("conv-1", retrieved.externalId)
        assertEquals("Test Chat", retrieved.title)
    }

    @Test
    fun `getConversationById returns null for non-existent id`() = runTest {
        val retrieved = repository.getConversationById(999)
        assertNull(retrieved)
    }

    @Test
    fun `getConversationByExternalId returns conversation`() = runTest {
        val now = Clock.System.now()
        repository.createConversation(
            externalId = "ext-1",
            title = "By External",
            createdAt = now,
            updatedAt = now
        )
        val retrieved = repository.getConversationByExternalId("ext-1")
        assertNotNull(retrieved)
        assertEquals("ext-1", retrieved.externalId)
    }

    @Test
    fun `getConversationByExternalId returns null when not found`() = runTest {
        assertNull(repository.getConversationByExternalId("missing"))
    }

    @Test
    fun `renameConversation updates title and timestamp`() = runTest {
        val now = Clock.System.now()
        val id = repository.createConversation(
            externalId = "rename-test",
            title = "Original",
            createdAt = now,
            updatedAt = now
        )
        val later = now.plus(1.hours)
        repository.renameConversation(id, "Renamed", later)

        val retrieved = repository.getConversationById(id)
        assertNotNull(retrieved)
        assertEquals("Renamed", retrieved.title)
        assertEquals(later.toEpochMilliseconds(), retrieved.updatedAt.toEpochMilliseconds())
    }

    @Test
    fun `touchConversation updates timestamp`() = runTest {
        val now = Clock.System.now()
        val id = repository.createConversation(
            externalId = "touch-test",
            title = "Touch",
            createdAt = now,
            updatedAt = now
        )
        val later = now.plus(2.hours)
        repository.touchConversation(id, later)

        val retrieved = repository.getConversationById(id)
        assertNotNull(retrieved)
        assertEquals(later.toEpochMilliseconds(), retrieved.updatedAt.toEpochMilliseconds())
    }

    @Test
    fun `deleteConversation removes conversation`() = runTest {
        val now = Clock.System.now()
        val id = repository.createConversation(
            externalId = "delete-test",
            title = "Delete Me",
            createdAt = now,
            updatedAt = now
        )
        repository.deleteConversation(id)

        assertNull(repository.getConversationById(id))
    }

    @Test
    fun `deleteConversation cascades to messages`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "cascade-test",
            title = "Cascade",
            createdAt = now,
            updatedAt = now
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "Hello",
            createdAt = now.plus(1.hours)
        )
        assertEquals(1, repository.getMessageCountForConversation(convId))

        repository.deleteConversation(convId)
        assertEquals(0, repository.getMessageCountForConversation(convId))
    }

    @Test
    fun `getAllConversations returns conversations sorted by updatedAt desc`() = runTest {
        val now = Clock.System.now()
        val id1 = repository.createConversation(
            externalId = "conv-a",
            title = "A",
            createdAt = now,
            updatedAt = now
        )
        val id2 = repository.createConversation(
            externalId = "conv-b",
            title = "B",
            createdAt = now,
            updatedAt = now.plus(1.hours)
        )
        val id3 = repository.createConversation(
            externalId = "conv-c",
            title = "C",
            createdAt = now,
            updatedAt = now.plus(2.hours)
        )

        val all = repository.getAllConversations()
        assertEquals(3, all.size)
        // Most recently updated first
        assertEquals(id3, all[0].conversationId)
        assertEquals(id2, all[1].conversationId)
        assertEquals(id1, all[2].conversationId)
    }

    @Test
    fun `getAllConversations includes last-message metadata`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "last-msg-test",
            title = "Last Msg",
            createdAt = now,
            updatedAt = now
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "What is my blood pressure?",
            createdAt = now.plus(1.hours)
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.ASSISTANT,
            content = "Your blood pressure is 120/80",
            createdAt = now.plus(2.hours)
        )
        // Touch so updatedAt reflects latest message
        repository.touchConversation(convId, now.plus(2.hours))

        val all = repository.getAllConversations()
        assertEquals(1, all.size)
        val conv = all[0]
        assertNotNull(conv.lastMessageContent)
        assertNotNull(conv.lastMessageRole)
        assertNotNull(conv.lastMessageCreatedAt)
        assertEquals("Your blood pressure is 120/80", conv.lastMessageContent)
        assertEquals(ChatRole.ASSISTANT, conv.lastMessageRole)
    }

    @Test
    fun `getAllConversations returns null last-message fields for empty conversations`() = runTest {
        val now = Clock.System.now()
        repository.createConversation(
            externalId = "empty-conv",
            title = "Empty",
            createdAt = now,
            updatedAt = now
        )
        val all = repository.getAllConversations()
        assertEquals(1, all.size)
        assertNull(all[0].lastMessageContent)
        assertNull(all[0].lastMessageRole)
        assertNull(all[0].lastMessageCreatedAt)
    }

    @Test
    fun `insertMessage persists with COMPLETED status by default`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "msg-status-test",
            title = "Status",
            createdAt = now,
            updatedAt = now
        )
        val msgId = repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "Hello",
            createdAt = now.plus(1.hours)
        )
        assertTrue(msgId > 0)

        val messages = repository.getMessagesForConversation(convId)
        assertEquals(1, messages.size)
        assertEquals(ChatMessageStatus.COMPLETED, messages[0].status)
    }

    @Test
    fun `insertMessage persists PENDING status`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "pending-msg",
            title = "Pending Msg",
            createdAt = now,
            updatedAt = now
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.ASSISTANT,
            content = "Thinking...",
            createdAt = now.plus(1.hours),
            status = ChatMessageStatus.PENDING
        )
        val messages = repository.getMessagesForConversation(convId)
        assertEquals(1, messages.size)
        assertEquals(ChatMessageStatus.PENDING, messages[0].status)
    }

    @Test
    fun `insertMessage persists ERROR status`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "error-msg",
            title = "Error Msg",
            createdAt = now,
            updatedAt = now
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.ASSISTANT,
            content = "Error occurred",
            createdAt = now.plus(1.hours),
            status = ChatMessageStatus.ERROR
        )
        val messages = repository.getMessagesForConversation(convId)
        assertEquals(1, messages.size)
        assertEquals(ChatMessageStatus.ERROR, messages[0].status)
    }

    @Test
    fun `updateMessageStatus changes status`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "update-status-test",
            title = "Update Status",
            createdAt = now,
            updatedAt = now
        )
        val msgId = repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "Hello",
            createdAt = now.plus(1.hours),
            status = ChatMessageStatus.PENDING
        )
        repository.updateMessageStatus(msgId, ChatMessageStatus.COMPLETED)

        val messages = repository.getMessagesForConversation(convId)
        assertEquals(1, messages.size)
        assertEquals(ChatMessageStatus.COMPLETED, messages[0].status)
    }

    @Test
    fun `getMessagesForConversation returns messages in creation order`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "msg-order-test",
            title = "Order",
            createdAt = now,
            updatedAt = now
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "First",
            createdAt = now.plus(1.hours)
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.ASSISTANT,
            content = "Second",
            createdAt = now.plus(2.hours)
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "Third",
            createdAt = now.plus(3.hours)
        )

        val messages = repository.getMessagesForConversation(convId)
        assertEquals(3, messages.size)
        assertEquals("First", messages[0].content)
        assertEquals("Second", messages[1].content)
        assertEquals("Third", messages[2].content)
    }

    @Test
    fun `getMessagesForConversation uses messageId tiebreak when createdAt ties`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "tiebreak-test",
            title = "Tiebreak",
            createdAt = now,
            updatedAt = now
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "A",
            createdAt = now,
            status = ChatMessageStatus.COMPLETED,
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "B",
            createdAt = now,
            status = ChatMessageStatus.COMPLETED,
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "C",
            createdAt = now,
            status = ChatMessageStatus.COMPLETED,
        )

        val messages = repository.getMessagesForConversation(convId)
        assertEquals(3, messages.size)
        assertEquals("A", messages[0].content, "Tiebreak: oldest messageId first")
        assertEquals("B", messages[1].content)
        assertEquals("C", messages[2].content)
    }

    @Test
    fun `getLatestMessageForConversation filters out tool and non-completed messages`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "preview-filter",
            title = "Preview",
            createdAt = now,
            updatedAt = now
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "Hello",
            createdAt = now.plus(1.hours),
            status = ChatMessageStatus.COMPLETED,
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.ASSISTANT,
            content = "Hi!",
            createdAt = now.plus(2.hours),
            status = ChatMessageStatus.COMPLETED,
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.TOOL,
            content = "",
            createdAt = now.plus(3.hours),
            status = ChatMessageStatus.COMPLETED,
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.ASSISTANT,
            content = "PENDING",
            createdAt = now.plus(4.hours),
            status = ChatMessageStatus.PENDING,
        )
        repository.touchConversation(convId, now.plus(4.hours))

        val conversations = repository.getAllConversations()
        assertEquals(1, conversations.size)
        val conv = conversations[0]
        assertEquals("Hi!", conv.lastMessageContent)
        assertEquals(ChatRole.ASSISTANT, conv.lastMessageRole)
    }

    @Test
    fun `deleteMessage removes specific message`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "del-msg-test",
            title = "Del Msg",
            createdAt = now,
            updatedAt = now
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "Keep",
            createdAt = now.plus(1.hours)
        )
        val delMsgId = repository.insertMessage(
            conversationId = convId,
            role = ChatRole.ASSISTANT,
            content = "Delete",
            createdAt = now.plus(2.hours)
        )
        repository.deleteMessage(delMsgId)

        val messages = repository.getMessagesForConversation(convId)
        assertEquals(1, messages.size)
        assertEquals("Keep", messages[0].content)
    }

    @Test
    fun `getConversationCount returns correct count`() = runTest {
        val now = Clock.System.now()
        repository.createConversation(
            externalId = "count-a",
            title = "A",
            createdAt = now,
            updatedAt = now
        )
        repository.createConversation(
            externalId = "count-b",
            title = "B",
            createdAt = now,
            updatedAt = now
        )
        assertEquals(2, repository.getConversationCount())
    }

    @Test
    fun `getMessageCountForConversation returns correct count`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "msg-count-test",
            title = "Msg Count",
            createdAt = now,
            updatedAt = now
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.USER,
            content = "One",
            createdAt = now.plus(1.hours)
        )
        repository.insertMessage(
            conversationId = convId,
            role = ChatRole.ASSISTANT,
            content = "Two",
            createdAt = now.plus(2.hours)
        )
        assertEquals(2, repository.getMessageCountForConversation(convId))
    }

    @Test
    fun `conversation and message round-trips with all fields`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "round-trip",
            title = "Round Trip",
            provider = "openai",
            model = "gpt-4",
            createdAt = now,
            updatedAt = now
        )
        val msgId = repository.insertMessage(
            conversationId = convId,
            role = ChatRole.TOOL,
            content = "Tool result payload",
            createdAt = now.plus(1.hours),
            provider = "openai",
            model = "gpt-4",
            toolCallsJson = """[{"name":"get_health_data"}]""",
            toolResultJson = """{"result":"ok"}""",
            status = ChatMessageStatus.PENDING
        )

        val conversation = repository.getConversationById(convId)
        assertNotNull(conversation)
        assertEquals("openai", conversation.provider)
        assertEquals("gpt-4", conversation.model)

        val messages = repository.getMessagesForConversation(convId)
        assertEquals(1, messages.size)
        val msg = messages[0]
        assertEquals(msgId, msg.messageId)
        assertEquals(ChatRole.TOOL, msg.role)
        assertEquals("Tool result payload", msg.content)
        assertEquals("openai", msg.provider)
        assertEquals("gpt-4", msg.model)
        assertEquals("""[{"name":"get_health_data"}]""", msg.toolCallsJson)
        assertEquals("""{"result":"ok"}""", msg.toolResultJson)
        assertEquals(ChatMessageStatus.PENDING, msg.status)
    }

    @Test
    fun `unknown persisted status maps to ERROR`() = runTest {
        val now = Clock.System.now()
        val convId = repository.createConversation(
            externalId = "unknown-status",
            title = "Unknown",
            createdAt = now,
            updatedAt = now
        )
        driver.execute(
            identifier = null,
            sql = """
                INSERT INTO ChatMessage(conversationId, role, content, createdAt, provider, model, toolCallsJson, toolResultJson, status)
                VALUES ($convId, 'user', 'Unknown status', ${now.toEpochMilliseconds()}, NULL, NULL, NULL, NULL, 'bogus')
            """.trimIndent(),
            parameters = 0
        )

        val messages = repository.getMessagesForConversation(convId)
        assertEquals(1, messages.size)
        assertEquals(ChatMessageStatus.ERROR, messages[0].status, "Unknown persisted status must map to ERROR")
    }
}
