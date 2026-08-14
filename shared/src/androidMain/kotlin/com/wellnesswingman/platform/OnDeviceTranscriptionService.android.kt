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
    private var onAutoComplete: ((Result<String?>) -> Unit)? = null
    private var stopRequested = false
    private var listening = false

    override suspend fun checkPermission(): Boolean = withContext(Dispatchers.Main.immediate) {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun startListening(onAutoComplete: (Result<String?>) -> Unit) = withContext(Dispatchers.Main.immediate) {
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
        this@AndroidOnDeviceTranscriptionService.onAutoComplete = onAutoComplete
        stopRequested = false
        newRecognizer.setRecognitionListener(listener)
        newRecognizer.startListening(recognitionIntent())
        recognizer = newRecognizer
        listening = true
    }

    override suspend fun stopListening(): String? {
        val pendingResult = withContext(Dispatchers.Main.immediate) {
            result?.also {
                if (listening) {
                    stopRequested = true
                    recognizer?.stopListening()
                }
            }
        } ?: return null

        return try {
            withTimeoutOrNull(10_000) { pendingResult.await() }
                ?: run {
                    cancel()
                    throw IllegalStateException("On-device speech recognition timed out")
                }
        } finally {
            withContext(Dispatchers.Main.immediate) {
                if (result === pendingResult) {
                    result = null
                    onAutoComplete = null
                    stopRequested = false
                }
            }
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
        onAutoComplete = null
        stopRequested = false
    }

    private fun complete(text: String?) {
        val pendingResult = result
        val callback = onAutoComplete
        val shouldNotify = !stopRequested
        recognizer?.destroy()
        recognizer = null
        listening = false
        pendingResult?.complete(text)
        if (shouldNotify) {
            result = null
            onAutoComplete = null
            stopRequested = false
            callback?.invoke(Result.success(text))
        }
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
            val callback = onAutoComplete
            val shouldNotify = !stopRequested
            val exception = IllegalStateException("On-device speech recognition failed (error $error)")
            recognizer?.destroy()
            recognizer = null
            listening = false
            pendingResult?.completeExceptionally(exception)
            if (shouldNotify) {
                result = null
                onAutoComplete = null
                stopRequested = false
                callback?.invoke(Result.failure(exception))
            }
        }
    }

    private fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }
}
