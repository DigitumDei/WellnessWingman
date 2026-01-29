package com.wellnesswingman.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS implementation of FileSystem.
 */
@OptIn(ExperimentalForeignApi::class)
actual class FileSystem {

    actual fun getAppDataDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        return paths.firstOrNull() as? String ?: ""
    }

    actual fun getPhotosDirectory(): String {
        val docsDir = getAppDataDirectory()
        val photosPath = "$docsDir/photos"
        NSFileManager.defaultManager.createDirectoryAtPath(
            photosPath,
            true,
            null,
            null
        )
        return photosPath
    }

    actual suspend fun readBytes(path: String): ByteArray = withContext(Dispatchers.Default) {
        val data = NSData.dataWithContentsOfFile(path)
            ?: throw Exception("Failed to read file: $path")
        ByteArray(data.length.toInt()).apply {
            data.bytes?.let { bytes ->
                for (i in indices) {
                    this[i] = (bytes as kotlinx.cinterop.CPointer<*>).toLong().toByte()
                }
            }
        }
    }

    actual suspend fun writeBytes(path: String, bytes: ByteArray) = withContext(Dispatchers.Default) {
        val data = bytes.toNSData()
        data.writeToFile(path, true)
    }

    actual suspend fun delete(path: String): Boolean = withContext(Dispatchers.Default) {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    actual fun exists(path: String): Boolean {
        return NSFileManager.defaultManager.fileExistsAtPath(path)
    }

    actual fun listFiles(path: String): List<String> {
        val contents = NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, null)
        return (contents as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    }

    actual fun createDirectory(path: String): Boolean {
        return NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            true,
            null,
            null
        )
    }

    private fun ByteArray.toNSData(): NSData {
        return NSData.create(bytes = this.toCValues(), length = this.size.toULong())
    }
}
