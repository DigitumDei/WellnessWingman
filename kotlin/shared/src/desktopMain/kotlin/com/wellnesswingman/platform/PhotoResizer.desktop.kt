package com.wellnesswingman.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Desktop implementation of PhotoResizer using Java AWT.
 */
actual class PhotoResizer {

    actual suspend fun resize(
        photoBytes: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int
    ): ByteArray = withContext(Dispatchers.Default) {
        // Read the original image
        val inputStream = ByteArrayInputStream(photoBytes)
        val originalImage = ImageIO.read(inputStream)

        // Calculate new dimensions
        val (newWidth, newHeight) = calculateDimensions(
            originalImage.width,
            originalImage.height,
            maxWidth,
            maxHeight
        )

        // Resize the image
        val scaledImage = originalImage.getScaledInstance(
            newWidth,
            newHeight,
            Image.SCALE_SMOOTH
        )

        // Convert to BufferedImage
        val bufferedImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = bufferedImage.createGraphics()
        graphics.drawImage(scaledImage, 0, 0, null)
        graphics.dispose()

        // Write to bytes
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "jpg", outputStream)

        outputStream.toByteArray()
    }

    private fun calculateDimensions(
        originalWidth: Int,
        originalHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Pair<Int, Int> {
        if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
            return Pair(originalWidth, originalHeight)
        }

        val widthRatio = maxWidth.toDouble() / originalWidth
        val heightRatio = maxHeight.toDouble() / originalHeight
        val ratio = minOf(widthRatio, heightRatio)

        return Pair(
            (originalWidth * ratio).toInt(),
            (originalHeight * ratio).toInt()
        )
    }
}
