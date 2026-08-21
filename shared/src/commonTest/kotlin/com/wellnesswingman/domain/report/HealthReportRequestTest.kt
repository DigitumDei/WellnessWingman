package com.wellnesswingman.domain.report

import com.wellnesswingman.domain.common.DateRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HealthReportRequestTest {

    private val range = DateRange.of(LocalDate(2024, 1, 1), LocalDate(2024, 1, 7))
    private val zone = TimeZone.UTC

    @Test
    fun `food diary preset selects meals, check-ins and summaries`() {
        val expected = setOf(
            HealthReportSection.MEALS_AND_NUTRITION,
            HealthReportSection.CHECK_INS,
            HealthReportSection.DAILY_AND_WEEKLY_SUMMARIES
        )
        assertEquals(expected, HealthReportPreset.FOOD_DIARY.defaultSections())
    }

    @Test
    fun `complete health record preset selects every section`() {
        assertEquals(
            HealthReportSection.entries.toSet(),
            HealthReportPreset.COMPLETE_HEALTH_RECORD.defaultSections()
        )
    }

    @Test
    fun `custom preset initializes to no sections`() {
        assertTrue(HealthReportPreset.CUSTOM.defaultSections().isEmpty())
    }

    @Test
    fun `request carries the explicit overridden section set regardless of preset`() {
        val request = HealthReportRequest(
            range = range,
            timeZone = zone,
            preset = HealthReportPreset.FOOD_DIARY,
            selectedSections = setOf(HealthReportSection.WEIGHT_HISTORY)
        )

        assertEquals(setOf(HealthReportSection.WEIGHT_HISTORY), request.sections)
    }

    @Test
    fun `request with no sections is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            HealthReportRequest(
                range = range,
                timeZone = zone,
                preset = HealthReportPreset.CUSTOM,
                selectedSections = emptySet()
            )
        }
    }

    @Test
    fun `custom request with explicit sections is accepted`() {
        val request = HealthReportRequest(
            range = range,
            timeZone = zone,
            preset = HealthReportPreset.CUSTOM,
            selectedSections = setOf(HealthReportSection.CHECK_INS, HealthReportSection.POLAR_METRICS)
        )

        assertEquals(
            setOf(HealthReportSection.CHECK_INS, HealthReportSection.POLAR_METRICS),
            request.sections
        )
    }
}
