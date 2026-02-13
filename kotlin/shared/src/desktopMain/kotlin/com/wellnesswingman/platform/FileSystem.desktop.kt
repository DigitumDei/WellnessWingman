package com.wellnesswingman.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop (JVM) implementation of FileSystem.
 */
actual class FileSystem {

    actual fun getAppDataDirectory(): String {
        val homeDir = System.getProperty("user.home")
        val appDir = File(homeDir, ".wellnesswingman")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return appDir.absolutePath
    }

    actual fun getPhotosDirectory(): String {
        val photosDir = File(getAppDataDirectory(), "photos")
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

    actual fun getCacheDirectory(): String {
        val cacheDir = File(getAppDataDirectory(), "cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir.absolutePath
    }

    actual fun listFilesRecursively(path: String): List<String> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList()
    }

    actual suspend fun copyFile(sourcePath: String, destPath: String): Unit = withContext(Dispatchers.IO) {
        val destFile = File(destPath)
        destFile.parentFile?.mkdirs()
        File(sourcePath).copyTo(destFile, overwrite = true)
        Unit
    }
}
