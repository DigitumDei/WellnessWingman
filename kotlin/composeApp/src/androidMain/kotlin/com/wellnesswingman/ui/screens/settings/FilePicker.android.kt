package com.wellnesswingman.ui.screens.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.aakira.napier.Napier
import java.io.File

@Composable
actual fun rememberFilePicker(onFilePicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                // Copy the content URI to a temp file that we can access
                val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                onFilePicked(tempFile.absolutePath)
            } catch (e: Exception) {
                Napier.e("Failed to copy import file", e)
            }
        }
    }

    return {
        launcher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
    }
}
