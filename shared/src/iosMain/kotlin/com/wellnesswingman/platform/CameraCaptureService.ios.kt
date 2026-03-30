package com.wellnesswingman.platform

/**
 * iOS implementation of CameraCaptureService.
 * TODO: Implement using UIImagePickerController
 */
actual class CameraCaptureService : CameraCaptureOperations {

    actual override suspend fun capturePhoto(): CaptureResult {
        // TODO: Implement using UIImagePickerController
        return CaptureResult.Error("Camera capture not yet implemented on iOS")
    }

    actual override suspend fun pickFromGallery(): CaptureResult? {
        // TODO: Implement using UIImagePickerController
        return CaptureResult.Error("Gallery picker not yet implemented on iOS")
    }
}
