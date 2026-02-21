package com.wellnesswingman.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wellnesswingman.db.WellnessWingmanDatabase

/**
 * Android implementation of database driver factory.
 * AndroidSqliteDriver automatically handles migrations using the Schema.
 */
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = WellnessWingmanDatabase.Schema,
            context = context,
            name = "wellnesswingman.db",
            callback = object : AndroidSqliteDriver.Callback(WellnessWingmanDatabase.Schema) {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            }
        )
    }
}
