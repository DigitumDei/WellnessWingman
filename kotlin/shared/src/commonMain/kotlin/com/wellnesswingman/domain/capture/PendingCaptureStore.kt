package com.wellnesswingman.domain.capture

import com.wellnesswingman.platform.FileSystem
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json

/**
 * File-based store for pending camera captures.
 * Survives process death because it writes to the app's persistent data directory.
 *
 * When the camera is launched, a PendingCapture is saved with the photo file path.
 * If the app is killed during camera use, the pending capture can be recovered on restart.
 */
class PendingCaptureStore(private val fileSystem: FileSystem) {

    private val json = Json { ignoreUnknownKeys = true }

    private val pendingCapturePath: String
        get() = "${fileSystem.getAppDataDirectory()}/pending_capture.json"

    /**
     * Saves a pending capture record to disk.
     * Called before launching the camera.
     */
    suspend fun save(pendingCapture: PendingCapture) {
        try {
            val jsonString = json.encodeToString(PendingCapture.serializer(), pendingCapture)
            fileSystem.writeBytes(pendingCapturePath, jsonString.encodeToByteArray())
            Napier.d("Saved pending capture: ${pendingCapture.photoFilePath}")
        } catch (e: Exception) {
            Napier.e("Failed to save pending capture", e)
        }
    }

    /**
     * Retrieves the pending capture record, if one exists.
     * Returns null if no pending capture or if the file is corrupted.
     */
    suspend fun get(): PendingCapture? {
        return try {
            if (!fileSystem.exists(pendingCapturePath)) return null
            val bytes = fileSystem.readBytes(pendingCapturePath)
            val jsonString = bytes.decodeToString()
            json.decodeFromString(PendingCapture.serializer(), jsonString)
        } catch (e: Exception) {
            Napier.e("Failed to read pending capture", e)
            null
        }
    }

    /**
     * Clears the pending capture record.
     * Called after the capture is successfully handled or cancelled.
     */
    suspend fun clear() {
        try {
            if (fileSystem.exists(pendingCapturePath)) {
                fileSystem.delete(pendingCapturePath)
                Napier.d("Cleared pending capture")
            }
        } catch (e: Exception) {
            Napier.e("Failed to clear pending capture", e)
        }
    }

    /**
     * Returns the directory path for pending capture photo files.
     * Uses the app's persistent files directory (not cache) so files survive process death.
     */
    fun getPendingPhotosDirectory(): String {
        val dir = "${fileSystem.getAppDataDirectory()}/pending_photos"
        fileSystem.createDirectory(dir)
        return dir
    }
}
