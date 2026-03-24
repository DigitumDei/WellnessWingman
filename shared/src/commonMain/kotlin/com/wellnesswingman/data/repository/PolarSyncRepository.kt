package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.polar.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Polar sync data is stored separately from screenshot/manual tracking tables.
 * This keeps imported wearable data idempotent and avoids mixing Polar records
 * into the TrackedEntry pipeline until a feature explicitly bridges them.
 */
interface PolarSyncRepository {
    suspend fun upsertActivities(activities: List<PolarDailyActivity>, syncedAt: Instant): Int
    suspend fun getActivities(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarActivity>

    suspend fun upsertSleepResults(results: List<PolarSleepResult>, syncedAt: Instant): Int
    suspend fun getSleepResults(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarSleepResult>

    suspend fun upsertTrainingSessions(sessions: List<PolarTrainingSession>, syncedAt: Instant): Int
    suspend fun getTrainingSessions(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarTrainingSession>

    suspend fun upsertNightlyRecharge(results: List<PolarNightlyRecharge>, syncedAt: Instant): Int
    suspend fun getNightlyRecharge(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarNightlyRecharge>

    suspend fun upsertUserProfile(userId: String, profile: PolarUserProfile, syncedAt: Instant)
    suspend fun getUserProfile(userId: String): StoredPolarUserProfile?

    suspend fun getCheckpoint(metricFamily: PolarMetricFamily): PolarSyncCheckpoint?
    suspend fun getAllCheckpoints(): List<PolarSyncCheckpoint>
    suspend fun updateCheckpoint(checkpoint: PolarSyncCheckpoint)
    suspend fun clearCheckpoint(metricFamily: PolarMetricFamily)

    suspend fun clearAll()
}
