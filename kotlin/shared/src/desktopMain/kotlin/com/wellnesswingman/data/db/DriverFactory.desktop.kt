package com.wellnesswingman.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.db.WellnessWingmanDatabase
import java.io.File
import java.util.Properties

/**
 * Desktop (JVM) implementation of database driver factory.
 */
actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        val databasePath = File(
            System.getProperty("user.home"),
            ".wellnesswingman/wellnesswingman.db"
        )
        databasePath.parentFile?.mkdirs()

        val databaseExists = databasePath.exists()
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${databasePath.absolutePath}",
            properties = Properties()
        )

        if (!databaseExists) {
            // Fresh install - create schema
            WellnessWingmanDatabase.Schema.create(driver)
        } else {
            // Existing database - run migrations if needed
            val currentVersion = getCurrentVersion(driver)
            val schemaVersion = WellnessWingmanDatabase.Schema.version

            if (currentVersion < schemaVersion) {
                WellnessWingmanDatabase.Schema.migrate(
                    driver = driver,
                    oldVersion = currentVersion,
                    newVersion = schemaVersion
                )
            }
        }

        return driver
    }

    private fun getCurrentVersion(driver: SqlDriver): Long {
        // SQLite stores schema version in user_version pragma
        val result = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor: app.cash.sqldelight.db.SqlCursor ->
                QueryResult.Value(
                    if (cursor.next().value) {
                        cursor.getLong(0) ?: 0L
                    } else {
                        0L
                    }
                )
            },
            parameters = 0
        )
        return result.value
    }
}
