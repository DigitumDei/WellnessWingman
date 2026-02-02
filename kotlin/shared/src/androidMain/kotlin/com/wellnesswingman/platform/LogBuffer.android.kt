package com.wellnesswingman.platform

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Napier antilog that stores recent logs in memory for diagnostic sharing.
 */
class LogBuffer(private val maxSize: Int = 1000) : Antilog() {
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String?,
        val message: String,
        val throwable: Throwable?
    )

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?
    ) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            level = priority,
            tag = tag,
            message = message ?: "",
            throwable = throwable
        )

        logs.add(entry)

        // Keep buffer size limited
        while (logs.size > maxSize) {
            logs.poll()
        }

        // Also log to Android logcat
        val logMessage = buildString {
            if (tag != null) append("[$tag] ")
            append(message)
            if (throwable != null) {
                append("\n")
                append(throwable.stackTraceToString())
            }
        }

        when (priority) {
            LogLevel.VERBOSE -> android.util.Log.v(tag ?: "WellnessWingman", logMessage)
            LogLevel.DEBUG -> android.util.Log.d(tag ?: "WellnessWingman", logMessage)
            LogLevel.INFO -> android.util.Log.i(tag ?: "WellnessWingman", logMessage)
            LogLevel.WARNING -> android.util.Log.w(tag ?: "WellnessWingman", logMessage)
            LogLevel.ERROR -> android.util.Log.e(tag ?: "WellnessWingman", logMessage)
            LogLevel.ASSERT -> android.util.Log.wtf(tag ?: "WellnessWingman", logMessage)
        }
    }

    fun getAllLogs(): List<LogEntry> = logs.toList()

    fun getLogsAsText(): String = buildString {
        appendLine("=== WellnessWingman Diagnostic Logs ===")
        appendLine("Total entries: ${logs.size}")
        appendLine("Generated: ${dateFormat.format(Date())}")
        appendLine()

        for (entry in logs) {
            append("${entry.timestamp} [${entry.level.name}]")
            if (entry.tag != null) append(" [${entry.tag}]")
            append(": ${entry.message}")
            appendLine()

            if (entry.throwable != null) {
                appendLine("Exception: ${entry.throwable.message}")
                appendLine(entry.throwable.stackTraceToString())
                appendLine()
            }
        }
    }

    fun clear() {
        logs.clear()
    }

    companion object {
        private var instance: LogBuffer? = null

        fun getInstance(): LogBuffer {
            if (instance == null) {
                instance = LogBuffer()
            }
            return instance!!
        }
    }
}
