package com.wellnesswingman.domain.capture

import kotlinx.serialization.Serializable

/**
 * Origin of an in-progress capture.
 *
 * Persisted as part of [PendingCapture] so recovery and cleanup can apply the
 * correct handling for each input source.
 */
@Serializable
enum class CaptureSource {
    CAMERA,
    GALLERY,
    COPY
}
