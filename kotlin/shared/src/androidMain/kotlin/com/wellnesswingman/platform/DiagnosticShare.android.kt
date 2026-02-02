package com.wellnesswingman.platform

import android.content.Context
import android.content.Intent
import java.io.File

actual class DiagnosticShare(
    private val context: Context,
    private val diagnosticLogger: DiagnosticLogger
) {
    actual fun shareDiagnosticLogs() {
        try {
            // Generate diagnostic report
            val report = diagnosticLogger.generateDiagnosticReport()

            // Create share intent with text
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "WellnessWingman Diagnostic Logs")
                putExtra(Intent.EXTRA_TEXT, report)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Launch share dialog
            val chooser = Intent.createChooser(shareIntent, "Share Diagnostic Logs")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            android.util.Log.e("DiagnosticShare", "Failed to share logs", e)
        }
    }
}
