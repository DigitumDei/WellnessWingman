package com.wellnesswingman.data.model.analysis

import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.CheckInSlot
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.*

/**
 * Covers the derived properties the UI and totals rely on.
 *
 * These decide which food reaches a day's calories and which state a card shows, so they are
 * worth pinning directly rather than only through the service that produces them.
 */
class CheckInFacetsTest {

    private fun food(
        name: String,
        calories: Double? = 100.0,
        alreadyLogged: Boolean = false,
        onDate: Boolean = true
    ) = MentionedFood(
        name = name,
        nutrition = calories?.let { NutritionEstimate(totalCalories = it) },
        possiblyAlreadyLogged = alreadyLogged,
        eatenOnCheckInDate = onDate
    )

    private fun factor(valence: FactorValence, origin: FactorOrigin) = CheckInFactor(
        description = "something",
        valence = valence,
        origin = origin
    )

    @Test
    fun `countable food excludes duplicates and other days`() {
        val facets = CheckInFacets(
            mentionedFood = listOf(
                food("beer"),
                food("salad", alreadyLogged = true),
                food("last night's chips", onDate = false)
            )
        )

        assertEquals(listOf("beer"), facets.countableFood.map { it.name })
    }

    @Test
    fun `good and bad factors are separated`() {
        val facets = CheckInFacets(
            factors = listOf(
                factor(FactorValence.GOOD, FactorOrigin.INTERNAL),
                factor(FactorValence.BAD, FactorOrigin.EXTERNAL),
                factor(FactorValence.BAD, FactorOrigin.INTERNAL)
            )
        )

        assertEquals(1, facets.goodFactors.size)
        assertEquals(2, facets.badFactors.size)
    }

    @Test
    fun `empty means neither food nor factors`() {
        assertTrue(CheckInFacets().isEmpty)
        assertFalse(CheckInFacets(mentionedFood = listOf(food("toast"))).isEmpty)
        assertFalse(
            CheckInFacets(factors = listOf(factor(FactorValence.GOOD, FactorOrigin.INTERNAL)))
                .isEmpty
        )
    }

    @Test
    fun `valence parses the words a model might use`() {
        assertEquals(FactorValence.GOOD, FactorValence.fromString("Good"))
        assertEquals(FactorValence.GOOD, FactorValence.fromString("positive"))
        assertEquals(FactorValence.BAD, FactorValence.fromString("BAD"))
        assertEquals(FactorValence.BAD, FactorValence.fromString(" negative "))
        assertNull(FactorValence.fromString("sideways"))
        assertNull(FactorValence.fromString(null))
    }

    @Test
    fun `origin parses case-insensitively and rejects anything else`() {
        assertEquals(FactorOrigin.INTERNAL, FactorOrigin.fromString("Internal"))
        assertEquals(FactorOrigin.EXTERNAL, FactorOrigin.fromString(" EXTERNAL "))
        assertNull(FactorOrigin.fromString("both"))
        assertNull(FactorOrigin.fromString(null))
    }

    @Test
    fun `enums round-trip through their storage strings`() {
        assertEquals(FactorValence.GOOD, FactorValence.fromString(FactorValence.GOOD.toStorageString()))
        assertEquals(FactorValence.BAD, FactorValence.fromString(FactorValence.BAD.toStorageString()))
        assertEquals(FactorOrigin.INTERNAL, FactorOrigin.fromString(FactorOrigin.INTERNAL.toStorageString()))
        assertEquals(FactorOrigin.EXTERNAL, FactorOrigin.fromString(FactorOrigin.EXTERNAL.toStorageString()))
    }

    @Test
    fun `analysis status defaults to pending for anything unrecognised`() {
        assertEquals(CheckInAnalysisStatus.COMPLETED, CheckInAnalysisStatus.fromString("Completed"))
        assertEquals(CheckInAnalysisStatus.FAILED, CheckInAnalysisStatus.fromString("failed"))
        assertEquals(CheckInAnalysisStatus.PENDING, CheckInAnalysisStatus.fromString("Pending"))
        // An unreadable status must not present as finished work.
        assertEquals(CheckInAnalysisStatus.PENDING, CheckInAnalysisStatus.fromString("nonsense"))
        assertEquals(CheckInAnalysisStatus.PENDING, CheckInAnalysisStatus.fromString(null))
    }

    @Test
    fun `status round-trips through storage strings`() {
        CheckInAnalysisStatus.entries.forEach {
            assertEquals(it, CheckInAnalysisStatus.fromString(it.toStorageString()))
        }
    }

    @Test
    fun `completed facets are only exposed once extraction finished`() {
        val facets = CheckInFacets(mentionedFood = listOf(food("toast")))

        fun analysis(status: CheckInAnalysisStatus) = CheckInAnalysis(
            checkInDate = LocalDate(2026, 8, 12),
            slot = CheckInSlot.MORNING,
            status = status,
            analyzedAt = Instant.fromEpochMilliseconds(1_785_000_000_000),
            facets = facets
        )

        // Pending holds no result yet, which is not the same as having found nothing.
        assertNull(analysis(CheckInAnalysisStatus.PENDING).completedFacets)
        assertNull(analysis(CheckInAnalysisStatus.FAILED).completedFacets)
        assertNotNull(analysis(CheckInAnalysisStatus.COMPLETED).completedFacets)
    }

    @Test
    fun `analysis state flags match the stored status`() {
        fun analysis(status: CheckInAnalysisStatus) = CheckInAnalysis(
            checkInDate = LocalDate(2026, 8, 12),
            slot = CheckInSlot.EVENING,
            status = status,
            analyzedAt = Instant.fromEpochMilliseconds(1_785_000_000_000)
        )

        assertTrue(analysis(CheckInAnalysisStatus.PENDING).isPending)
        assertFalse(analysis(CheckInAnalysisStatus.PENDING).hasFailed)
        assertTrue(analysis(CheckInAnalysisStatus.FAILED).hasFailed)
        assertFalse(analysis(CheckInAnalysisStatus.COMPLETED).isPending)
    }
}
