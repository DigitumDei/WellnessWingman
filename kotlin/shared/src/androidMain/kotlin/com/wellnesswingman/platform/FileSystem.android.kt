package com.wellnesswingman.platform

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of FileSystem.
 */
actual class FileSystem(private val context: Context) {

    actual fun getAppDataDirectory(): String {
        return context.filesDir.absolutePath
    }

    actual fun getPhotosDirectory(): String {
        val photosDir = File(context.filesDir, "photos")
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        return photosDir.absolutePath
    }

    actual suspend fun readBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        File(path).readBytes()
    }

    actual suspend fun writeBytes(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        File(path).writeBytes(bytes)
    }

    actual suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).delete()
    }

    actual fun exists(path: String): Boolean {
        return File(path).exists()
    }

    actual fun listFiles(path: String): List<String> {
        return File(path).listFiles()?.map { it.absolutePath } ?: emptyList()
    }

    actual fun createDirectory(path: String): Boolean {
        return File(path).mkdirs()
    }
}
