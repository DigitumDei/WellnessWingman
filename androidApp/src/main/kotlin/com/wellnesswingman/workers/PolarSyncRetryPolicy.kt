package com.wellnesswingman.workers

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit

internal object PolarSyncRetryPolicy {
    const val MAX_RETRIES = 10

    // WorkManager doubles each delay and caps it at five hours. The first nine
    // delays total roughly seven hours (base * 511), then retry ten waits five hours.
    const val INITIAL_BACKOFF_MILLIS = 49_316L

    // WorkManager reports zero for the initial attempt, then one through ten for retries.
    fun shouldRetry(runAttemptCount: Int): Boolean = runAttemptCount < MAX_RETRIES

    fun createRequest(constraints: Constraints): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<PolarSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                INITIAL_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
}
