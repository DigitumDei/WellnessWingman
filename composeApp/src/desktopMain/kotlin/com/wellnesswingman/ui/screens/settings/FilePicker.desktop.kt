package com.wellnesswingman.ui.screens.settings

import androidx.compose.runtime.Composable
import io.github.aakira.napier.Napier
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberFilePicker(onFilePicked: (String) -> Unit): () -> Unit {
    return {
        try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Select WellnessWingman Export File"
                fileFilter = FileNameExtensionFilter("ZIP files", "zip")
                isAcceptAllFileFilterUsed = false
            }
            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                onFilePicked(chooser.selectedFile.absolutePath)
            }
        } catch (e: Exception) {
            Napier.e("Failed to open file picker", e)
        }
    }
}
