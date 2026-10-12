package com.wellnesswingman.workers

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolarSyncRetryPolicyTest {
    @Test
    fun initialFailureAllowsExactlyTenRetries() {
        var attempt = 0
        while (PolarSyncRetryPolicy.shouldRetry(attempt)) {
            attempt++
        }
        assertEquals(10, attempt)
        assertFalse(PolarSyncRetryPolicy.shouldRetry(10))
        assertFalse(PolarSyncRetryPolicy.shouldRetry(11))
        assertFalse(PolarSyncRetryPolicy.shouldRetry(Int.MAX_VALUE))
        // The next periodic cycle resets WorkManager's attempt count.
        assertTrue(PolarSyncRetryPolicy.shouldRetry(0))
    }

    @Test
    fun workManagerSchedulesFinalRetryAroundTwelveHours() {
        val spec = PolarSyncRetryPolicy.createRequest(Constraints.NONE).workSpec
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(12 * 60 * 60 * 1000L, spec.intervalDuration)

        var elapsed = 0L
        var previousDelay = 0L
        for (retry in 1..10) {
            spec.runAttemptCount = retry
            spec.lastEnqueueTime = elapsed
            val nextRun = spec.calculateNextRunTime()
            val delay = nextRun - elapsed
            assertTrue(delay > previousDelay)
            elapsed = nextRun
            previousDelay = delay
        }
        assertEquals(5 * 60 * 60 * 1000L, previousDelay)
        val twelveHours = 12 * 60 * 60 * 1000L
        assertTrue(elapsed in twelveHours..(twelveHours + 1000L))
    }
}
