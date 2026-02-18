package com.wellnesswingman.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Android implementation of PhotoResizer.
 */
actual class PhotoResizer {

    actual suspend fun resize(
        photoBytes: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int,
        cropHeight: Boolean
    ): ByteArray = withContext(Dispatchers.Default) {
        // Decode bounds only first
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size, options)
        val originalWidth = options.outWidth

        var bitmap: Bitmap

        if (cropHeight) {
            // Decode with a sample size that gets us close to maxWidth without going under
            val sampleSize = calculateSampleSizeForWidth(originalWidth, maxWidth)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }
            bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size, decodeOptions)
            bitmap = applyExifRotation(bitmap, photoBytes)

            // Scale to exactly maxWidth if still wider
            if (bitmap.width > maxWidth) {
                val targetHeight = (bitmap.height.toFloat() * maxWidth / bitmap.width).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, targetHeight, true)
                bitmap.recycle()
                bitmap = scaledBitmap
            }

            // Crop height to maxHeight if too tall (take top portion)
            if (bitmap.height > maxHeight) {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, maxHeight)
                bitmap.recycle()
                bitmap = cropped
            }
        } else {
            val scale = calculateScale(
                originalWidth = originalWidth,
                originalHeight = options.outHeight,
                maxWidth = maxWidth,
                maxHeight = maxHeight
            )
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
                inJustDecodeBounds = false
            }
            bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size, decodeOptions)
            bitmap = applyExifRotation(bitmap, photoBytes)
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val result = outputStream.toByteArray()
        bitmap.recycle()
        outputStream.close()

        result
    }

    private fun applyExifRotation(bitmap: Bitmap, imageBytes: ByteArray): Bitmap {
        val orientation = ExifInterface(ByteArrayInputStream(imageBytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.preScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotated
    }

    private fun calculateSampleSizeForWidth(originalWidth: Int, targetWidth: Int): Int {
        var sampleSize = 1
        while (originalWidth / (sampleSize * 2) > targetWidth) {
            sampleSize *= 2
        }
        return sampleSize
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
