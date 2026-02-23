package com.wellnesswingman.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop implementation of CameraCaptureService.
 * Desktop doesn't have camera, so only file picker is supported.
 */
actual class CameraCaptureService {

    actual suspend fun capturePhoto(): CaptureResult {
        // Desktop doesn't have camera, fallback to file picker
        return pickFromGallery() ?: CaptureResult.Cancelled
    }

    actual suspend fun pickFromGallery(): CaptureResult? = withContext(Dispatchers.IO) {
        val fileDialog = FileDialog(null as Frame?, "Select Image", FileDialog.LOAD)
        fileDialog.setFilenameFilter { _, name ->
            name.lowercase().let {
                it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".jpeg")
            }
        }
        fileDialog.isVisible = true

        val selectedFile = fileDialog.file?.let { File(fileDialog.directory, it) }

        if (selectedFile != null && selectedFile.exists()) {
            CaptureResult.Success(
                photoPath = selectedFile.absolutePath,
                bytes = selectedFile.readBytes()
            )
        } else {
            null
        }
    }
}
