package com.wellnesswingman.ui.screens.nutrition

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.wellnesswingman.domain.capture.PendingCapture
import com.wellnesswingman.domain.capture.PendingCaptureStore
import com.wellnesswingman.platform.FileSystemOperations
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
actual fun NutritionLabelCaptureButtons(
    onCameraClickFallback: () -> Unit,
    onGalleryClickFallback: () -> Unit,
    onImageSelected: (photoPath: String?, bytes: ByteArray) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val pendingCaptureStore: PendingCaptureStore = koinInject()
    val fileSystem: FileSystemOperations = koinInject()
    val coroutineScope = rememberCoroutineScope()

    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    val cameraLauncherHolder = remember { mutableStateOf<androidx.activity.result.ActivityResultLauncher<android.net.Uri>?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            coroutineScope.launch {
                launchNutritionCameraCapture(
                    context = context,
                    pendingCaptureStore = pendingCaptureStore,
                    onPendingPhotoPathChanged = { pendingPhotoPath = it },
                    launch = { photoUri -> cameraLauncherHolder.value?.launch(photoUri) }
                )
            }
        } else {
            onError("Camera permission is required to take a nutrition label photo.")
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        coroutineScope.launch {
            try {
                val photoPath = pendingPhotoPath
                if (success && photoPath != null) {
                    val bytes = File(photoPath).readBytes()
                    onImageSelected(photoPath, bytes)
                } else if (!success && pendingPhotoPath != null) {
                    File(pendingPhotoPath!!).delete()
                }
            } catch (_: Exception) {
                onError("Failed to load the captured nutrition label photo.")
            } finally {
                pendingPhotoPath = null
                pendingCaptureStore.clear()
            }
        }
    }
    cameraLauncherHolder.value = cameraLauncher

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@launch onError("Failed to read the selected nutrition label image.")
                val photoPath = persistNutritionLabelPhoto(fileSystem, bytes)
                onImageSelected(photoPath, bytes)
            } catch (_: Exception) {
                onError("Failed to load the selected nutrition label image.")
            }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    coroutineScope.launch {
                        launchNutritionCameraCapture(
                            context = context,
                            pendingCaptureStore = pendingCaptureStore,
                            onPendingPhotoPathChanged = { pendingPhotoPath = it },
                            launch = cameraLauncher::launch
                        )
                    }
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.weight(1f)
        ) {
            Text("Camera")
        }
        OutlinedButton(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier.weight(1f)
        ) {
            Text("Gallery")
        }
    }
}

private suspend fun launchNutritionCameraCapture(
    context: android.content.Context,
    pendingCaptureStore: PendingCaptureStore,
    onPendingPhotoPathChanged: (String) -> Unit,
    launch: (android.net.Uri) -> Unit
) {
    val pendingDir = File(pendingCaptureStore.getPendingPhotosDirectory())
    val photoFile = File(pendingDir, "nutrition_label_${System.currentTimeMillis()}.jpg")
    val photoUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        photoFile
    )
    onPendingPhotoPathChanged(photoFile.absolutePath)
    pendingCaptureStore.save(
        PendingCapture(
            photoFilePath = photoFile.absolutePath,
            capturedAtMillis = System.currentTimeMillis()
        )
    )
    launch(photoUri)
}

private suspend fun persistNutritionLabelPhoto(
    fileSystem: FileSystemOperations,
    bytes: ByteArray
): String {
    val directory = "${fileSystem.getPhotosDirectory()}/nutrition-labels"
    fileSystem.createDirectory(directory)
    val path = "$directory/nutrition_label_${System.currentTimeMillis()}.jpg"
    fileSystem.writeBytes(path, bytes)
    return path
}
