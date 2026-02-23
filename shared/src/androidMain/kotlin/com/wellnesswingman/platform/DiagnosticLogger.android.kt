package com.wellnesswingman.platform

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

class DiagnosticLogger(private val context: Context) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun generateDiagnosticReport(): String = buildString {
        appendLine("=== WellnessWingman Diagnostic Report ===")
        appendLine("Generated: ${dateFormat.format(Date())}")
        appendLine()

        // App Information
        appendLine("--- App Information ---")
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appendLine("App Version: ${packageInfo.versionName}")
            appendLine("Version Code: ${packageInfo.versionCode}")
        } catch (e: Exception) {
            appendLine("App Version: Unable to retrieve")
        }
        appendLine("Package: ${context.packageName}")
        appendLine()

        // Device Information
        appendLine("--- Device Information ---")
        appendLine("Manufacturer: ${Build.MANUFACTURER}")
        appendLine("Brand: ${Build.BRAND}")
        appendLine("Model: ${Build.MODEL}")
        appendLine("Device: ${Build.DEVICE}")
        appendLine("Product: ${Build.PRODUCT}")
        appendLine()

        // Android Information
        appendLine("--- Android Information ---")
        appendLine("Android Version: ${Build.VERSION.RELEASE}")
        appendLine("SDK: ${Build.VERSION.SDK_INT}")
        appendLine("Build ID: ${Build.ID}")
        appendLine("Build Type: ${Build.TYPE}")
        appendLine()

        // Hardware Information
        appendLine("--- Hardware Information ---")
        appendLine("CPU ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("Board: ${Build.BOARD}")
        appendLine("Hardware: ${Build.HARDWARE}")
        appendLine()

        // Memory Information
        appendLine("--- Memory Information ---")
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory

        appendLine("Max Memory: ${maxMemory}MB")
        appendLine("Total Memory: ${totalMemory}MB")
        appendLine("Used Memory: ${usedMemory}MB")
        appendLine("Free Memory: ${freeMemory}MB")
        appendLine()

        // Application Logs
        appendLine("--- Application Logs ---")
        appendLine()
        append(LogBuffer.getInstance().getLogsAsText())
    }

    fun shareDiagnostics(): String {
        return generateDiagnosticReport()
    }
}
