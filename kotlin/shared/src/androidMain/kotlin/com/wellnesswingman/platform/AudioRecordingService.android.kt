package com.wellnesswingman.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class AudioRecordingService(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentOutputPath: String? = null
    private var isCurrentlyRecording = false

    actual suspend fun checkPermission(): Boolean = withContext(Dispatchers.IO) {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    actual suspend fun requestPermission(): Boolean {
        // Permission requests must be handled by the Activity/UI layer
        // This method exists for API compatibility but returns current permission state
        return checkPermission()
    }

    actual suspend fun startRecording(outputFilePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isCurrentlyRecording) {
                Napier.w("Recording already in progress")
                return@withContext false
            }

            if (!checkPermission()) {
                Napier.w("RECORD_AUDIO permission not granted")
                return@withContext false
            }

            // Create parent directory if it doesn't exist
            val outputFile = File(outputFilePath)
            outputFile.parentFile?.mkdirs()

            // Initialize MediaRecorder
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setAudioEncodingBitRate(64000)
                setOutputFile(outputFilePath)

                try {
                    prepare()
                    start()
                    currentOutputPath = outputFilePath
                    isCurrentlyRecording = true
                    Napier.i("Recording started: $outputFilePath")
                    return@withContext true
                } catch (e: Exception) {
                    Napier.e("Failed to start MediaRecorder", e)
                    release()
                    recorder = null
                    return@withContext false
                }
            }

            false
        } catch (e: Exception) {
            Napier.e("Failed to start recording", e)
            false
        }
    }

    actual suspend fun stopRecording(): AudioRecordingResult = withContext(Dispatchers.IO) {
        try {
            if (!isCurrentlyRecording || recorder == null) {
                Napier.w("No recording in progress")
                return@withContext AudioRecordingResult.Error(
                    AudioRecordingStatus.FAILED,
                    "No recording in progress"
                )
            }

            val outputPath = currentOutputPath
            if (outputPath == null) {
                Napier.e("Output path is null")
                return@withContext AudioRecordingResult.Error(
                    AudioRecordingStatus.FAILED,
                    "Output path is null"
                )
            }

            try {
                recorder?.stop()
                recorder?.release()
            } catch (e: Exception) {
                Napier.e("Error stopping MediaRecorder", e)
            } finally {
                recorder = null
                isCurrentlyRecording = false
                currentOutputPath = null
            }

            // Validate the output file
            val outputFile = File(outputPath)
            if (!outputFile.exists()) {
                Napier.e("Output file does not exist: $outputPath")
                return@withContext AudioRecordingResult.Error(
                    AudioRecordingStatus.FAILED,
                    "Output file was not created"
                )
            }

            if (outputFile.length() == 0L) {
                Napier.e("Output file is empty: $outputPath")
                outputFile.delete()
                return@withContext AudioRecordingResult.Error(
                    AudioRecordingStatus.FAILED,
                    "Recording produced no audio data"
                )
            }

            Napier.i("Recording stopped successfully: $outputPath (${outputFile.length()} bytes)")
            AudioRecordingResult.Success(outputPath)

        } catch (e: Exception) {
            Napier.e("Failed to stop recording", e)
            recorder?.release()
            recorder = null
            isCurrentlyRecording = false
            currentOutputPath = null
            AudioRecordingResult.Error(
                AudioRecordingStatus.FAILED,
                e.message ?: "Unknown error"
            )
        }
    }

    actual fun isRecording(): Boolean = isCurrentlyRecording
}
