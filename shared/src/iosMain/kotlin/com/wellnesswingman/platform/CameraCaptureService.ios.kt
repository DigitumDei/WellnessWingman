package com.wellnesswingman.platform

/**
 * iOS implementation of CameraCaptureService.
 * TODO: Implement using UIImagePickerController
 */
actual class CameraCaptureService {

    actual suspend fun capturePhoto(): CaptureResult {
        // TODO: Implement using UIImagePickerController
        return CaptureResult.Error("Camera capture not yet implemented on iOS")
    }

    actual suspend fun pickFromGallery(): CaptureResult? {
        // TODO: Implement using UIImagePickerController
        return CaptureResult.Error("Gallery picker not yet implemented on iOS")
    }
}
