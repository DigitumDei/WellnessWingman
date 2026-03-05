package com.wellnesswingman.platform

/**
 * Platform-specific file system operations.
 */
expect class FileSystem : FileSystemOperations {
    override fun getAppDataDirectory(): String
    override fun getPhotosDirectory(): String
    override suspend fun readBytes(path: String): ByteArray
    override suspend fun writeBytes(path: String, bytes: ByteArray)
    override suspend fun delete(path: String): Boolean
    override fun exists(path: String): Boolean
    override fun isDirectory(path: String): Boolean
    override fun listFiles(path: String): List<String>
    override fun createDirectory(path: String): Boolean
    override fun getCacheDirectory(): String
    override fun getExportsDirectory(): String
    override fun listFilesRecursively(path: String): List<String>
    override suspend fun copyFile(sourcePath: String, destPath: String)
}
