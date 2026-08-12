package com.wellnesswingman.domain.migration

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.analysis.CheckInAnalysisStatus
import com.wellnesswingman.data.model.analysis.CheckInFacets
import com.wellnesswingman.data.model.analysis.CheckInFactor
import com.wellnesswingman.data.model.analysis.FactorOrigin
import com.wellnesswingman.data.model.analysis.FactorValence
import com.wellnesswingman.data.repository.SqlDelightCheckInAnalysisRepository
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.*

/**
 * Covers the 12 -> 13 migration that introduces CheckInAnalysis.
 *
 * Follows the same approach as [DailyCheckInDatabaseMigrationTest]: build the current schema and
 * drop the new table, which is faithful here because the migration only adds that table and its
 * index and alters nothing pre-existing.
 */
class CheckInAnalysisDatabaseMigrationTest {

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

    private fun dropAnalysisTable() {
        execute("DROP INDEX IF EXISTS idx_checkin_analysis_date_slot")
        execute("DROP TABLE IF EXISTS CheckInAnalysis")
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

    private fun analysis(
        date: LocalDate,
        slot: CheckInSlot = CheckInSlot.MORNING,
        status: CheckInAnalysisStatus = CheckInAnalysisStatus.COMPLETED,
        facets: CheckInFacets? = null
    ) = CheckInAnalysis(
        checkInDate = date,
        slot = slot,
        status = status,
        providerId = "openai",
        model = "gpt-4o-mini",
        analyzedAt = Instant.fromEpochMilliseconds(1_785_000_000_000),
        facets = facets
    )

    @Test
    fun `migration creates the CheckInAnalysis table`() {
        dropAnalysisTable()
        assertFalse(tableExists("CheckInAnalysis"), "Precondition: table should be absent")

        WellnessWingmanDatabase.Schema.migrate(driver, 12, 13)

        assertTrue(tableExists("CheckInAnalysis"), "Migration should have created CheckInAnalysis")
    }

    @Test
    fun `migrated table round-trips facets`() = runTest {
        dropAnalysisTable()
        WellnessWingmanDatabase.Schema.migrate(driver, 12, 13)

        val repository = SqlDelightCheckInAnalysisRepository(WellnessWingmanDatabase(driver))
        val date = LocalDate(2026, 8, 11)

        repository.saveAnalysis(
            analysis(
                date = date,
                facets = CheckInFacets(
                    factors = listOf(
                        CheckInFactor(
                            description = "the cat brought in a rat at 1am",
                            valence = FactorValence.BAD,
                            origin = FactorOrigin.EXTERNAL
                        )
                    )
                )
            )
        )

        val retrieved = repository.getAnalysis(date, CheckInSlot.MORNING)

        assertNotNull(retrieved)
        val factor = retrieved.facets?.factors?.single()
        assertNotNull(factor)
        assertEquals(FactorValence.BAD, factor.valence)
        assertEquals(FactorOrigin.EXTERNAL, factor.origin)
    }

    @Test
    fun `migrated unique index keeps one analysis per slot per day`() = runTest {
        dropAnalysisTable()
        WellnessWingmanDatabase.Schema.migrate(driver, 12, 13)

        val repository = SqlDelightCheckInAnalysisRepository(WellnessWingmanDatabase(driver))
        val date = LocalDate(2026, 8, 11)

        repository.saveAnalysis(analysis(date, status = CheckInAnalysisStatus.PENDING))
        repository.saveAnalysis(analysis(date, status = CheckInAnalysisStatus.COMPLETED))

        // Re-running extraction replaces the earlier attempt rather than accumulating rows.
        val forDate = repository.getAnalysesForDate(date)
        assertEquals(1, forDate.size)
        assertEquals(CheckInAnalysisStatus.COMPLETED, forDate.single().status)
    }
}
