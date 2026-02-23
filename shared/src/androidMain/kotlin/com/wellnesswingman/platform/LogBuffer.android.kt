package com.wellnesswingman.platform

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Napier antilog that stores recent logs in memory for diagnostic sharing,
 * and persists them to disk so previous sessions are available after a crash.
 */
class LogBuffer(private val maxSize: Int = 1000) : Antilog() {
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var currentLogFile: File? = null

    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String?,
        val message: String,
        val throwable: Throwable?
    )

    /**
     * Call once at app startup. Rotates session log files so the previous
     * session's log is preserved, then opens a new file for this session.
     *
     * Files written to [filesDir]/logs/:
     *   session_0.log — current session (being written)
     *   session_1.log — previous session
     *   session_2.log — two sessions ago
     */
    fun initPersistentLogging(filesDir: File) {
        val logsDir = File(filesDir, "logs")
        logsDir.mkdirs()

        val session0 = File(logsDir, "session_0.log")
        val session1 = File(logsDir, "session_1.log")
        val session2 = File(logsDir, "session_2.log")

        // Rotate: drop oldest, shift the rest back
        session2.delete()
        if (session1.exists()) session1.renameTo(session2)
        if (session0.exists()) session0.renameTo(session1)

        currentLogFile = session0
    }

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

        // Persist to disk so the log survives crashes
        val line = formatEntry(entry)
        currentLogFile?.let { file ->
            try {
                FileOutputStream(file, /* append = */ true).use { it.write(line.toByteArray()) }
            } catch (_: Exception) {
                // Never crash the app due to a logging failure
            }
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

    private fun formatEntry(entry: LogEntry): String = buildString {
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

    fun getAllLogs(): List<LogEntry> = logs.toList()

    fun getLogsAsText(): String = buildString {
        appendLine("=== WellnessWingman Diagnostic Logs ===")
        appendLine("Total entries: ${logs.size}")
        appendLine("Generated: ${dateFormat.format(Date())}")
        appendLine()

        for (entry in logs) {
            append(formatEntry(entry))
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
