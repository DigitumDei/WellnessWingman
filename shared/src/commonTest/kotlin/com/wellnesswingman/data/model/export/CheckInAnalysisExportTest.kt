package com.wellnesswingman.data.model.export

import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.analysis.CheckInAnalysisStatus
import com.wellnesswingman.data.model.analysis.CheckInFacets
import com.wellnesswingman.data.model.analysis.CheckInFactor
import com.wellnesswingman.data.model.analysis.FactorOrigin
import com.wellnesswingman.data.model.analysis.FactorValence
import com.wellnesswingman.data.model.analysis.MentionedFood
import com.wellnesswingman.data.model.analysis.NutritionEstimate
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.*

/**
 * Covers the export and import mapping for check-in extractions.
 *
 * Facets are derived and regenerable, so the import side must degrade rather than fail: a blob
 * this build cannot read is a reason to re-run extraction, not to abandon the archive.
 */
class CheckInAnalysisExportTest {

    private val date = LocalDate(2026, 8, 12)
    private val analyzedAt = Instant.fromEpochMilliseconds(1_785_000_000_000)

    private val facets = CheckInFacets(
        mentionedFood = listOf(
            MentionedFood(
                name = "cheese",
                portionSize = "one slice, about 20g",
                nutrition = NutritionEstimate(totalCalories = 80.0, fat = 6.5),
                confidence = 0.6,
                possiblyAlreadyLogged = false,
                eatenOnCheckInDate = true
            )
        ),
        factors = listOf(
            CheckInFactor(
                description = "the cat brought in a rat at 1am",
                valence = FactorValence.BAD,
                origin = FactorOrigin.EXTERNAL,
                quote = "the cats brought a rat in at 1am",
                domain = "sleep",
                confidence = 0.9
            )
        ),
        confidence = 0.75,
        warnings = listOf("portion was vague")
    )

    private fun analysis(
        status: CheckInAnalysisStatus = CheckInAnalysisStatus.COMPLETED,
        facets: CheckInFacets? = this.facets,
        errorMessage: String? = null
    ) = CheckInAnalysis(
        analysisId = 7L,
        externalId = "checkin-analysis-7",
        checkInDate = date,
        slot = CheckInSlot.MORNING,
        status = status,
        providerId = "openai",
        model = "gpt-4o-mini",
        analyzedAt = analyzedAt,
        facets = facets,
        errorMessage = errorMessage
    )

    @Test
    fun `a completed analysis survives a round trip`() {
        val restored = assertNotNull(analysis().toExport().toDomain())

        assertEquals(7L, restored.analysisId)
        assertEquals("checkin-analysis-7", restored.externalId)
        assertEquals(date, restored.checkInDate)
        assertEquals(CheckInSlot.MORNING, restored.slot)
        assertEquals(CheckInAnalysisStatus.COMPLETED, restored.status)
        assertEquals("openai", restored.providerId)
        assertEquals("gpt-4o-mini", restored.model)
        assertEquals(analyzedAt, restored.analyzedAt)
    }

    @Test
    fun `facet detail survives the round trip`() {
        val restored = assertNotNull(analysis().toExport().toDomain())
        val restoredFacets = assertNotNull(restored.facets)

        val food = restoredFacets.mentionedFood.single()
        assertEquals("cheese", food.name)
        assertEquals(80.0, food.nutrition?.totalCalories)
        assertTrue(food.eatenOnCheckInDate)

        val factor = restoredFacets.factors.single()
        assertEquals(FactorValence.BAD, factor.valence)
        assertEquals(FactorOrigin.EXTERNAL, factor.origin)
        assertEquals("sleep", factor.domain)
        assertEquals(listOf("portion was vague"), restoredFacets.warnings)
    }

    @Test
    fun `a failed analysis exports its message and no facets`() {
        val exported = analysis(
            status = CheckInAnalysisStatus.FAILED,
            facets = null,
            errorMessage = "network down"
        ).toExport()

        assertEquals("", exported.facetsJson)

        val restored = assertNotNull(exported.toDomain())
        assertEquals(CheckInAnalysisStatus.FAILED, restored.status)
        assertEquals("network down", restored.errorMessage)
        assertNull(restored.facets)
    }

    @Test
    fun `an unrecognised slot is dropped rather than guessed`() {
        val broken = analysis().toExport().copy(slot = "Afternoon")

        // An analysis with no slot has no check-in to attach to.
        assertNull(broken.toDomain())
    }

    @Test
    fun `an unreadable facets blob keeps the row and drops only the facets`() {
        val broken = analysis().toExport().copy(facetsJson = "{ not json at all")

        val restored = assertNotNull(broken.toDomain())

        // The row still records that extraction was attempted and how it went.
        assertNull(restored.facets)
        assertEquals(CheckInAnalysisStatus.COMPLETED, restored.status)
    }

    @Test
    fun `an unparseable date falls back rather than throwing`() {
        val odd = analysis().toExport().copy(checkInDate = "2026-08-12T00:00:00Z")

        assertEquals(date, assertNotNull(odd.toDomain()).checkInDate)
    }

    @Test
    fun `an unparseable timestamp falls back to the start of the day`() {
        val odd = analysis().toExport().copy(analyzedAt = "not a timestamp")

        assertNotNull(odd.toDomain()).let {
            assertEquals(date, it.checkInDate)
            assertNotNull(it.analyzedAt)
        }
    }

    @Test
    fun `export data carries check-in analyses`() {
        val data = ExportData(
            version = 1,
            exportedAt = "2026-08-12T00:00:00Z",
            checkInAnalyses = listOf(analysis().toExport())
        )

        assertEquals(1, data.checkInAnalyses.size)
        assertEquals("checkin-analysis-7", data.checkInAnalyses.single().externalId)
    }
}
