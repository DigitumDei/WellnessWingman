package com.wellnesswingman.domain.polar

import com.wellnesswingman.data.model.polar.PolarApiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolarSyncDiagnosticsTest {

    @Test
    fun `summarizeError categorizes auth errors without leaking raw token text`() {
        val message = PolarSyncDiagnostics.summarizeError(
            PolarApiError.Unauthorized("""{"error":"bad token","access_token":"secret-value"}""")
        )

        assertEquals("Authorization failed with Polar. Reconnect or refresh the session.", message)
        assertFalse(message.contains("secret-value"))
    }

    @Test
    fun `sanitizeForLogs redacts tokens and raw metric arrays`() {
        val sanitized = PolarSyncDiagnostics.sanitizeForLogs(
            """Bearer abc123 {"activityDays":[{"date":"2025-03-01"}],"refresh_token":"rt-1"}"""
        )

        assertTrue(sanitized.contains("Bearer [REDACTED]"))
        assertTrue(sanitized.contains("\"activityDays\":[REDACTED]"))
        assertFalse(sanitized.contains("abc123"))
        assertFalse(sanitized.contains("rt-1"))
    }
}
