package com.wellnesswingman.domain.checkin

import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.CheckInInputSource
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn
import com.wellnesswingman.data.model.analysis.CheckInAnalysisStatus
import com.wellnesswingman.data.model.analysis.CheckInFacets
import com.wellnesswingman.data.model.analysis.MentionedFood
import com.wellnesswingman.data.model.analysis.NutritionEstimate
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.CheckInAnalysisRepository
import com.wellnesswingman.data.repository.DailyCheckInRepository
import com.wellnesswingman.data.repository.LlmProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.*

/**
 * Covers the seam both day screens read through.
 *
 * It exists so the Today screen and the calendar day view cannot present check-ins differently,
 * which makes its failure behaviour worth pinning: a screen must still render its entries when
 * check-ins or their extractions cannot be read.
 */
class DayCheckInsProviderTest {

    private val date = LocalDate(2026, 8, 12)
    private val capturedAt = Instant.fromEpochMilliseconds(1_785_000_000_000)

    /** Fixed at 09:00 UTC on the test date, so the morning slot is open and the evening is not. */
    private val clock = object : Clock {
        override fun now(): Instant = LocalDate(2026, 8, 12)
            .atTime(9, 0)
            .toInstant(TimeZone.UTC)
    }

    private class FakeDailyCheckInRepository(
        private val checkIns: List<DailyCheckIn> = emptyList(),
        private val failing: Boolean = false
    ) : DailyCheckInRepository {
        override suspend fun getCheckInsForDate(date: LocalDate): List<DailyCheckIn> {
            if (failing) error("database unavailable")
            return checkIns.filter { it.checkInDate == date }
        }

        override suspend fun getAllCheckIns(): List<DailyCheckIn> = checkIns
        override suspend fun getCheckIn(date: LocalDate, slot: CheckInSlot): DailyCheckIn? =
            checkIns.firstOrNull { it.checkInDate == date && it.slot == slot }
        override suspend fun getCheckInsForDateRange(startDate: LocalDate, endDate: LocalDate) = checkIns
        override suspend fun getCheckInByExternalId(externalId: String): DailyCheckIn? = null
        override suspend fun saveCheckIn(checkIn: DailyCheckIn): Long = 1L
        override suspend fun attachConversation(date: LocalDate, slot: CheckInSlot, conversationExternalId: String) = Unit
        override suspend fun deleteCheckIn(date: LocalDate, slot: CheckInSlot) = Unit
        override suspend fun deleteOldCheckIns(beforeDate: LocalDate) = Unit
        override suspend fun upsertCheckIn(checkIn: DailyCheckIn) = Unit
    }

    private class FakeCheckInAnalysisRepository(
        private val analyses: List<CheckInAnalysis> = emptyList(),
        private val failing: Boolean = false
    ) : CheckInAnalysisRepository {
        override suspend fun getAnalysesForDate(date: LocalDate): List<CheckInAnalysis> {
            if (failing) error("analysis table unavailable")
            return analyses.filter { it.checkInDate == date }
        }

        override suspend fun getAllAnalyses(): List<CheckInAnalysis> = analyses
        override suspend fun getAnalysis(date: LocalDate, slot: CheckInSlot): CheckInAnalysis? =
            analyses.firstOrNull { it.checkInDate == date && it.slot == slot }
        override suspend fun getAnalysesForDateRange(startDate: LocalDate, endDate: LocalDate) = analyses
        override suspend fun getAnalysisByExternalId(externalId: String): CheckInAnalysis? = null
        override suspend fun saveAnalysis(analysis: CheckInAnalysis): Long = 1L
        override suspend fun deleteAnalysis(date: LocalDate, slot: CheckInSlot) = Unit
        override suspend fun deleteOldAnalyses(beforeDate: LocalDate) = Unit
        override suspend fun upsertAnalysis(analysis: CheckInAnalysis) = Unit
    }

    private class FakeSettings : AppSettingsRepository {
        override fun isMorningCheckInEnabled(): Boolean = true
        override fun getMorningCheckInTime(): String = "07:00"
        override fun isEveningCheckInEnabled(): Boolean = true
        override fun getEveningCheckInTime(): String = "21:00"

        override fun getApiKey(provider: LlmProvider): String? = null
        override fun setApiKey(provider: LlmProvider, apiKey: String) {}
        override fun removeApiKey(provider: LlmProvider) {}
        override fun getSelectedProvider(): LlmProvider = LlmProvider.GEMINI
        override fun setSelectedProvider(provider: LlmProvider) {}
        override fun getModel(provider: LlmProvider): String? = null
        override fun setModel(provider: LlmProvider, model: String) {}
        override fun clear() {}
        override fun getHeight(): Double? = null
        override fun setHeight(height: Double) {}
        override fun getHeightUnit(): String = "cm"
        override fun setHeightUnit(unit: String) {}
        override fun getSex(): String? = null
        override fun setSex(sex: String) {}
        override fun getCurrentWeight(): Double? = null
        override fun setCurrentWeight(weight: Double) {}
        override fun getWeightUnit(): String = "kg"
        override fun setWeightUnit(unit: String) {}
        override fun getDateOfBirth(): String? = null
        override fun setDateOfBirth(dob: String) {}
        override fun getActivityLevel(): String? = null
        override fun setActivityLevel(level: String) {}
        override fun clearHeight() {}
        override fun clearCurrentWeight() {}
        override fun clearProfileData() {}
        override fun getImageRetentionThresholdDays(): Int = 30
        override fun setImageRetentionThresholdDays(days: Int) {}
        override fun setMorningCheckInEnabled(enabled: Boolean) {}
        override fun setMorningCheckInTime(time: String) {}
        override fun setEveningCheckInEnabled(enabled: Boolean) {}
        override fun setEveningCheckInTime(time: String) {}
        override fun getPolarAccessToken(): String? = null
        override fun setPolarAccessToken(token: String) {}
        override fun getPolarRefreshToken(): String? = null
        override fun setPolarRefreshToken(token: String) {}
        override fun getPolarTokenExpiresAt(): Long = 0L
        override fun setPolarTokenExpiresAt(expiresAt: Long) {}
        override fun getPolarUserId(): String? = null
        override fun setPolarUserId(userId: String) {}
        override fun getPendingOAuthState(): String? = null
        override fun setPendingOAuthState(state: String) {}
        override fun getPendingOAuthSessionId(): String? = null
        override fun setPendingOAuthSessionId(sessionId: String) {}
        override fun clearPendingOAuthSession() {}
        override fun clearPolarTokens() {}
        override fun isPolarConnected(): Boolean = false
    }

    private fun checkIn(slot: CheckInSlot) = DailyCheckIn(
        checkInDate = date,
        slot = slot,
        capturedAt = capturedAt,
        responseText = "slept badly",
        inputSource = CheckInInputSource.TYPED
    )

    private fun analysis(
        slot: CheckInSlot,
        status: CheckInAnalysisStatus = CheckInAnalysisStatus.COMPLETED
    ) = CheckInAnalysis(
        checkInDate = date,
        slot = slot,
        status = status,
        analyzedAt = capturedAt,
        facets = CheckInFacets(
            mentionedFood = listOf(
                MentionedFood(name = "toast", nutrition = NutritionEstimate(totalCalories = 180.0))
            )
        )
    )

    private fun provider(
        checkIns: List<DailyCheckIn> = emptyList(),
        analyses: List<CheckInAnalysis> = emptyList(),
        checkInsFailing: Boolean = false,
        analysesFailing: Boolean = false,
        withAnalysisRepository: Boolean = true
    ) = DayCheckInsProvider(
        dailyCheckInRepository = FakeDailyCheckInRepository(checkIns, checkInsFailing),
        appSettingsRepository = FakeSettings(),
        checkInAnalysisRepository = if (withAnalysisRepository) {
            FakeCheckInAnalysisRepository(analyses, analysesFailing)
        } else null,
        clock = clock,
        timeZoneProvider = { TimeZone.UTC }
    )

    @Test
    fun `an answered slot carries its analysis`() = runTest {
        val slots = provider(
            checkIns = listOf(checkIn(CheckInSlot.MORNING)),
            analyses = listOf(analysis(CheckInSlot.MORNING))
        ).slotsFor(date)

        val morning = slots.single { it.slot == CheckInSlot.MORNING }
        assertTrue(morning.isAnswered)
        assertNotNull(morning.facets)
        assertEquals("toast", morning.facets!!.mentionedFood.single().name)
    }

    @Test
    fun `a pending analysis exposes no facets but reports pending`() = runTest {
        val slots = provider(
            checkIns = listOf(checkIn(CheckInSlot.MORNING)),
            analyses = listOf(analysis(CheckInSlot.MORNING, CheckInAnalysisStatus.PENDING))
        ).slotsFor(date)

        val morning = slots.single { it.slot == CheckInSlot.MORNING }
        assertTrue(morning.isAnalysisPending)
        assertNull(morning.facets)
    }

    @Test
    fun `a failed analysis is reported so the screen can offer a retry`() = runTest {
        val slots = provider(
            checkIns = listOf(checkIn(CheckInSlot.EVENING)),
            analyses = listOf(analysis(CheckInSlot.EVENING, CheckInAnalysisStatus.FAILED))
        ).slotsFor(date)

        assertTrue(slots.single { it.slot == CheckInSlot.EVENING }.hasAnalysisFailed)
    }

    @Test
    fun `an answered slot with no analysis simply carries none`() = runTest {
        val slots = provider(checkIns = listOf(checkIn(CheckInSlot.MORNING))).slotsFor(date)

        val morning = slots.single { it.slot == CheckInSlot.MORNING }
        assertTrue(morning.isAnswered)
        assertNull(morning.analysis)
        assertFalse(morning.isAnalysisPending)
    }

    @Test
    fun `slots still load when the analysis table cannot be read`() = runTest {
        val slots = provider(
            checkIns = listOf(checkIn(CheckInSlot.MORNING)),
            analysesFailing = true
        ).slotsFor(date)

        // The answer is the thing worth showing; losing the slot because its derived data
        // failed to load would be the worse outcome.
        assertTrue(slots.single { it.slot == CheckInSlot.MORNING }.isAnswered)
        assertNull(slots.single { it.slot == CheckInSlot.MORNING }.analysis)
    }

    @Test
    fun `an absent analysis repository is not an error`() = runTest {
        val slots = provider(
            checkIns = listOf(checkIn(CheckInSlot.MORNING)),
            withAnalysisRepository = false
        ).slotsFor(date)

        assertTrue(slots.single { it.slot == CheckInSlot.MORNING }.isAnswered)
    }

    @Test
    fun `a failure reading check-ins degrades to no slots`() = runTest {
        // A screen should still render its entries when check-ins cannot be read.
        assertTrue(provider(checkInsFailing = true).slotsFor(date).isEmpty())
    }
}
