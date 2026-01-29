package com.wellnesswingman.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.db.WellnessWingmanDatabase
import java.io.File

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

        val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.absolutePath}")
        WellnessWingmanDatabase.Schema.create(driver)
        return driver
    }
}
