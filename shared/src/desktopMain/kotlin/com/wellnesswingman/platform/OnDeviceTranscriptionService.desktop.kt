package com.wellnesswingman.platform

class DesktopOnDeviceTranscriptionService : OnDeviceTranscriptionService {
    override suspend fun checkPermission(): Boolean = true

    override suspend fun startListening(onAutoComplete: (Result<String?>) -> Unit) {
        throw IllegalStateException("On-device speech recognition is not supported on Desktop")
    }

    override suspend fun stopListening(): String? = null

    override fun cancel() = Unit
}
