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
                sql = "PRAGMA user_version = 9",
                parameters = 0
            )

            WellnessWingmanDatabase.Schema.migrate(
                driver = migrationDriver,
                oldVersion = 9,
                newVersion = 10
            )

            // Generated queries target the current (v11) schema, so complete the
            // next migration before querying the v10 tables.
            WellnessWingmanDatabase.Schema.migrate(
                driver = migrationDriver,
                oldVersion = 10,
                newVersion = 11
            )

            val migratedDb = WellnessWingmanDatabase(migrationDriver)

            migrationDriver.execute(
                identifier = null,
                sql = """
                    INSERT INTO ChatConversation(externalId, title, provider, model, createdAt, updatedAt)
                    VALUES ('conv-1', 'Test', NULL, NULL, 1000, 1000)
                """.trimIndent(),
                parameters = 0
            )

            migrationDriver.execute(
                identifier = null,
                sql = """
                    INSERT INTO ChatMessage(conversationId, role, content, createdAt, provider, model, toolCallsJson, toolResultJson)
                    VALUES (1, 'user', 'Hello', 1001, NULL, NULL, NULL, NULL)
                """.trimIndent(),
                parameters = 0
            )

            val conversations = migratedDb.chatConversationQueries.getConversationById(1)
                .executeAsOneOrNull()
            assertNotNull(conversations)
            assertEquals("conv-1", conversations.externalId)
            assertEquals("Test", conversations.title)

            val messages = migratedDb.chatConversationQueries
                .getMessagesForConversation(1)
                .executeAsList()
            assertEquals(1, messages.size)
            assertEquals("user", messages[0].role)
            assertEquals("Hello", messages[0].content)

            migrationDriver.execute(
                identifier = null,
                sql = "PRAGMA foreign_keys = ON",
                parameters = 0
            )

            migratedDb.chatConversationQueries.deleteConversation(1)

            val messagesAfterDelete = migratedDb.chatConversationQueries
                .getMessagesForConversation(1)
                .executeAsList()
            assertEquals(0, messagesAfterDelete.size)
        } finally {
            migrationDriver.close()
        }
    }

    @Test
    fun `version 10 to 11 migration adds status column to ChatMessage`() = runTest {
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
                sql = "PRAGMA user_version = 10",
                parameters = 0
            )

            migrationDriver.execute(
                identifier = null,
                sql = """
                    CREATE TABLE ChatConversation (
                        conversationId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        externalId TEXT NOT NULL UNIQUE,
                        title TEXT NOT NULL DEFAULT '',
                        provider TEXT,
                        model TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    );
                """.trimIndent(),
                parameters = 0
            )

            migrationDriver.execute(
                identifier = null,
                sql = """
                    CREATE TABLE ChatMessage (
                        messageId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        conversationId INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        provider TEXT,
                        model TEXT,
                        toolCallsJson TEXT,
                        toolResultJson TEXT,
                        FOREIGN KEY (conversationId) REFERENCES ChatConversation(conversationId) ON DELETE CASCADE
                    );
                """.trimIndent(),
                parameters = 0
            )

            migrationDriver.execute(
                identifier = null,
                sql = """
                    CREATE INDEX idx_chat_message_conversation ON ChatMessage(conversationId, createdAt);
                """.trimIndent(),
                parameters = 0
            )

            WellnessWingmanDatabase.Schema.migrate(
                driver = migrationDriver,
                oldVersion = 10,
                newVersion = 11
            )

            val migratedDb = WellnessWingmanDatabase(migrationDriver)

            migratedDb.chatConversationQueries.insertConversation(
                externalId = "conv-status-test",
                title = "Status Test",
                provider = null,
                model = null,
                createdAt = 1000,
                updatedAt = 1000
            )

            migratedDb.chatConversationQueries.insertMessage(
                conversationId = 1,
                role = "user",
                content = "Status check",
                createdAt = 1001,
                provider = null,
                model = null,
                toolCallsJson = null,
                toolResultJson = null,
                status = "pending"
            )

            val messages = migratedDb.chatConversationQueries
                .getMessagesForConversation(1)
                .executeAsList()
            assertEquals(1, messages.size)
            assertEquals("pending", messages[0].status)
        } finally {
            migrationDriver.close()
        }
    }
}
