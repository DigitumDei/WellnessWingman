package com.wellnesswingman.domain.testutil

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
import com.wellnesswingman.data.repository.PolarSyncRepository
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

class FakePolarSyncRepository(
    private val activities: List<StoredPolarActivity> = emptyList(),
    private val sleepResults: List<StoredPolarSleepResult> = emptyList(),
    private val trainingSessions: List<StoredPolarTrainingSession> = emptyList(),
    private val nightlyRecharge: List<StoredPolarNightlyRecharge> = emptyList()
) : PolarSyncRepository {
    override suspend fun upsertActivities(activities: List<PolarDailyActivity>, syncedAt: Instant) = 0
    override suspend fun getActivities(startDate: LocalDate, endDateExclusive: LocalDate) =
        activities.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
    override suspend fun upsertSleepResults(results: List<PolarSleepResult>, syncedAt: Instant) = 0
    override suspend fun getSleepResults(startDate: LocalDate, endDateExclusive: LocalDate) =
        sleepResults.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
    override suspend fun upsertTrainingSessions(sessions: List<PolarTrainingSession>, syncedAt: Instant) = 0
    override suspend fun getTrainingSessions(startDate: LocalDate, endDateExclusive: LocalDate) =
        trainingSessions.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
    override suspend fun upsertNightlyRecharge(results: List<PolarNightlyRecharge>, syncedAt: Instant) = 0
    override suspend fun getNightlyRecharge(startDate: LocalDate, endDateExclusive: LocalDate) =
        nightlyRecharge.filter { it.localDate >= startDate && it.localDate < endDateExclusive }
    override suspend fun upsertUserProfile(userId: String, profile: PolarUserProfile, syncedAt: Instant) = Unit
    override suspend fun getUserProfile(userId: String): StoredPolarUserProfile? = null
    override suspend fun getCheckpoint(metricFamily: PolarMetricFamily): PolarSyncCheckpoint? = null
    override suspend fun getAllCheckpoints(): List<PolarSyncCheckpoint> = emptyList()
    override suspend fun updateCheckpoint(checkpoint: PolarSyncCheckpoint) = Unit
    override suspend fun clearCheckpoint(metricFamily: PolarMetricFamily) = Unit
    override suspend fun clearAll() = Unit
}
