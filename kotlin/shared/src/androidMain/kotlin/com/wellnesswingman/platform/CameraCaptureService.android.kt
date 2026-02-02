package com.wellnesswingman.platform

import android.content.Context

/**
 * Android implementation of CameraCaptureService.
 *
 * Note: Camera and gallery functionality is implemented directly in the UI layer
 * using Compose's rememberLauncherForActivityResult, as ActivityResultContracts
 * cannot be properly integrated with suspend functions.
 *
 * See: composeApp/src/androidMain/kotlin/com/wellnesswingman/ui/screens/photo/PhotoReviewScreen.android.kt
 */
actual class CameraCaptureService(private val context: Context) {

    actual suspend fun capturePhoto(): CaptureResult {
        return CaptureResult.Error("Camera capture not implemented in service layer. Use PhotoReviewScreen UI instead.")
    }

    actual suspend fun pickFromGallery(): CaptureResult? {
        return CaptureResult.Error("Gallery picker not implemented in service layer. Use PhotoReviewScreen UI instead.")
    }
}
