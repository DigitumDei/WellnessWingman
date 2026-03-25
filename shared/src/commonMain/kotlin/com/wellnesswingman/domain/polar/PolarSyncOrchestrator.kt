package com.wellnesswingman.domain.polar

import com.wellnesswingman.data.model.polar.PolarApiError
import com.wellnesswingman.data.model.polar.PolarMetricFamily
import com.wellnesswingman.data.model.polar.PolarSyncCheckpoint
import com.wellnesswingman.data.model.polar.PolarUserProfile
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.PolarApiClient
import com.wellnesswingman.data.repository.PolarOAuthRepository
import com.wellnesswingman.data.repository.PolarSyncRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

enum class PolarSyncTrigger {
    INITIAL_LINK,
    MANUAL_REFRESH,
    APP_ENTRY,
    ANDROID_BACKGROUND
}

enum class PolarSyncOutcome {
    SUCCESS,
    PARTIAL_FAILURE,
    SKIPPED
}

data class PolarMetricSyncResult(
    val metricFamily: PolarMetricFamily,
    val importedCount: Int,
    val checkpointCursor: String? = null,
    val failureMessage: String? = null
)

data class PolarSyncRunResult(
    val trigger: PolarSyncTrigger,
    val outcome: PolarSyncOutcome,
    val startedAt: Instant,
    val completedAt: Instant,
    val metricResults: List<PolarMetricSyncResult>,
    val message: String
)

data class PolarSyncStatus(
    val isRunning: Boolean = false,
    val activeTrigger: PolarSyncTrigger? = null,
    val lastResult: PolarSyncRunResult? = null
)

class PolarSyncOrchestrator(
    private val appSettingsRepository: AppSettingsRepository,
    private val polarOAuthRepository: PolarOAuthRepository,
    private val polarApiClient: PolarApiClient,
    private val polarSyncRepository: PolarSyncRepository
) {
    private val syncMutex = Mutex()
    private val _status = MutableStateFlow(PolarSyncStatus())
    val status: StateFlow<PolarSyncStatus> = _status.asStateFlow()

    suspend fun syncIfStale(
        trigger: PolarSyncTrigger,
        maxAge: Duration = DEFAULT_AUTOMATIC_SYNC_MAX_AGE
    ): PolarSyncRunResult = syncMutex.withLock {
        syncLocked(trigger = trigger, maxAge = maxAge)
    }

    suspend fun sync(trigger: PolarSyncTrigger): PolarSyncRunResult = syncMutex.withLock {
        syncLocked(trigger = trigger, maxAge = null)
    }

    private suspend fun syncLocked(
        trigger: PolarSyncTrigger,
        maxAge: Duration?
    ): PolarSyncRunResult {
        val startedAt = Clock.System.now()
        if (maxAge != null && shouldSkipStaleSync(trigger, maxAge, startedAt)) {
            return PolarSyncRunResult(
                trigger = trigger,
                outcome = PolarSyncOutcome.SKIPPED,
                startedAt = startedAt,
                completedAt = startedAt,
                metricResults = emptyList(),
                message = "Polar sync skipped because data is still fresh."
            ).also { result ->
                _status.value = _status.value.copy(lastResult = result)
            }
        }

        _status.value = _status.value.copy(isRunning = true, activeTrigger = trigger)

        val result = try {
            if (!appSettingsRepository.isPolarConnected()) {
                PolarSyncRunResult(
                    trigger = trigger,
                    outcome = PolarSyncOutcome.SKIPPED,
                    startedAt = startedAt,
                    completedAt = Clock.System.now(),
                    metricResults = emptyList(),
                    message = "Polar account is not connected."
                )
            } else {
                runSync(trigger, startedAt)
            }
        } finally {
            _status.value = _status.value.copy(isRunning = false, activeTrigger = null)
        }

        _status.value = _status.value.copy(lastResult = result)
        return result
    }

    private suspend fun shouldSkipStaleSync(
        trigger: PolarSyncTrigger,
        maxAge: Duration,
        now: Instant
    ): Boolean {
        if (trigger == PolarSyncTrigger.MANUAL_REFRESH || trigger == PolarSyncTrigger.INITIAL_LINK) {
            return false
        }

        val latestSuccessfulSync = polarSyncRepository.getAllCheckpoints()
            .maxByOrNull { it.lastSuccessfulSyncAt }
            ?.lastSuccessfulSyncAt

        return latestSuccessfulSync != null && now - latestSuccessfulSync < maxAge
    }

    private suspend fun runSync(trigger: PolarSyncTrigger, startedAt: Instant): PolarSyncRunResult {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val syncedAt = Clock.System.now()
        val metricResults = buildList {
            add(syncActivities(today, syncedAt))
            add(syncSleep(today, syncedAt))
            add(syncTraining(today, syncedAt))
            add(syncNightlyRecharge(today, syncedAt))
            add(syncProfile(today, syncedAt))
        }

        val completedAt = Clock.System.now()
        val failures = metricResults.filter { it.failureMessage != null }
        val outcome = when {
            failures.isEmpty() -> PolarSyncOutcome.SUCCESS
            failures.size == metricResults.size -> PolarSyncOutcome.PARTIAL_FAILURE
            else -> PolarSyncOutcome.PARTIAL_FAILURE
        }

        val importedCount = metricResults.sumOf { it.importedCount }
        val message = when (outcome) {
            PolarSyncOutcome.SUCCESS -> "Polar sync completed. Imported or refreshed $importedCount records."
            PolarSyncOutcome.PARTIAL_FAILURE -> "Polar sync completed with ${failures.size} metric failure(s). Imported or refreshed $importedCount records."
            PolarSyncOutcome.SKIPPED -> "Polar sync skipped."
        }

        return PolarSyncRunResult(
            trigger = trigger,
            outcome = outcome,
            startedAt = startedAt,
            completedAt = completedAt,
            metricResults = metricResults,
            message = message
        )
    }

    private suspend fun syncActivities(today: LocalDate, syncedAt: Instant): PolarMetricSyncResult {
        val family = PolarMetricFamily.ACTIVITY
        val range = resolveDateRange(
            family = family,
            today = today,
            defaultLookbackDays = 28,
            overlapDays = 1
        )

        return fetchAuthorized(family) { token ->
            polarApiClient.getActivities(
                accessToken = token,
                from = range.start.toString(),
                to = range.endExclusive.toString()
            )
        }.fold(
            onSuccess = { activities ->
                polarSyncRepository.upsertActivities(activities, syncedAt)
                saveSuccessCheckpoint(family, today, syncedAt)
                PolarMetricSyncResult(family, activities.size, checkpointCursor = today.toString())
            },
            onFailure = { error -> handleFailure(family, error) }
        )
    }

    private suspend fun syncSleep(today: LocalDate, syncedAt: Instant): PolarMetricSyncResult {
        val family = PolarMetricFamily.SLEEP
        val range = resolveDateRange(
            family = family,
            today = today,
            defaultLookbackDays = 28,
            overlapDays = 1
        )

        val accumulated = mutableListOf<com.wellnesswingman.data.model.polar.PolarSleepResult>()
        var day = range.start
        while (day < range.endExclusive) {
            val nextDay = day.plus(DatePeriod(days = 1))
            val dayResult = fetchAuthorized(family) { token ->
                polarApiClient.getSleep(
                    accessToken = token,
                    from = day.toString(),
                    to = nextDay.toString()
                )
            }

            dayResult.fold(
                onSuccess = { accumulated += it },
                onFailure = { error -> return handleFailure(family, error) }
            )
            day = nextDay
        }

        polarSyncRepository.upsertSleepResults(accumulated, syncedAt)
        saveSuccessCheckpoint(family, today, syncedAt)
        return PolarMetricSyncResult(family, accumulated.size, checkpointCursor = today.toString())
    }

    private suspend fun syncTraining(today: LocalDate, syncedAt: Instant): PolarMetricSyncResult {
        val family = PolarMetricFamily.TRAINING
        val range = resolveDateRange(
            family = family,
            today = today,
            defaultLookbackDays = 90,
            overlapDays = 2
        )

        return fetchAuthorized(family) { token ->
            polarApiClient.getTrainingSessions(
                accessToken = token,
                from = "${range.start}T00:00:00",
                to = "${range.endExclusive}T00:00:00"
            )
        }.fold(
            onSuccess = { sessions ->
                polarSyncRepository.upsertTrainingSessions(sessions, syncedAt)
                saveSuccessCheckpoint(family, today, syncedAt)
                PolarMetricSyncResult(family, sessions.size, checkpointCursor = today.toString())
            },
            onFailure = { error -> handleFailure(family, error) }
        )
    }

    private suspend fun syncNightlyRecharge(today: LocalDate, syncedAt: Instant): PolarMetricSyncResult {
        val family = PolarMetricFamily.NIGHTLY_RECHARGE
        val range = resolveDateRange(
            family = family,
            today = today,
            defaultLookbackDays = 28,
            overlapDays = 1
        )

        return fetchAuthorized(family) { token ->
            polarApiClient.getNightlyRecharge(
                accessToken = token,
                from = range.start.toString(),
                to = range.endExclusive.toString()
            )
        }.fold(
            onSuccess = { results ->
                polarSyncRepository.upsertNightlyRecharge(results, syncedAt)
                saveSuccessCheckpoint(family, today, syncedAt)
                PolarMetricSyncResult(family, results.size, checkpointCursor = today.toString())
            },
            onFailure = { error -> handleFailure(family, error) }
        )
    }

    private suspend fun syncProfile(today: LocalDate, syncedAt: Instant): PolarMetricSyncResult {
        val family = PolarMetricFamily.USER_PROFILE
        return fetchAuthorized(family) { token -> polarApiClient.getUserProfile(token) }.fold(
            onSuccess = { profile ->
                val userId = appSettingsRepository.getPolarUserId().orEmpty().ifBlank { "current" }
                polarSyncRepository.upsertUserProfile(userId, profile, syncedAt)
                saveSuccessCheckpoint(family, today, syncedAt)
                syncProfileIntoSettings(profile)
                PolarMetricSyncResult(family, 1, checkpointCursor = today.toString())
            },
            onFailure = { error -> handleFailure(family, error) }
        )
    }

    private suspend fun <T> fetchAuthorized(
        family: PolarMetricFamily,
        block: suspend (String) -> Result<T>
    ): Result<T> {
        val tokenResult = getValidAccessToken()
        if (tokenResult.isFailure) {
            return Result.failure(tokenResult.exceptionOrNull()!!)
        }

        val initialResult = block(tokenResult.getOrThrow())
        val initialError = initialResult.exceptionOrNull()
        if (initialError !is PolarApiError.Unauthorized) {
            return initialResult
        }

        Napier.w("Polar sync received 401 for ${family.storageValue}; refreshing token and retrying once")
        val refreshedToken = refreshAccessToken()
        if (refreshedToken.isFailure) {
            return Result.failure(refreshedToken.exceptionOrNull()!!)
        }
        return block(refreshedToken.getOrThrow())
    }

    private suspend fun getValidAccessToken(): Result<String> {
        val token = appSettingsRepository.getPolarAccessToken()
            ?: return Result.failure(IllegalStateException("No Polar access token available"))
        val expiresAt = appSettingsRepository.getPolarTokenExpiresAt()
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        return if (expiresAt > nowMillis + ACCESS_TOKEN_EXPIRY_SAFETY_WINDOW_MS) {
            Result.success(token)
        } else {
            refreshAccessToken()
        }
    }

    private suspend fun refreshAccessToken(): Result<String> {
        val refreshed = polarOAuthRepository.refreshTokens()
        return refreshed.fold(
            onSuccess = {
                appSettingsRepository.getPolarAccessToken()?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("Polar refresh succeeded without storing a token"))
            },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun saveSuccessCheckpoint(
        family: PolarMetricFamily,
        today: LocalDate,
        syncedAt: Instant
    ) {
        polarSyncRepository.updateCheckpoint(
            PolarSyncCheckpoint(
                metricFamily = family,
                lastSyncCursor = today.toString(),
                lastSuccessfulSyncAt = syncedAt,
                lastFailureMessage = null
            )
        )
    }

    private suspend fun handleFailure(
        family: PolarMetricFamily,
        error: Throwable
    ): PolarMetricSyncResult {
        val diagnosticsMessage = PolarSyncDiagnostics.summarizeError(error)
        Napier.e("Polar sync failed for ${family.storageValue}: $diagnosticsMessage")
        val existingCheckpoint = polarSyncRepository.getCheckpoint(family)
        if (existingCheckpoint != null) {
            polarSyncRepository.updateCheckpoint(
                existingCheckpoint.copy(lastFailureMessage = diagnosticsMessage)
            )
        }
        return PolarMetricSyncResult(
            metricFamily = family,
            importedCount = 0,
            checkpointCursor = existingCheckpoint?.lastSyncCursor,
            failureMessage = diagnosticsMessage
        )
    }

    private suspend fun resolveDateRange(
        family: PolarMetricFamily,
        today: LocalDate,
        defaultLookbackDays: Int,
        overlapDays: Int
    ): DateRange {
        val checkpoint = polarSyncRepository.getCheckpoint(family)
        val checkpointDate = checkpoint?.lastSyncCursor?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val minimumStart = today.plus(DatePeriod(days = -(defaultLookbackDays - 1)))

        val baseStart = checkpointDate?.plus(DatePeriod(days = -overlapDays))
            ?: minimumStart

        return DateRange(
            start = when {
                baseStart > today -> today
                baseStart < minimumStart -> minimumStart
                else -> baseStart
            },
            endExclusive = today.plus(DatePeriod(days = 1))
        )
    }

    private fun syncProfileIntoSettings(profile: PolarUserProfile) {
        if (appSettingsRepository.getHeight() == null && profile.heightCm > 0.0) {
            appSettingsRepository.setHeight(profile.heightCm)
        }
        if (appSettingsRepository.getCurrentWeight() == null && profile.weightKg > 0.0) {
            appSettingsRepository.setCurrentWeight(profile.weightKg)
        }
        if (appSettingsRepository.getSex().isNullOrBlank() && profile.sex.isNotBlank()) {
            appSettingsRepository.setSex(profile.sex)
        }
        if (appSettingsRepository.getDateOfBirth().isNullOrBlank() && profile.birthday.isNotBlank()) {
            appSettingsRepository.setDateOfBirth(profile.birthday)
        }
    }

    private data class DateRange(
        val start: LocalDate,
        val endExclusive: LocalDate
    )

    private companion object {
        val DEFAULT_AUTOMATIC_SYNC_MAX_AGE: Duration = 6.hours
        const val ACCESS_TOKEN_EXPIRY_SAFETY_WINDOW_MS = 60_000L
    }
}
