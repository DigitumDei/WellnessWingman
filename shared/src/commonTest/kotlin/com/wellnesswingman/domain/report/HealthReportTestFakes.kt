package com.wellnesswingman.domain.report

import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.DailyCheckIn
import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeeklySummary
import com.wellnesswingman.data.model.WeightRecord
import com.wellnesswingman.data.model.polar.PolarDailyActivity
import com.wellnesswingman.data.model.polar.PolarMetricFamily
import com.wellnesswingman.data.model.polar.PolarNightlyRecharge
import com.wellnesswingman.data.model.polar.PolarSleepResult
import com.wellnesswingman.data.model.polar.PolarSyncCheckpoint
import com.wellnesswingman.data.model.polar.PolarTrainingSession
import com.wellnesswingman.data.model.polar.PolarUserProfile
import com.wellnesswingman.data.model.polar.StoredPolarActivity
import com.wellnesswingman.data.model.polar.StoredPolarNightlyRecharge
import com.wellnesswingman.data.model.polar.StoredPolarSleepResult
import com.wellnesswingman.data.model.polar.StoredPolarTrainingSession
import com.wellnesswingman.data.model.polar.StoredPolarUserProfile
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.CheckInAnalysisRepository
import com.wellnesswingman.data.repository.DailyCheckInRepository
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.data.repository.PolarSyncRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeeklySummaryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

internal class FakeTrackedEntryRepository : TrackedEntryRepository {
    var entries: List<TrackedEntry> = emptyList()
    var rangeCalls: Int = 0

    override suspend fun getAllEntries(): List<TrackedEntry> = entries
    override suspend fun getRecentEntries(limit: Int, entryType: EntryType?): List<TrackedEntry> = emptyList()
    override fun observeAllEntries(): Flow<List<TrackedEntry>> = flowOf(entries)
    override suspend fun getEntryById(id: Long): TrackedEntry? = null
    override suspend fun getEntryByExternalId(externalId: String): TrackedEntry? = null
    override suspend fun getEntryByBlobPath(blobPath: String): TrackedEntry? = null
    override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long): List<TrackedEntry> = emptyList()
    override suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry> = emptyList()
    override fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>> = flowOf(emptyList())
    override suspend fun getEntriesInRange(startInclusive: Instant, endExclusive: Instant): List<TrackedEntry> {
        rangeCalls += 1
        return entries.filter {
            it.capturedAt >= startInclusive && it.capturedAt < endExclusive
        }.sortedWith(compareBy({ it.capturedAt }, { it.entryId }))
    }
    override suspend fun getEntriesByStatus(status: ProcessingStatus): List<TrackedEntry> = emptyList()
    override suspend fun getPendingEntries(): List<TrackedEntry> = emptyList()
    override suspend fun insertEntry(entry: TrackedEntry): Long = 0L
    override suspend fun updateEntryStatus(id: Long, status: ProcessingStatus) {}
    override suspend fun updateEntryType(id: Long, entryType: EntryType) {}
    override suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int) {}
    override suspend fun updateUserNotes(id: Long, notes: String?) {}
    override suspend fun deleteEntry(id: Long) {}
    override suspend fun upsertEntry(entry: TrackedEntry) {}
}

internal class FakeEntryAnalysisRepository : EntryAnalysisRepository {
    var analyses: Map<Long, List<EntryAnalysis>> = emptyMap()
    var parentEntries: List<TrackedEntry> = emptyList()
    var batchCalls: Int = 0

    override suspend fun getAllAnalyses(): List<EntryAnalysis> = analyses.values.flatten()
    override suspend fun getAnalysisById(id: Long): EntryAnalysis? = null
    override suspend fun getAnalysisByExternalId(externalId: String): EntryAnalysis? = null
    override suspend fun getAnalysesForEntry(entryId: Long): List<EntryAnalysis> = analyses[entryId].orEmpty()
    override suspend fun getLatestAnalysisForEntry(entryId: Long): EntryAnalysis? =
        analyses[entryId]?.maxWithOrNull(compareBy<EntryAnalysis> { it.capturedAt }.thenBy { it.analysisId })

    override suspend fun getLatestAnalysesForEntries(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<EntryAnalysis> {
        batchCalls += 1
        val inRangeParentIds = parentEntries
            .filter { it.capturedAt >= startInclusive && it.capturedAt < endExclusive }
            .map { it.entryId }
            .toSet()
        return inRangeParentIds.mapNotNull { entryId ->
            analyses[entryId]?.maxWithOrNull(
                compareBy<EntryAnalysis> { it.capturedAt }.thenBy { it.analysisId }
            )
        }.sortedBy { it.entryId }
    }

    override suspend fun insertAnalysis(analysis: EntryAnalysis): Long = 0L
    override suspend fun updateAnalysis(id: Long, insightsJson: String, schemaVersion: String) {}
    override suspend fun deleteAnalysis(id: Long) {}
    override suspend fun deleteAnalysesForEntry(entryId: Long) {}
    override suspend fun upsertAnalysis(analysis: EntryAnalysis) {}
}

internal class FakeDailySummaryRepository : DailySummaryRepository {
    var summaries: List<DailySummary> = emptyList()
    var rangeCalls: Int = 0

    override suspend fun getAllSummaries(): List<DailySummary> = summaries
    override suspend fun getSummaryById(id: Long): DailySummary? = null
    override suspend fun getSummaryByExternalId(externalId: String): DailySummary? = null
    override suspend fun getSummaryForDate(date: LocalDate): DailySummary? = null
    override suspend fun getSummariesForDateRange(startDate: LocalDate, endDate: LocalDate): List<DailySummary> {
        rangeCalls += 1
        return summaries.filter { it.summaryDate >= startDate && it.summaryDate <= endDate }
    }
    override suspend fun getRecentSummaries(limit: Long): List<DailySummary> = emptyList()
    override suspend fun insertSummary(summary: DailySummary): Long = 0L
    override suspend fun updateSummary(id: Long, highlights: String, recommendations: String) {}
    override suspend fun updateSummaryByDate(date: LocalDate, highlights: String, recommendations: String) {}
    override suspend fun updateUserComments(date: LocalDate, comments: String?) {}
    override suspend fun deleteSummary(id: Long) {}
    override suspend fun deleteSummaryByDate(date: LocalDate) {}
    override suspend fun deleteOldSummaries(beforeDate: LocalDate) {}
    override suspend fun upsertSummary(summary: DailySummary) {}
}

internal class FakeWeeklySummaryRepository : WeeklySummaryRepository {
    var summaries: List<WeeklySummary> = emptyList()
    var rangeCalls: Int = 0

    override suspend fun getAllSummaries(): List<WeeklySummary> = summaries
    override suspend fun getSummaryById(id: Long): WeeklySummary? = null
    override suspend fun getSummaryForWeek(weekStart: LocalDate): WeeklySummary? = null
    override suspend fun getSummariesForDateRange(startDate: LocalDate, endDate: LocalDate): List<WeeklySummary> {
        rangeCalls += 1
        return summaries.filter { it.weekStartDate >= startDate && it.weekStartDate <= endDate }
    }
    override suspend fun getRecentSummaries(limit: Long): List<WeeklySummary> = emptyList()
    override suspend fun insertSummary(summary: WeeklySummary): Long = 0L
    override suspend fun updateSummary(summary: WeeklySummary) {}
    override suspend fun updateSummaryByWeek(weekStart: LocalDate, summary: WeeklySummary) {}
    override suspend fun updateUserComments(weekStart: LocalDate, comments: String?) {}
    override suspend fun deleteSummary(id: Long) {}
    override suspend fun deleteSummaryByWeek(weekStart: LocalDate) {}
    override suspend fun deleteOldSummaries(beforeDate: LocalDate) {}
}

internal class FakeWeightHistoryRepository : WeightHistoryRepository {
    var records: List<WeightRecord> = emptyList()
    var rangeCalls: Int = 0

    override suspend fun addWeightRecord(record: WeightRecord): Long = 0L
    override suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<WeightRecord> {
        rangeCalls += 1
        return records.filter { it.recordedAt >= startDate && it.recordedAt < endDate }
    }
    override suspend fun getLatestWeightRecord(): WeightRecord? = null
    override suspend fun getAllWeightRecords(): List<WeightRecord> = records
    override suspend fun deleteWeightRecord(recordId: Long) {}
    override suspend fun nullifyRelatedEntryId(entryId: Long) {}
    override suspend fun upsertWeightRecord(record: WeightRecord) {}
}

internal class FakeDailyCheckInRepository : DailyCheckInRepository {
    var checkIns: List<DailyCheckIn> = emptyList()
    var rangeCalls: Int = 0

    override suspend fun getAllCheckIns(): List<DailyCheckIn> = checkIns
    override suspend fun getCheckInsForDate(date: LocalDate): List<DailyCheckIn> = checkIns.filter { it.checkInDate == date }
    override suspend fun getCheckIn(date: LocalDate, slot: com.wellnesswingman.data.model.CheckInSlot): DailyCheckIn? = null
    override suspend fun getCheckInsForDateRange(startDate: LocalDate, endDate: LocalDate): List<DailyCheckIn> {
        rangeCalls += 1
        return checkIns.filter { it.checkInDate >= startDate && it.checkInDate <= endDate }
    }
    override suspend fun getCheckInByExternalId(externalId: String): DailyCheckIn? = null
    override suspend fun saveCheckIn(checkIn: DailyCheckIn): Long = 0L
    override suspend fun attachConversation(date: LocalDate, slot: com.wellnesswingman.data.model.CheckInSlot, conversationExternalId: String) {}
    override suspend fun deleteCheckIn(date: LocalDate, slot: com.wellnesswingman.data.model.CheckInSlot) {}
    override suspend fun deleteOldCheckIns(beforeDate: LocalDate) {}
    override suspend fun upsertCheckIn(checkIn: DailyCheckIn) {}
}

internal class FakeCheckInAnalysisRepository : CheckInAnalysisRepository {
    var analyses: List<CheckInAnalysis> = emptyList()
    var rangeCalls: Int = 0

    override suspend fun getAllAnalyses(): List<CheckInAnalysis> = analyses
    override suspend fun getAnalysis(date: LocalDate, slot: com.wellnesswingman.data.model.CheckInSlot): CheckInAnalysis? = null
    override suspend fun getAnalysesForDate(date: LocalDate): List<CheckInAnalysis> = analyses.filter { it.checkInDate == date }
    override suspend fun getAnalysesForDateRange(startDate: LocalDate, endDate: LocalDate): List<CheckInAnalysis> {
        rangeCalls += 1
        return analyses.filter { it.checkInDate >= startDate && it.checkInDate <= endDate }
    }
    override suspend fun getAnalysisByExternalId(externalId: String): CheckInAnalysis? = null
    override suspend fun saveAnalysis(analysis: CheckInAnalysis): Long = 0L
    override suspend fun deleteAnalysis(date: LocalDate, slot: com.wellnesswingman.data.model.CheckInSlot) {}
    override suspend fun deleteOldAnalyses(beforeDate: LocalDate) {}
    override suspend fun upsertAnalysis(analysis: CheckInAnalysis) {}
}

internal class FakePolarSyncRepository : PolarSyncRepository {
    var activities: List<StoredPolarActivity> = emptyList()
    var sleeps: List<StoredPolarSleepResult> = emptyList()
    var trainings: List<StoredPolarTrainingSession> = emptyList()
    var recharges: List<StoredPolarNightlyRecharge> = emptyList()
    var activityCalls: Int = 0

    override suspend fun upsertActivities(activities: List<PolarDailyActivity>, syncedAt: Instant): Int = 0
    override suspend fun getActivities(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarActivity> {
        activityCalls += 1
        return activities.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
    }
    override suspend fun upsertSleepResults(results: List<PolarSleepResult>, syncedAt: Instant): Int = 0
    override suspend fun getSleepResults(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarSleepResult> =
        sleeps.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
    override suspend fun upsertTrainingSessions(sessions: List<PolarTrainingSession>, syncedAt: Instant): Int = 0
    override suspend fun getTrainingSessions(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarTrainingSession> =
        trainings.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
    override suspend fun upsertNightlyRecharge(results: List<PolarNightlyRecharge>, syncedAt: Instant): Int = 0
    override suspend fun getNightlyRecharge(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarNightlyRecharge> =
        recharges.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
    override suspend fun upsertUserProfile(userId: String, profile: PolarUserProfile, syncedAt: Instant) {}
    override suspend fun getUserProfile(userId: String): StoredPolarUserProfile? = null
    override suspend fun getCheckpoint(metricFamily: PolarMetricFamily): PolarSyncCheckpoint? = null
    override suspend fun getAllCheckpoints(): List<PolarSyncCheckpoint> = emptyList()
    override suspend fun updateCheckpoint(checkpoint: PolarSyncCheckpoint) {}
    override suspend fun clearCheckpoint(metricFamily: PolarMetricFamily) {}
    override suspend fun clearAll() {}
}

internal class FakeAppSettingsRepository : AppSettingsRepository {
    private var _height: Double? = null
    private var _heightUnit: String = "cm"
    private var _sex: String? = null
    private var _currentWeight: Double? = null
    private var _weightUnit: String = "kg"
    private var _dateOfBirth: String? = null
    private var _activityLevel: String? = null
    private var _goalsAndPreferences: String? = null

    override fun getApiKey(provider: LlmProvider): String? = null
    override fun setApiKey(provider: LlmProvider, apiKey: String) {}
    override fun removeApiKey(provider: LlmProvider) {}
    override fun getSelectedProvider(): LlmProvider = LlmProvider.OPENAI
    override fun setSelectedProvider(provider: LlmProvider) {}
    override fun getModel(provider: LlmProvider): String? = null
    override fun setModel(provider: LlmProvider, model: String) {}
    override fun clear() {}
    override fun getHeight(): Double? = _height
    override fun setHeight(height: Double) { _height = height }
    override fun getHeightUnit(): String = _heightUnit
    override fun setHeightUnit(unit: String) { _heightUnit = unit }
    override fun getSex(): String? = _sex
    override fun setSex(sex: String) { _sex = sex }
    override fun getCurrentWeight(): Double? = _currentWeight
    override fun setCurrentWeight(weight: Double) { _currentWeight = weight }
    override fun getWeightUnit(): String = _weightUnit
    override fun setWeightUnit(unit: String) { _weightUnit = unit }
    override fun getDateOfBirth(): String? = _dateOfBirth
    override fun setDateOfBirth(dob: String) { _dateOfBirth = dob }
    override fun getActivityLevel(): String? = _activityLevel
    override fun setActivityLevel(level: String) { _activityLevel = level }
    override fun getGoalsAndPreferences(): String? = _goalsAndPreferences
    override fun setGoalsAndPreferences(text: String) { _goalsAndPreferences = text }
    override fun clearHeight() {}
    override fun clearCurrentWeight() {}
    override fun clearProfileData() {}
    override fun getImageRetentionThresholdDays(): Int = 7
    override fun setImageRetentionThresholdDays(days: Int) {}
    override fun isMorningCheckInEnabled(): Boolean = false
    override fun setMorningCheckInEnabled(enabled: Boolean) {}
    override fun getMorningCheckInTime(): String = "07:00"
    override fun setMorningCheckInTime(time: String) {}
    override fun isEveningCheckInEnabled(): Boolean = false
    override fun setEveningCheckInEnabled(enabled: Boolean) {}
    override fun getEveningCheckInTime(): String = "21:00"
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
