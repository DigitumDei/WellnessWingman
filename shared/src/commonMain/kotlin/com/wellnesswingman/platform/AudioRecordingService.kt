package com.wellnesswingman.platform

enum class AudioRecordingStatus {
    SUCCESS, CANCELLED, PERMISSION_DENIED, MICROPHONE_IN_USE, FAILED
}

sealed class AudioRecordingResult {
    data class Success(val audioFilePath: String) : AudioRecordingResult()
    data class Error(val status: AudioRecordingStatus, val message: String) : AudioRecordingResult()

    val isSuccess: Boolean get() = this is Success
    val filePath: String? get() = (this as? Success)?.audioFilePath
}

expect class AudioRecordingService {
    suspend fun checkPermission(): Boolean
    suspend fun requestPermission(): Boolean
    suspend fun startRecording(outputFilePath: String): Boolean
    suspend fun stopRecording(): AudioRecordingResult
    fun isRecording(): Boolean
}
