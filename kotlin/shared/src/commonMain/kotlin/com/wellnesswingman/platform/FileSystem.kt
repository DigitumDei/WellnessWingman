package com.wellnesswingman.platform

/**
 * Platform-specific file system operations.
 */
expect class FileSystem {
    /**
     * Gets the app's data directory path.
     */
    fun getAppDataDirectory(): String

    /**
     * Gets the photos directory path.
     */
    fun getPhotosDirectory(): String

    /**
     * Reads bytes from a file.
     */
    suspend fun readBytes(path: String): ByteArray

    /**
     * Writes bytes to a file.
     */
    suspend fun writeBytes(path: String, bytes: ByteArray)

    /**
     * Deletes a file.
     */
    suspend fun delete(path: String): Boolean

    /**
     * Checks if a file exists.
     */
    fun exists(path: String): Boolean

    /**
     * Lists files in a directory.
     */
    fun listFiles(path: String): List<String>

    /**
     * Creates a directory.
     */
    fun createDirectory(path: String): Boolean
}
