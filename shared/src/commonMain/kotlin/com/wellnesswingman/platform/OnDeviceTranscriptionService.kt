package com.wellnesswingman.platform

/**
 * Platform speech recognition that does not send audio to a remote provider.
 */
interface OnDeviceTranscriptionService {
    suspend fun checkPermission(): Boolean

    suspend fun startListening()

    suspend fun stopListening(): String?

    fun cancel()
}
