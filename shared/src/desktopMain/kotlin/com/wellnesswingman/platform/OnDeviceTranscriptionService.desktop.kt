package com.wellnesswingman.platform

class DesktopOnDeviceTranscriptionService : OnDeviceTranscriptionService {
    override suspend fun checkPermission(): Boolean = false

    override suspend fun startListening() {
        throw IllegalStateException("On-device speech recognition is not supported on Desktop")
    }

    override suspend fun stopListening(): String? = null

    override fun cancel() = Unit
}
