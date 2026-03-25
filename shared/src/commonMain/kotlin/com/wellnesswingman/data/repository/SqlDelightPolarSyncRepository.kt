package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.polar.*
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SqlDelightPolarSyncRepository(
    private val database: WellnessWingmanDatabase
) : PolarSyncRepository {

    private val queries = database.polarMetricRecordQueries
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun upsertActivities(activities: List<PolarDailyActivity>, syncedAt: Instant): Int =
        withContext(Dispatchers.IO) {
            activities.forEach { activity ->
                queries.upsertMetricRecord(
                    metricFamily = PolarMetricFamily.ACTIVITY.storageValue,
                    externalId = "activity:${activity.date}",
                    source = POLAR_SOURCE,
                    localDate = activity.date,
                    startedAt = "${activity.date}T${activity.stepSampleStartTime}",
                    endedAt = null,
                    payloadJson = json.encodeToString(activity),
                    syncedAt = syncedAt.toEpochMilliseconds()
                )
            }
            activities.size
        }

    override suspend fun getActivities(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarActivity> =
        withContext(Dispatchers.IO) {
            queries.getRecordsForFamilyInDateRange(
                metricFamily = PolarMetricFamily.ACTIVITY.storageValue,
                localDate = startDate.toString(),
                localDate_ = endDateExclusive.toString()
            ).executeAsList().map { record ->
                StoredPolarActivity(
                    recordId = record.polarMetricRecordId,
                    externalId = record.externalId,
                    source = record.source,
                    localDate = LocalDate.parse(record.localDate),
                    startedAt = record.startedAt,
                    syncedAt = Instant.fromEpochMilliseconds(record.syncedAt),
                    data = json.decodeFromString<PolarDailyActivity>(record.payloadJson)
                )
            }
        }

    override suspend fun upsertSleepResults(results: List<PolarSleepResult>, syncedAt: Instant): Int =
        withContext(Dispatchers.IO) {
            results.forEach { sleep ->
                queries.upsertMetricRecord(
                    metricFamily = PolarMetricFamily.SLEEP.storageValue,
                    externalId = "sleep:${sleep.date}",
                    source = POLAR_SOURCE,
                    localDate = sleep.date,
                    startedAt = sleep.sleepStart.ifBlank { null },
                    endedAt = sleep.sleepEnd.ifBlank { null },
                    payloadJson = json.encodeToString(sleep),
                    syncedAt = syncedAt.toEpochMilliseconds()
                )
            }
            results.size
        }

    override suspend fun getSleepResults(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarSleepResult> =
        withContext(Dispatchers.IO) {
            queries.getRecordsForFamilyInDateRange(
                metricFamily = PolarMetricFamily.SLEEP.storageValue,
                localDate = startDate.toString(),
                localDate_ = endDateExclusive.toString()
            ).executeAsList().map { record ->
                StoredPolarSleepResult(
                    recordId = record.polarMetricRecordId,
                    externalId = record.externalId,
                    source = record.source,
                    localDate = LocalDate.parse(record.localDate),
                    startedAt = record.startedAt,
                    endedAt = record.endedAt,
                    syncedAt = Instant.fromEpochMilliseconds(record.syncedAt),
                    data = json.decodeFromString<PolarSleepResult>(record.payloadJson)
                )
            }
        }

    override suspend fun upsertTrainingSessions(sessions: List<PolarTrainingSession>, syncedAt: Instant): Int =
        withContext(Dispatchers.IO) {
            sessions.forEach { session ->
                queries.upsertMetricRecord(
                    metricFamily = PolarMetricFamily.TRAINING.storageValue,
                    externalId = session.id,
                    source = POLAR_SOURCE,
                    localDate = session.startTime.take(10),
                    startedAt = session.startTime.ifBlank { null },
                    endedAt = null,
                    payloadJson = json.encodeToString(session),
                    syncedAt = syncedAt.toEpochMilliseconds()
                )
            }
            sessions.size
        }

    override suspend fun getTrainingSessions(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarTrainingSession> =
        withContext(Dispatchers.IO) {
            queries.getRecordsForFamilyInDateRange(
                metricFamily = PolarMetricFamily.TRAINING.storageValue,
                localDate = startDate.toString(),
                localDate_ = endDateExclusive.toString()
            ).executeAsList().map { record ->
                StoredPolarTrainingSession(
                    recordId = record.polarMetricRecordId,
                    externalId = record.externalId,
                    source = record.source,
                    localDate = LocalDate.parse(record.localDate),
                    startedAt = record.startedAt,
                    syncedAt = Instant.fromEpochMilliseconds(record.syncedAt),
                    data = json.decodeFromString<PolarTrainingSession>(record.payloadJson)
                )
            }
        }

    override suspend fun upsertNightlyRecharge(results: List<PolarNightlyRecharge>, syncedAt: Instant): Int =
        withContext(Dispatchers.IO) {
            results.forEach { recharge ->
                queries.upsertMetricRecord(
                    metricFamily = PolarMetricFamily.NIGHTLY_RECHARGE.storageValue,
                    externalId = "nightly-recharge:${recharge.date}",
                    source = POLAR_SOURCE,
                    localDate = recharge.date,
                    startedAt = null,
                    endedAt = null,
                    payloadJson = json.encodeToString(recharge),
                    syncedAt = syncedAt.toEpochMilliseconds()
                )
            }
            results.size
        }

    override suspend fun getNightlyRecharge(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarNightlyRecharge> =
        withContext(Dispatchers.IO) {
            queries.getRecordsForFamilyInDateRange(
                metricFamily = PolarMetricFamily.NIGHTLY_RECHARGE.storageValue,
                localDate = startDate.toString(),
                localDate_ = endDateExclusive.toString()
            ).executeAsList().map { record ->
                StoredPolarNightlyRecharge(
                    recordId = record.polarMetricRecordId,
                    externalId = record.externalId,
                    source = record.source,
                    localDate = LocalDate.parse(record.localDate),
                    syncedAt = Instant.fromEpochMilliseconds(record.syncedAt),
                    data = json.decodeFromString<PolarNightlyRecharge>(record.payloadJson)
                )
            }
        }

    override suspend fun upsertUserProfile(userId: String, profile: PolarUserProfile, syncedAt: Instant) =
        withContext(Dispatchers.IO) {
            queries.upsertMetricRecord(
                metricFamily = PolarMetricFamily.USER_PROFILE.storageValue,
                externalId = "profile:$userId",
                source = POLAR_SOURCE,
                localDate = syncedAt.toString().take(10),
                startedAt = null,
                endedAt = null,
                payloadJson = json.encodeToString(profile),
                syncedAt = syncedAt.toEpochMilliseconds()
            )
        }

    override suspend fun getUserProfile(userId: String): StoredPolarUserProfile? = withContext(Dispatchers.IO) {
        queries.getRecordForFamilyAndExternalId(
            metricFamily = PolarMetricFamily.USER_PROFILE.storageValue,
            externalId = "profile:$userId"
        )
            .executeAsOneOrNull()
            ?.let { record ->
                StoredPolarUserProfile(
                    recordId = record.polarMetricRecordId,
                    externalId = record.externalId,
                    source = record.source,
                    localDate = LocalDate.parse(record.localDate),
                    syncedAt = Instant.fromEpochMilliseconds(record.syncedAt),
                    data = json.decodeFromString<PolarUserProfile>(record.payloadJson)
                )
            }
    }

    override suspend fun getCheckpoint(metricFamily: PolarMetricFamily): PolarSyncCheckpoint? = withContext(Dispatchers.IO) {
        queries.getCheckpoint(metricFamily.storageValue).executeAsOneOrNull()?.toCheckpoint()
    }

    override suspend fun getAllCheckpoints(): List<PolarSyncCheckpoint> = withContext(Dispatchers.IO) {
        queries.getAllCheckpoints().executeAsList().map { it.toCheckpoint() }
    }

    override suspend fun updateCheckpoint(checkpoint: PolarSyncCheckpoint) = withContext(Dispatchers.IO) {
        queries.upsertCheckpoint(
            metricFamily = checkpoint.metricFamily.storageValue,
            lastSyncCursor = checkpoint.lastSyncCursor,
            lastSuccessfulSyncAt = checkpoint.lastSuccessfulSyncAt.toEpochMilliseconds(),
            lastFailureMessage = checkpoint.lastFailureMessage
        )
    }

    override suspend fun clearCheckpoint(metricFamily: PolarMetricFamily) = withContext(Dispatchers.IO) {
        queries.deleteCheckpoint(metricFamily.storageValue)
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        database.transaction {
            queries.clearPolarSyncData()
            queries.clearPolarSyncCheckpoints()
        }
    }

    private fun com.wellnesswingman.db.PolarSyncCheckpoint.toCheckpoint(): PolarSyncCheckpoint =
        PolarSyncCheckpoint(
            metricFamily = PolarMetricFamily.fromStorage(metricFamily),
            lastSyncCursor = lastSyncCursor,
            lastSuccessfulSyncAt = Instant.fromEpochMilliseconds(lastSuccessfulSyncAt),
            lastFailureMessage = lastFailureMessage
        )

    private companion object {
        const val POLAR_SOURCE = "Polar"
    }
}
