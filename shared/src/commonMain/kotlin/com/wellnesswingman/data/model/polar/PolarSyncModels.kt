package com.wellnesswingman.data.model.polar

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

enum class PolarMetricFamily(val storageValue: String) {
    ACTIVITY("ACTIVITY"),
    SLEEP("SLEEP"),
    TRAINING("TRAINING"),
    NIGHTLY_RECHARGE("NIGHTLY_RECHARGE"),
    USER_PROFILE("USER_PROFILE");

    companion object {
        fun fromStorage(value: String): PolarMetricFamily =
            entries.firstOrNull { it.storageValue == value } ?: ACTIVITY
    }
}

data class PolarSyncCheckpoint(
    val metricFamily: PolarMetricFamily,
    val lastSyncCursor: String,
    val lastSuccessfulSyncAt: Instant,
    val lastFailureMessage: String? = null
)

data class StoredPolarActivity(
    val recordId: Long,
    val externalId: String,
    val source: String,
    val localDate: LocalDate,
    val startedAt: String?,
    val syncedAt: Instant,
    val data: PolarDailyActivity
)

data class StoredPolarSleepResult(
    val recordId: Long,
    val externalId: String,
    val source: String,
    val localDate: LocalDate,
    val startedAt: String?,
    val endedAt: String?,
    val syncedAt: Instant,
    val data: PolarSleepResult
)

data class StoredPolarTrainingSession(
    val recordId: Long,
    val externalId: String,
    val source: String,
    val localDate: LocalDate,
    val startedAt: String?,
    val syncedAt: Instant,
    val data: PolarTrainingSession
)

data class StoredPolarNightlyRecharge(
    val recordId: Long,
    val externalId: String,
    val source: String,
    val localDate: LocalDate,
    val syncedAt: Instant,
    val data: PolarNightlyRecharge
)

data class StoredPolarUserProfile(
    val recordId: Long,
    val externalId: String,
    val source: String,
    val localDate: LocalDate,
    val syncedAt: Instant,
    val data: PolarUserProfile
)
