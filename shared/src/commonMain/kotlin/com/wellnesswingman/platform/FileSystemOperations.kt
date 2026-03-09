package com.wellnesswingman.platform

interface FileSystemOperations {
    fun getAppDataDirectory(): String
    fun getPhotosDirectory(): String
    suspend fun readBytes(path: String): ByteArray
    suspend fun writeBytes(path: String, bytes: ByteArray)
    suspend fun delete(path: String): Boolean
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun listFiles(path: String): List<String>
    fun createDirectory(path: String): Boolean
    fun getCacheDirectory(): String
    fun getExportsDirectory(): String
    fun listFilesRecursively(path: String): List<String>
    suspend fun copyFile(sourcePath: String, destPath: String)
}
