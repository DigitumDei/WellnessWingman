package com.wellnesswingman.domain.migration

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn
import com.wellnesswingman.data.repository.SqlDelightDailyCheckInRepository
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.*

/**
 * Covers the 9 -> 10 migration that introduces DailyCheckIn.
 *
 * The "old" database is built by creating the current schema and dropping the check-in table,
 * which is a faithful stand-in here because the migration only adds that table and its index —
 * it alters nothing pre-existing. This proves the migration runs and leaves a usable table on an
 * upgraded install; it does not attempt to reconstruct the full historical v9 schema.
 */
class DailyCheckInDatabaseMigrationTest {

    private lateinit var driver: SqlDriver

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    private fun execute(sql: String) {
        driver.execute(null, sql, 0)
    }

    private fun dropCheckInTable() {
        execute("DROP INDEX IF EXISTS idx_daily_checkin_date_slot")
        execute("DROP TABLE IF EXISTS DailyCheckIn")
    }

    private fun tableExists(name: String): Boolean {
        val query = driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$name'",
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
                )
            },
            parameters = 0
        )
        return query.value > 0L
    }

    @Test
    fun `migration creates the DailyCheckIn table`() {
        dropCheckInTable()
        assertFalse(tableExists("DailyCheckIn"), "Precondition: table should be absent")

        WellnessWingmanDatabase.Schema.migrate(driver, 9, 10)

        assertTrue(tableExists("DailyCheckIn"), "Migration should have created DailyCheckIn")
    }

    @Test
    fun `migrated table accepts and returns check-ins`() = runTest {
        dropCheckInTable()
        WellnessWingmanDatabase.Schema.migrate(driver, 9, 10)

        val repository = SqlDelightDailyCheckInRepository(WellnessWingmanDatabase(driver))
        val date = LocalDate(2026, 8, 7)

        repository.saveCheckIn(
            DailyCheckIn(
                checkInDate = date,
                slot = CheckInSlot.MORNING,
                capturedAt = Instant.fromEpochMilliseconds(1_785_000_000_000),
                responseText = "Slept badly"
            )
        )

        val retrieved = repository.getCheckIn(date, CheckInSlot.MORNING)

        assertNotNull(retrieved)
        assertEquals("Slept badly", retrieved.responseText)
    }

    @Test
    fun `migrated unique index still enforces one check-in per slot per day`() = runTest {
        dropCheckInTable()
        WellnessWingmanDatabase.Schema.migrate(driver, 9, 10)

        val repository = SqlDelightDailyCheckInRepository(WellnessWingmanDatabase(driver))
        val date = LocalDate(2026, 8, 7)

        repeat(2) { index ->
            repository.saveCheckIn(
                DailyCheckIn(
                    checkInDate = date,
                    slot = CheckInSlot.EVENING,
                    capturedAt = Instant.fromEpochMilliseconds(1_785_000_000_000 + index),
                    responseText = "Answer $index"
                )
            )
        }

        // Without the index the upsert would insert twice instead of replacing.
        val forDate = repository.getCheckInsForDate(date)
        assertEquals(1, forDate.size)
        assertEquals("Answer 1", forDate.single().responseText)
    }
}
