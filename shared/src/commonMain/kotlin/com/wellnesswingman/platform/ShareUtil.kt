package com.wellnesswingman.platform

expect class ShareUtil {
    fun shareFile(filePath: String, mimeType: String, title: String)
}
