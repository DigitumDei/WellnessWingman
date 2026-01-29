package com.wellnesswingman.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.wellnesswingman.db.WellnessWingmanDatabase

/**
 * iOS implementation of database driver factory.
 */
actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = WellnessWingmanDatabase.Schema,
            name = "wellnesswingman.db"
        )
    }
}
