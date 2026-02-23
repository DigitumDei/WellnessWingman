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
        val image = applyExifRotation(originalImage, readJpegExifOrientation(photoBytes))

        val outputStream = ByteArrayOutputStream()

        if (cropHeight) {
            // Scale to maxWidth maintaining aspect ratio, then crop height from top
            val scaledWidth: Int
            val scaledHeight: Int
            if (image.width > maxWidth) {
                val ratio = maxWidth.toDouble() / image.width
                scaledWidth = maxWidth
                scaledHeight = (image.height * ratio).toInt()
            } else {
                scaledWidth = image.width
                scaledHeight = image.height
            }

            val finalHeight = minOf(scaledHeight, maxHeight)
            val bufferedImage = BufferedImage(scaledWidth, finalHeight, BufferedImage.TYPE_INT_RGB)
            val graphics = bufferedImage.createGraphics()
            // Drawing into a finalHeight-tall canvas clips naturally to the top portion
            graphics.drawImage(image, 0, 0, scaledWidth, scaledHeight, null)
            graphics.dispose()

            ImageIO.write(bufferedImage, "jpg", outputStream)
        } else {
            val (newWidth, newHeight) = calculateDimensions(
                image.width,
                image.height,
                maxWidth,
                maxHeight
            )

            val scaledImage = image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
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

/**
 * Reads the EXIF orientation tag from JPEG bytes.
 * Returns a value in 1–8, or 1 (normal) if absent or not a JPEG.
 */
private fun readJpegExifOrientation(bytes: ByteArray): Int {
    if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return 1
    var pos = 2
    while (pos + 3 < bytes.size) {
        if (bytes[pos] != 0xFF.toByte()) return 1
        val marker = bytes[pos + 1].toInt() and 0xFF
        pos += 2
        if (marker == 0xD9 || marker == 0xDA) return 1  // EOI / SOS – stop scanning
        val segLen = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
        if (marker == 0xE1 && pos + 8 <= bytes.size) {  // APP1
            if (bytes[pos + 2] == 0x45.toByte() &&  // E
                bytes[pos + 3] == 0x78.toByte() &&  // x
                bytes[pos + 4] == 0x69.toByte() &&  // i
                bytes[pos + 5] == 0x66.toByte() &&  // f
                bytes[pos + 6] == 0x00.toByte() &&
                bytes[pos + 7] == 0x00.toByte()
            ) {
                val tiff = pos + 8
                if (tiff + 8 > bytes.size) return 1
                val le = bytes[tiff] == 0x49.toByte()  // "II" = little-endian
                val ifd0 = tiff + readUInt32(bytes, tiff + 4, le)
                if (ifd0 + 2 > bytes.size) return 1
                val entryCount = readUInt16(bytes, ifd0, le)
                for (i in 0 until entryCount) {
                    val e = ifd0 + 2 + i * 12
                    if (e + 12 > bytes.size) break
                    if (readUInt16(bytes, e, le) == 0x0112) {
                        return readUInt16(bytes, e + 8, le)
                    }
                }
            }
        }
        pos += segLen
    }
    return 1
}

private fun readUInt16(b: ByteArray, off: Int, le: Boolean): Int {
    if (off + 2 > b.size) return 0
    val b0 = b[off].toInt() and 0xFF
    val b1 = b[off + 1].toInt() and 0xFF
    return if (le) b0 or (b1 shl 8) else (b0 shl 8) or b1
}

private fun readUInt32(b: ByteArray, off: Int, le: Boolean): Int {
    if (off + 4 > b.size) return 0
    val b0 = b[off].toInt() and 0xFF
    val b1 = b[off + 1].toInt() and 0xFF
    val b2 = b[off + 2].toInt() and 0xFF
    val b3 = b[off + 3].toInt() and 0xFF
    return if (le) b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    else (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
}

/**
 * Returns a new [BufferedImage] with EXIF orientation baked in.
 * Orientation values 1–8 follow the EXIF spec; 1 is the normal case (no-op).
 *
 * Transform derivation (Graphics2D "last-specified-first-applied" rule):
 *   6 (CW 90°):  translate(newW, 0) then rotate(π/2)  → dest = (H−y, x)
 *   8 (CCW 90°): translate(0, newH) then rotate(−π/2) → dest = (y, W−x)
 *   3 (180°):    translate(newW, newH) then rotate(π)  → dest = (W−x, H−y)
 *   5/7 use the direct affine matrix for transpose/transverse reflections.
 */
private fun applyExifRotation(image: BufferedImage, orientation: Int): BufferedImage {
    if (orientation <= 1) return image
    val (newWidth, newHeight) = if (orientation in 5..8) Pair(image.height, image.width)
                                else Pair(image.width, image.height)
    val result = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
    val g = result.createGraphics()
    when (orientation) {
        2 -> { g.translate(newWidth.toDouble(), 0.0); g.scale(-1.0, 1.0) }
        3 -> { g.translate(newWidth.toDouble(), newHeight.toDouble()); g.rotate(Math.PI) }
        4 -> { g.translate(0.0, newHeight.toDouble()); g.scale(1.0, -1.0) }
        5 -> g.transform(java.awt.geom.AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0))
        6 -> { g.translate(newWidth.toDouble(), 0.0); g.rotate(Math.PI / 2) }
        7 -> g.transform(java.awt.geom.AffineTransform(0.0, -1.0, -1.0, 0.0, newWidth.toDouble(), newHeight.toDouble()))
        8 -> { g.translate(0.0, newHeight.toDouble()); g.rotate(-Math.PI / 2) }
    }
    g.drawImage(image, 0, 0, null)
    g.dispose()
    return result
}
