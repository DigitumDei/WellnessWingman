package com.wellnesswingman.platform

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of FileSystem.
 */
actual class FileSystem(private val context: Context) : FileSystemOperations {

    actual override fun getAppDataDirectory(): String {
        return context.filesDir.absolutePath
    }

    actual override fun getPhotosDirectory(): String {
        val photosDir = File(context.filesDir, "photos")
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        return photosDir.absolutePath
    }

    actual override suspend fun readBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        File(path).readBytes()
    }

    actual override suspend fun writeBytes(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        File(path).writeBytes(bytes)
    }

    actual override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).delete()
    }

    actual override fun exists(path: String): Boolean {
        return File(path).exists()
    }

    actual override fun isDirectory(path: String): Boolean {
        return File(path).isDirectory
    }

    actual override fun listFiles(path: String): List<String> {
        return File(path).listFiles()?.map { it.absolutePath } ?: emptyList()
    }

    actual override fun createDirectory(path: String): Boolean {
        return File(path).mkdirs()
    }

    actual override fun getCacheDirectory(): String {
        return context.cacheDir.absolutePath
    }

    actual override fun getExportsDirectory(): String {
        val exportsDir = File(context.filesDir, "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }
        return exportsDir.absolutePath
    }

    actual override fun listFilesRecursively(path: String): List<String> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList()
    }

    actual override suspend fun copyFile(sourcePath: String, destPath: String): Unit = withContext(Dispatchers.IO) {
        val destFile = File(destPath)
        destFile.parentFile?.mkdirs()
        File(sourcePath).copyTo(destFile, overwrite = true)
        Unit
    }
}
