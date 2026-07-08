package com.wellnesswingman.domain.capture

import com.wellnesswingman.platform.FileSystem
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json

/**
 * File-based store for pending camera/gallery captures.
 * Survives process death and configuration changes because it writes to the
 * app's persistent data directory.
 *
 * The pending-capture record is kept until image persistence and tracked-entry
 * creation complete successfully; it is cleared only at a defined commit point
 * (entry committed) or on explicit cancel/retake. This avoids dropping the
 * recovery record while normalization, preview generation, or entry insertion
 * is still in flight.
 */
class PendingCaptureStore(private val fileSystem: FileSystem) {

    private val json = Json { ignoreUnknownKeys = true }

    private val pendingCapturePath: String
        get() = "${fileSystem.getAppDataDirectory()}/pending_capture.json"

    /**
     * Saves a pending capture record to disk, overwriting any existing record.
     * Called before launching the camera or after a gallery selection is copied
     * to a persistent file.
     */
    suspend fun save(pendingCapture: PendingCapture) {
        try {
            val jsonString = json.encodeToString(PendingCapture.serializer(), pendingCapture)
            fileSystem.writeBytes(pendingCapturePath, jsonString.encodeToByteArray())
            Napier.d("Saved pending capture: ${pendingCapture.photoFilePath} (phase=${pendingCapture.phase})")
        } catch (e: Exception) {
            Napier.e("Failed to save pending capture", e)
        }
    }

    /**
     * Atomically updates the pending capture record by transforming the current
     * value, if any. When no record exists, [transform] receives null and the
     * result is only written when it is non-null. Used to advance the workflow
     * phase and record the resulting entry id without losing other fields.
     */
    suspend fun update(transform: (PendingCapture?) -> PendingCapture?) {
        try {
            val current = get()
            val next = transform(current) ?: return
            val jsonString = json.encodeToString(PendingCapture.serializer(), next)
            fileSystem.writeBytes(pendingCapturePath, jsonString.encodeToByteArray())
            Napier.d("Updated pending capture: ${next.photoFilePath} (phase=${next.phase}, entryId=${next.entryId})")
        } catch (e: Exception) {
            Napier.e("Failed to update pending capture", e)
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
     * Called after the entry is committed or the workflow is explicitly cancelled.
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
