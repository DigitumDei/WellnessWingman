package com.wellnesswingman.platform

import io.github.aakira.napier.Napier

actual class AudioRecordingService {
    actual suspend fun checkPermission(): Boolean {
        Napier.w("AudioRecordingService not implemented for Desktop")
        return false
    }

    actual suspend fun requestPermission(): Boolean {
        Napier.w("AudioRecordingService not implemented for Desktop")
        return false
    }

    actual suspend fun startRecording(outputFilePath: String): Boolean {
        Napier.w("AudioRecordingService not implemented for Desktop")
        return false
    }

    actual suspend fun stopRecording(): AudioRecordingResult {
        Napier.w("AudioRecordingService not implemented for Desktop")
        return AudioRecordingResult.Error(
            AudioRecordingStatus.FAILED,
            "Audio recording not supported on Desktop yet"
        )
    }

    actual fun isRecording(): Boolean = false
}
