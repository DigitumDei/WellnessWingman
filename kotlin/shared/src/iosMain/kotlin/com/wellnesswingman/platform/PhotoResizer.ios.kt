package com.wellnesswingman.platform

/**
 * iOS implementation of PhotoResizer.
 * TODO: Implement using UIImage and Core Graphics
 */
actual class PhotoResizer {

    actual suspend fun resize(
        photoBytes: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int,
        cropHeight: Boolean
    ): ByteArray {
        // TODO: Implement using UIImage resizing
        // For now, just return the original bytes
        return photoBytes
    }
}
