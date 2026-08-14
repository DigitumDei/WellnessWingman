package com.wellnesswingman.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Uses Android's on-device recognizer. It deliberately refuses to fall back to the network
 * recognizer because profile goals can contain sensitive health information.
 */
class AndroidOnDeviceTranscriptionService(
    private val context: Context
) : OnDeviceTranscriptionService {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var result: CompletableDeferred<String?>? = null
    private var listening = false

    override suspend fun checkPermission(): Boolean = withContext(Dispatchers.Main.immediate) {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun startListening() = withContext(Dispatchers.Main.immediate) {
        if (listening) return@withContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw IllegalStateException("On-device speech recognition requires Android 12 or later")
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            throw IllegalStateException("On-device speech recognition is unavailable on this device")
        }
        if (!checkPermission()) {
            throw IllegalStateException("Microphone permission not granted")
        }

        val newRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        result = CompletableDeferred()
        newRecognizer.setRecognitionListener(listener)
        newRecognizer.startListening(recognitionIntent())
        recognizer = newRecognizer
        listening = true
    }

    override suspend fun stopListening(): String? {
        val pendingResult = withContext(Dispatchers.Main.immediate) {
            if (!listening) {
                null
            } else {
                recognizer?.stopListening()
                result
            }
        } ?: return null

        return withTimeoutOrNull(10_000) { pendingResult.await() }
            ?: run {
                cancel()
                throw IllegalStateException("On-device speech recognition timed out")
            }
    }

    override fun cancel() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cancelOnMain()
        } else {
            mainHandler.post(::cancelOnMain)
        }
    }

    private fun cancelOnMain() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        listening = false
        result?.cancel()
        result = null
    }

    private fun complete(text: String?) {
        val pendingResult = result
        recognizer?.destroy()
        recognizer = null
        listening = false
        result = null
        pendingResult?.complete(text)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            complete(text)
        }

        override fun onError(error: Int) {
            val pendingResult = result
            recognizer?.destroy()
            recognizer = null
            listening = false
            result = null
            pendingResult?.completeExceptionally(
                IllegalStateException("On-device speech recognition failed (error $error)")
            )
        }
    }

    private fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }
}
