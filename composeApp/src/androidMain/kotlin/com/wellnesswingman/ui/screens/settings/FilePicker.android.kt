package com.wellnesswingman.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun rememberFilePicker(onFilePicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val tempPath = withContext(Dispatchers.IO) {
                        // Copy to filesDir/imports/ (not cacheDir — cache is subject to eviction
                        // and may not have enough space for large exports).
                        val importsDir = File(context.filesDir, "imports")
                        importsDir.mkdirs()
                        // Clean up previous import temp files
                        importsDir.listFiles()?.forEach { it.delete() }

                        val tempFile = File(importsDir, "import_${System.currentTimeMillis()}.zip")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        if (tempFile.exists() && tempFile.length() > 0) {
                            Napier.i("Copied import file: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                            tempFile.absolutePath
                        } else {
                            throw Exception("Failed to copy import file or file is empty")
                        }
                    }
                    onFilePicked(tempPath)
                } catch (e: Exception) {
                    Napier.e("Failed to copy import file", e)
                }
            }
        }
    }

    return {
        launcher.launch(arrayOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/x-zip",
            "application/octet-stream",
            "*/*"
        ))
    }
}
