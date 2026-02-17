package com.wellnesswingman

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.wellnesswingman.domain.capture.PendingCapture
import com.wellnesswingman.domain.capture.PendingCaptureStore
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.ui.App
import io.github.aakira.napier.Napier
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainActivity : ComponentActivity(), KoinComponent {

    private val pendingCaptureStore: PendingCaptureStore by inject()
    private val fileSystem: FileSystem by inject()

    private val activityScope = MainScope()

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
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return

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
                        saveSharedImageAsPendingCapture(uri)
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
                        saveSharedImageAsPendingCapture(firstUri)
                    }
                }
            }
        }
    }

    private fun saveSharedImageAsPendingCapture(uri: Uri) {
        activityScope.launch {
            try {
                val imageBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (imageBytes == null) {
                    Napier.e("Failed to read shared image bytes from URI: $uri")
                    return@launch
                }

                val photosDir = pendingCaptureStore.getPendingPhotosDirectory()
                val timestamp = System.currentTimeMillis()
                val filePath = "$photosDir/shared_$timestamp.jpg"

                fileSystem.writeBytes(filePath, imageBytes)

                val pendingCapture = PendingCapture(
                    photoFilePath = filePath,
                    capturedAtMillis = timestamp
                )
                pendingCaptureStore.save(pendingCapture)

                Napier.d("Saved shared image as pending capture: $filePath")
            } catch (e: Exception) {
                Napier.e("Failed to handle shared image", e)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
