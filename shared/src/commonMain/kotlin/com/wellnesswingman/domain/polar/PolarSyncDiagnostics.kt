package com.wellnesswingman.domain.polar

import com.wellnesswingman.data.model.polar.PolarApiError

object PolarSyncDiagnostics {
    fun summarizeError(error: Throwable): String = when (error) {
        is PolarApiError.Unauthorized -> "Authorization failed with Polar. Reconnect or refresh the session."
        is PolarApiError.RateLimited -> "Polar rate limit reached. Try again later."
        is PolarApiError.ServerError -> "Polar API server error (${error.statusCode})."
        is PolarApiError.NetworkError -> "Polar network error: ${redact(error.cause?.message ?: "network failure")}"
        is PolarApiError.InvalidResponse -> "Polar API returned an invalid response."
        else -> redact(error.message ?: "Unknown Polar sync error")
    }

    fun sanitizeForLogs(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return redact(text).take(160)
    }

    private fun redact(text: String): String {
        return text
            .replace(Regex("(?i)(access[_ -]?token|refresh[_ -]?token|authorization)\\s*[:=]\\s*[^\\s,;]+"), "$1=[REDACTED]")
            .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer [REDACTED]")
            .replace(Regex("\"(activityDays|nightSleeps|trainingSessions|nightlyRechargeResults)\"\\s*:\\s*\\[[^\\]]*]"), "\"$1\":[REDACTED]")
    }
}
