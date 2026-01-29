package com.wellnesswingman.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform-specific database driver factory.
 * Each platform (Android, iOS, Desktop) provides its own implementation.
 */
expect class DriverFactory {
    fun createDriver(): SqlDriver
}
