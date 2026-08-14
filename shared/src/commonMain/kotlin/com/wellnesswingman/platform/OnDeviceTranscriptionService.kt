package com.wellnesswingman.platform

/**
 * Platform speech recognition that does not send audio to a remote provider.
 */
interface OnDeviceTranscriptionService {
    suspend fun checkPermission(): Boolean

    suspend fun startListening(onAutoComplete: (Result<String?>) -> Unit)

    suspend fun stopListening(): String?

    fun cancel()
}
