package com.wellnesswingman.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Android implementation of PhotoResizer.
 */
actual class PhotoResizer {

    actual suspend fun resize(
        photoBytes: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int
    ): ByteArray = withContext(Dispatchers.Default) {
        // Decode the original bitmap
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size, options)

        // Calculate scale factor
        val scale = calculateScale(
            originalWidth = options.outWidth,
            originalHeight = options.outHeight,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )

        // Decode with scaling
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = scale
            inJustDecodeBounds = false
        }
        val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size, decodeOptions)

        // Compress to JPEG
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val result = outputStream.toByteArray()

        // Clean up
        bitmap.recycle()
        outputStream.close()

        result
    }

    private fun calculateScale(
        originalWidth: Int,
        originalHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Int {
        var scale = 1

        if (originalWidth > maxWidth || originalHeight > maxHeight) {
            val widthScale = originalWidth / maxWidth
            val heightScale = originalHeight / maxHeight
            scale = maxOf(widthScale, heightScale)
        }

        return scale
    }
}
