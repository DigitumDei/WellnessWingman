package com.wellnesswingman.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android-specific camera and gallery helper using Jetpack Compose.
 */
@Composable
fun rememberCameraLauncher(
    context: Context,
    onResult: (success: Boolean, uri: Uri?, bytes: ByteArray?) -> Unit
): Pair<ManagedActivityResultLauncher<Uri, Boolean>, () -> Uri> {
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(photoUri!!)?.use { it.readBytes() }
                onResult(true, photoUri, bytes)
            } catch (e: Exception) {
                onResult(false, null, null)
            }
        } else {
            onResult(false, null, null)
        }
    }

    val createPhotoUri: () -> Uri = {
        val photoFile = File.createTempFile(
            "photo_${System.currentTimeMillis()}",
            ".jpg",
            context.cacheDir
        )
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            photoFile
        )
        photoUri = uri
        uri
    }

    return Pair(launcher, createPhotoUri)
}

@Composable
fun rememberGalleryLauncher(
    context: Context,
    onResult: (uri: Uri?, bytes: ByteArray?) -> Unit
): ManagedActivityResultLauncher<String, Uri?> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                onResult(uri, bytes)
            } catch (e: Exception) {
                onResult(null, null)
            }
        } else {
            onResult(null, null)
        }
    }
}

@Composable
fun rememberCameraPermissionLauncher(
    onPermissionResult: (granted: Boolean) -> Unit
): ManagedActivityResultLauncher<String, Boolean> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionResult(granted)
    }
}
