package com.wellnesswingman.platform

import java.awt.Desktop
import java.io.File

actual class ShareUtil {

    actual fun shareFile(filePath: String, mimeType: String, title: String) {
        try {
            val file = File(filePath)
            if (file.exists() && Desktop.isDesktopSupported()) {
                // Open the containing folder and select the file
                Desktop.getDesktop().open(file.parentFile)
            }
        } catch (e: Exception) {
            println("Failed to share file: ${e.message}")
        }
    }
}
