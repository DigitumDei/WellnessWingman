package com.wellnesswingman.platform

/**
 * Result of a photo capture operation.
 */
sealed class CaptureResult {
    data class Success(val photoPath: String, val bytes: ByteArray) : CaptureResult()
    data class Error(val message: String) : CaptureResult()
    object Cancelled : CaptureResult()
}

/**
 * Platform-specific camera and photo picker service.
 */
expect class CameraCaptureService : CameraCaptureOperations {
    /**
     * Captures a photo using the device camera.
     */
    override suspend fun capturePhoto(): CaptureResult

    /**
     * Picks a photo from the device gallery/photos.
     */
    override suspend fun pickFromGallery(): CaptureResult?
}
