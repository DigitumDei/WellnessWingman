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
        quality: Int,
        cropHeight: Boolean
    ): ByteArray = withContext(Dispatchers.Default) {
        val inputStream = ByteArrayInputStream(photoBytes)
        val originalImage = ImageIO.read(inputStream)

        val outputStream = ByteArrayOutputStream()

        if (cropHeight) {
            // Scale to maxWidth maintaining aspect ratio, then crop height from top
            val scaledWidth: Int
            val scaledHeight: Int
            if (originalImage.width > maxWidth) {
                val ratio = maxWidth.toDouble() / originalImage.width
                scaledWidth = maxWidth
                scaledHeight = (originalImage.height * ratio).toInt()
            } else {
                scaledWidth = originalImage.width
                scaledHeight = originalImage.height
            }

            val finalHeight = minOf(scaledHeight, maxHeight)
            val bufferedImage = BufferedImage(scaledWidth, finalHeight, BufferedImage.TYPE_INT_RGB)
            val graphics = bufferedImage.createGraphics()
            // Drawing into a finalHeight-tall canvas clips naturally to the top portion
            graphics.drawImage(originalImage, 0, 0, scaledWidth, scaledHeight, null)
            graphics.dispose()

            ImageIO.write(bufferedImage, "jpg", outputStream)
        } else {
            val (newWidth, newHeight) = calculateDimensions(
                originalImage.width,
                originalImage.height,
                maxWidth,
                maxHeight
            )

            val scaledImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
            val bufferedImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
            val graphics = bufferedImage.createGraphics()
            graphics.drawImage(scaledImage, 0, 0, null)
            graphics.dispose()

            ImageIO.write(bufferedImage, "jpg", outputStream)
        }

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
