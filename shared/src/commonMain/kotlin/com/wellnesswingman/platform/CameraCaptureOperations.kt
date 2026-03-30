package com.wellnesswingman.platform

interface CameraCaptureOperations {
    suspend fun capturePhoto(): CaptureResult
    suspend fun pickFromGallery(): CaptureResult?
}
