package com.wellnesswingman.domain.polar

import com.wellnesswingman.data.model.polar.PolarApiError

object PolarSyncDiagnostics {
    private val sensitiveJsonKeys = setOf("access_token", "refresh_token", "authorization")

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
        return redactSensitiveJsonValues(text)
            .replace(
                Regex("(?i)('(?:access[_ -]?token|refresh[_ -]?token|authorization)'\\s*:\\s*)'[^']*'"),
                "$1'[REDACTED]'"
            )
            .replace(Regex("(?i)(access[_ -]?token|refresh[_ -]?token|authorization)\\s*[:=]\\s*[^\\s,;]+"), "$1=[REDACTED]")
            .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer [REDACTED]")
            .replace(Regex("\"(activityDays|nightSleeps|trainingSessions|nightlyRechargeResults)\"\\s*:\\s*\\[[^\\]]*]"), "\"$1\":[REDACTED]")
    }

    private fun redactSensitiveJsonValues(text: String): String {
        val output = StringBuilder(text.length)
        var index = 0

        while (index < text.length) {
            if (text[index] != '"') {
                output.append(text[index])
                index += 1
                continue
            }

            val keyEnd = findQuotedStringEnd(text, index + 1)
            if (keyEnd == null) {
                output.append(text.substring(index))
                break
            }
            val rawKey = text.substring(index + 1, keyEnd)
            val normalizedKey = rawKey.lowercase().replace(" ", "_").replace("-", "_")

            output.append(text, index, keyEnd + 1)
            index = keyEnd + 1

            val colonStart = index
            while (index < text.length && text[index].isWhitespace()) index += 1
            if (index >= text.length || text[index] != ':') {
                output.append(text, colonStart, index)
                continue
            }
            index += 1
            while (index < text.length && text[index].isWhitespace()) index += 1

            if (normalizedKey !in sensitiveJsonKeys || index >= text.length || text[index] != '"') {
                output.append(text, colonStart, index)
                continue
            }

            output.append(text, colonStart, index)
            val valueEnd = findQuotedStringEnd(text, index + 1)
            if (valueEnd == null) {
                output.append(text.substring(index))
                break
            }
            output.append("\"[REDACTED]\"")
            index = valueEnd + 1
        }

        return output.toString()
    }

    private fun findQuotedStringEnd(text: String, start: Int): Int? {
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                return index
            }
        }
        return null
    }
}
