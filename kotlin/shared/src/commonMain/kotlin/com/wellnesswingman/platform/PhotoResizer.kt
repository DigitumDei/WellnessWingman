package com.wellnesswingman.platform

/**
 * Platform-specific photo resizing service.
 */
expect class PhotoResizer {
    /**
     * Resizes a photo to the specified maximum dimensions while maintaining aspect ratio.
     * @param photoBytes Original photo bytes
     * @param maxWidth Maximum width in pixels
     * @param maxHeight Maximum height in pixels
     * @param quality JPEG quality (0-100)
     * @return Resized photo bytes
     */
    suspend fun resize(
        photoBytes: ByteArray,
        maxWidth: Int = 1920,
        maxHeight: Int = 1080,
        quality: Int = 85
    ): ByteArray
}
