package com.wellnesswingman.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.analysis.CheckInAnalysisStatus
import com.wellnesswingman.data.model.analysis.CheckInFacets
import com.wellnesswingman.data.model.analysis.CheckInFactor
import com.wellnesswingman.data.model.analysis.FactorOrigin
import com.wellnesswingman.data.model.analysis.FactorValence
import com.wellnesswingman.data.model.analysis.MentionedFood
import com.wellnesswingman.data.model.analysis.NutritionEstimate
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.*

class SqlDelightCheckInAnalysisRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var repository: SqlDelightCheckInAnalysisRepository

    private val date = LocalDate(2026, 8, 12)
    private val analyzedAt = Instant.fromEpochMilliseconds(1_785_000_000_000)

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
        repository = SqlDelightCheckInAnalysisRepository(WellnessWingmanDatabase(driver))
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    private val facets = CheckInFacets(
        mentionedFood = listOf(
            MentionedFood(
                name = "cheese",
                portionSize = "a slice, about 20g",
                nutrition = NutritionEstimate(totalCalories = 80.0, fat = 6.5),
                confidence = 0.6
            )
        ),
        factors = listOf(
            CheckInFactor(
                description = "the cat brought in a rat at 1am",
                valence = FactorValence.BAD,
                origin = FactorOrigin.EXTERNAL,
                domain = "sleep",
                confidence = 0.9
            )
        ),
        confidence = 0.7
    )

    private fun analysis(
        slot: CheckInSlot = CheckInSlot.MORNING,
        on: LocalDate = date,
        status: CheckInAnalysisStatus = CheckInAnalysisStatus.COMPLETED,
        facets: CheckInFacets? = this.facets,
        errorMessage: String? = null,
        externalId: String? = null
    ) = CheckInAnalysis(
        externalId = externalId,
        checkInDate = on,
        slot = slot,
        status = status,
        providerId = "openai",
        model = "gpt-4o-mini",
        analyzedAt = analyzedAt,
        facets = facets,
        errorMessage = errorMessage
    )

    @Test
    fun `saves and reads back a full analysis`() = runTest {
        repository.saveAnalysis(analysis())

        val stored = assertNotNull(repository.getAnalysis(date, CheckInSlot.MORNING))
        assertEquals(CheckInAnalysisStatus.COMPLETED, stored.status)
        assertEquals("openai", stored.providerId)
        assertEquals("gpt-4o-mini", stored.model)
        assertEquals(analyzedAt, stored.analyzedAt)

        val food = assertNotNull(stored.facets).mentionedFood.single()
        assertEquals("cheese", food.name)
        assertEquals(80.0, food.nutrition?.totalCalories)
        assertEquals(FactorOrigin.EXTERNAL, stored.facets!!.factors.single().origin)
    }

    @Test
    fun `saving again for the same slot replaces rather than accumulates`() = runTest {
        repository.saveAnalysis(analysis(status = CheckInAnalysisStatus.PENDING, facets = null))
        repository.saveAnalysis(analysis(status = CheckInAnalysisStatus.COMPLETED))

        val forDate = repository.getAnalysesForDate(date)
        assertEquals(1, forDate.size)
        assertEquals(CheckInAnalysisStatus.COMPLETED, forDate.single().status)
    }

    @Test
    fun `the two slots of a day are stored independently`() = runTest {
        repository.saveAnalysis(analysis(slot = CheckInSlot.MORNING))
        repository.saveAnalysis(analysis(slot = CheckInSlot.EVENING))

        assertEquals(2, repository.getAnalysesForDate(date).size)
        assertNotNull(repository.getAnalysis(date, CheckInSlot.EVENING))
    }

    @Test
    fun `a date range returns only the days inside it`() = runTest {
        repository.saveAnalysis(analysis(on = LocalDate(2026, 8, 10)))
        repository.saveAnalysis(analysis(on = LocalDate(2026, 8, 12)))
        repository.saveAnalysis(analysis(on = LocalDate(2026, 8, 20)))

        val inRange = repository.getAnalysesForDateRange(
            LocalDate(2026, 8, 10),
            LocalDate(2026, 8, 12)
        )

        assertEquals(2, inRange.size)
    }

    @Test
    fun `an analysis can be found by external id`() = runTest {
        repository.saveAnalysis(analysis(externalId = "checkin-analysis-1"))

        assertNotNull(repository.getAnalysisByExternalId("checkin-analysis-1"))
        assertNull(repository.getAnalysisByExternalId("nope"))
    }

    @Test
    fun `a failed analysis keeps its message and stores no facets`() = runTest {
        repository.saveAnalysis(
            analysis(
                status = CheckInAnalysisStatus.FAILED,
                facets = null,
                errorMessage = "network down"
            )
        )

        val stored = assertNotNull(repository.getAnalysis(date, CheckInSlot.MORNING))
        assertEquals(CheckInAnalysisStatus.FAILED, stored.status)
        assertEquals("network down", stored.errorMessage)
        assertNull(stored.facets)
    }

    @Test
    fun `deleting removes only that slot`() = runTest {
        repository.saveAnalysis(analysis(slot = CheckInSlot.MORNING))
        repository.saveAnalysis(analysis(slot = CheckInSlot.EVENING))

        repository.deleteAnalysis(date, CheckInSlot.MORNING)

        assertNull(repository.getAnalysis(date, CheckInSlot.MORNING))
        assertNotNull(repository.getAnalysis(date, CheckInSlot.EVENING))
    }

    @Test
    fun `old analyses are purged by date`() = runTest {
        repository.saveAnalysis(analysis(on = LocalDate(2026, 7, 1)))
        repository.saveAnalysis(analysis(on = LocalDate(2026, 8, 12)))

        repository.deleteOldAnalyses(LocalDate(2026, 8, 1))

        assertEquals(1, repository.getAllAnalyses().size)
        assertNotNull(repository.getAnalysis(LocalDate(2026, 8, 12), CheckInSlot.MORNING))
    }

    @Test
    fun `upsert preserves the primary key for import`() = runTest {
        val imported = analysis().copy(analysisId = 42L)

        repository.upsertAnalysis(imported)

        val stored = assertNotNull(repository.getAnalysis(date, CheckInSlot.MORNING))
        assertEquals(42L, stored.analysisId)
    }

    @Test
    fun `getAllAnalyses returns every stored row`() = runTest {
        repository.saveAnalysis(analysis(slot = CheckInSlot.MORNING))
        repository.saveAnalysis(analysis(slot = CheckInSlot.EVENING, on = LocalDate(2026, 8, 11)))

        assertEquals(2, repository.getAllAnalyses().size)
    }

    @Test
    fun `an unreadable facets blob reads back as failed and retryable`() = runTest {
        repository.saveAnalysis(analysis())

        // Simulates a blob written by a newer schema: the row still claims COMPLETED.
        driver.execute(
            null,
            "UPDATE CheckInAnalysis SET facetsJson = '{ not json at all' WHERE checkInDate = ${date.toEpochDays()}",
            0
        )

        val stored = assertNotNull(repository.getAnalysis(date, CheckInSlot.MORNING))

        // Reporting it as completed-with-no-facets would read as "we looked and found nothing"
        // and offer no way to regenerate data that exists to be regenerated.
        assertEquals(CheckInAnalysisStatus.FAILED, stored.status)
        assertNull(stored.facets)
        assertNotNull(stored.errorMessage)
    }

    @Test
    fun `a missing analysis reads as null rather than throwing`() = runTest {
        assertNull(repository.getAnalysis(date, CheckInSlot.MORNING))
        assertTrue(repository.getAnalysesForDate(date).isEmpty())
    }
}
