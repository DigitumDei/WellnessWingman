package com.wellnesswingman.domain.migration

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

            val migratedDb = WellnessWingmanDatabase(migrationDriver)

            migratedDb.chatConversationQueries.insertConversation(
                externalId = "conv-1",
                title = "Test",
                provider = null,
                model = null,
                createdAt = 1000,
                updatedAt = 1000
            )
            migratedDb.chatConversationQueries.insertMessage(
                conversationId = 1,
                role = "user",
                content = "Hello",
                createdAt = 1001,
                provider = null,
                model = null,
                toolCallsJson = null,
                toolResultJson = null
            )

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
}
