package com.wellnesswingman

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.wellnesswingman.domain.capture.PendingCapture
import com.wellnesswingman.domain.capture.PendingCaptureStore
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.ui.App
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainActivity : ComponentActivity(), KoinComponent {

    private val pendingCaptureStore: PendingCaptureStore by inject()
    private val fileSystem: FileSystem by inject()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Napier.d("POST_NOTIFICATIONS permission granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        handleShareIntent(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleShareIntent(intent)) {
            recreate()
        }
    }

    private fun handleShareIntent(intent: Intent?): Boolean {
        if (intent == null) return false

        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (uri != null) {
                        return saveSharedImageAsPendingCapture(uri)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    val firstUri = uris?.firstOrNull()
                    if (firstUri != null) {
                        return saveSharedImageAsPendingCapture(firstUri)
                    }
                }
            }
        }
        return false
    }

    /**
     * Saves the shared image synchronously so the pending capture file exists
     * before the Compose UI and MainViewModel initialize.
     *
     * Retries reading the URI up to [MAX_SHARE_READ_RETRIES] times with a short
     * delay between attempts to handle the race condition where the sharing app
     * hasn't finished writing the image file when the intent is delivered.
     */
    private fun saveSharedImageAsPendingCapture(uri: Uri): Boolean {
        return runBlocking(Dispatchers.IO) {
            var imageBytes: ByteArray? = null
            for (attempt in 0 until MAX_SHARE_READ_RETRIES) {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        imageBytes = bytes
                        break
                    }
                    Napier.w("Shared image not ready (attempt ${attempt + 1}/$MAX_SHARE_READ_RETRIES)")
                } catch (e: Exception) {
                    Napier.w("Error reading shared image (attempt ${attempt + 1}/$MAX_SHARE_READ_RETRIES): ${e.message}")
                }
                if (attempt < MAX_SHARE_READ_RETRIES - 1) delay(SHARE_READ_RETRY_DELAY_MS)
            }

            val bytes = imageBytes
            if (bytes == null) {
                Napier.e("Failed to read shared image from URI after $MAX_SHARE_READ_RETRIES attempts: $uri")
                return@runBlocking false
            }

            try {
                val photosDir = pendingCaptureStore.getPendingPhotosDirectory()
                val timestamp = System.currentTimeMillis()
                val mimeType = contentResolver.getType(uri)
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
                val filePath = "$photosDir/shared_$timestamp.$extension"

                fileSystem.writeBytes(filePath, bytes)
                pendingCaptureStore.save(
                    PendingCapture(
                        photoFilePath = filePath,
                        capturedAtMillis = timestamp
                    )
                )

                Napier.d("Saved shared image as pending capture: $filePath")
                true
            } catch (e: Exception) {
                Napier.e("Failed to save shared image", e)
                false
            }
        }
    }

    companion object {
        private const val MAX_SHARE_READ_RETRIES = 5
        private const val SHARE_READ_RETRY_DELAY_MS = 200L
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
