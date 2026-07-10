package com.wellnesswingman.domain.migration

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatDatabaseMigrationTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: WellnessWingmanDatabase

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
        database = WellnessWingmanDatabase(driver)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun `version 9 to 10 migration creates health-chat tables and cascade deletion`() = runTest {
        val migrationDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        try {
            migrationDriver.execute(
                identifier = null,
                sql = """
                    CREATE TABLE IF NOT EXISTS WellnessWingmanDatabase(
                        version INTEGER NOT NULL
                    );
                """.trimIndent(),
                parameters = 0
            )

            migrationDriver.execute(
                identifier = null,
                sql = "CREATE TABLE ChatConversation (" +
                    "conversationId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "externalId TEXT NOT NULL UNIQUE, " +
                    "title TEXT NOT NULL DEFAULT '', " +
                    "provider TEXT, " +
                    "model TEXT, " +
                    "createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL" +
                    ")",
                parameters = 0
            )
            migrationDriver.execute(
                identifier = null,
                sql = "INSERT INTO ChatConversation " +
                    "(externalId, title, createdAt, updatedAt) " +
                    "VALUES ('conv-1', 'Test', 1000, 1000)",
                parameters = 0
            )

            migrationDriver.execute(
                identifier = null,
                sql = "CREATE TABLE ChatMessage (" +
                    "messageId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "conversationId INTEGER NOT NULL, " +
                    "role TEXT NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "provider TEXT, " +
                    "model TEXT, " +
                    "toolCallsJson TEXT, " +
                    "toolResultJson TEXT" +
                    ")",
                parameters = 0
            )
            migrationDriver.execute(
                identifier = null,
                sql = "INSERT INTO ChatMessage " +
                    "(conversationId, role, content, createdAt) " +
                    "VALUES (1, 'user', 'Hello', 1001)",
                parameters = 0
            )

            migrationDriver.execute(
                identifier = null,
                sql = "CREATE INDEX idx_chat_message_conversation " +
                    "ON ChatMessage(conversationId, createdAt)",
                parameters = 0
            )

            migrationDriver.execute(
                identifier = null,
                sql = "PRAGMA user_version = 9",
                parameters = 0
            )

            WellnessWingmanDatabase.Schema.migrate(
                driver = migrationDriver,
                oldVersion = 9,
                newVersion = 10
            )

            val migratedDb = WellnessWingmanDatabase(migrationDriver)

            val conversations = migratedDb.chatConversationQueries.getAllConversations()
                .executeAsList()
            assertEquals(1, conversations.size)
            assertEquals("conv-1", conversations[0].externalId)
            assertEquals("Test", conversations[0].title)

            val messages = migratedDb.chatConversationQueries
                .getMessagesForConversation(1)
                .executeAsList()
            assertEquals(1, messages.size)
            assertEquals("user", messages[0].role)
            assertEquals("Hello", messages[0].content)

            migratedDb.chatConversationQueries.deleteConversation(1)

            val messagesAfterDelete = migratedDb.chatConversationQueries
                .getMessagesForConversation(1)
                .executeAsList()
            assertEquals(0, messagesAfterDelete.size)
        } finally {
            migrationDriver.close()
        }
    }
}
