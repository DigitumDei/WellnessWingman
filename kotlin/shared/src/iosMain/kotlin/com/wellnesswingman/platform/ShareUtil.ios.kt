package com.wellnesswingman.platform

actual class ShareUtil {

    actual fun shareFile(filePath: String, mimeType: String, title: String) {
        // iOS implementation not available yet
        println("File sharing not implemented for iOS")
    }
}
