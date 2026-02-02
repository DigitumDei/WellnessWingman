package com.wellnesswingman.ui.screens.photo

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.detail.EntryDetailScreen
import java.io.File

/**
 * Provides the Android-specific PhotoReviewScreen implementation.
 */
actual fun createPhotoReviewScreen(): Screen = PhotoReviewScreen()

/**
 * Android-specific PhotoReviewScreen with actual camera and gallery support.
 */
private class PhotoReviewScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = getScreenModel<PhotoReviewViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        var capturedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
        var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
        var showPermissionDialog by remember { mutableStateOf(false) }

        // Camera permission launcher
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                showPermissionDialog = true
            }
        }

        // Camera launcher
        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success && capturedImageUri != null) {
                try {
                    val bytes = context.contentResolver.openInputStream(capturedImageUri!!)?.use {
                        it.readBytes()
                    }
                    if (bytes != null) {
                        capturedImageBytes = bytes
                    }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }

        // Gallery launcher
        val galleryLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    }
                    if (bytes != null) {
                        capturedImageBytes = bytes
                        capturedImageUri = uri
                    }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }

        // Handle camera button click
        val onCameraClick = {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                // Create temp file and launch camera
                val photoFile = File.createTempFile(
                    "photo_${System.currentTimeMillis()}",
                    ".jpg",
                    context.cacheDir
                )
                val photoUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    photoFile
                )
                capturedImageUri = photoUri
                cameraLauncher.launch(photoUri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Handle gallery button click
        val onGalleryClick = {
            galleryLauncher.launch("image/*")
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Add Entry") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is PhotoReviewUiState.Initial -> {
                    if (capturedImageBytes != null) {
                        PhotoReview(
                            imageBytes = capturedImageBytes!!,
                            viewModel = viewModel,
                            onConfirm = { entryType, notes ->
                                viewModel.createEntryFromPhoto(
                                    photoBytes = capturedImageBytes!!,
                                    entryType = entryType,
                                    userNotes = notes
                                )
                            },
                            onRetake = {
                                capturedImageBytes = null
                                capturedImageUri = null
                            },
                            onCancel = { navigator.pop() },
                            modifier = Modifier.padding(paddingValues)
                        )
                    } else {
                        CaptureOptions(
                            onCameraClick = onCameraClick,
                            onGalleryClick = onGalleryClick,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }
                is PhotoReviewUiState.Processing -> {
                    LoadingIndicator(Modifier.padding(paddingValues))
                }
                is PhotoReviewUiState.Success -> {
                    LaunchedEffect(state.entryId) {
                        navigator.replace(EntryDetailScreen(state.entryId))
                    }
                }
                is PhotoReviewUiState.Error -> {
                    ErrorMessage(
                        message = state.message,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                else -> {
                    CaptureOptions(
                        onCameraClick = onCameraClick,
                        onGalleryClick = onGalleryClick,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Camera Permission Required") },
                text = { Text("Please grant camera permission in Settings to capture photos.") },
                confirmButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun CaptureOptions(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedButton(
            onClick = onCameraClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(bottom = 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Take Photo", style = MaterialTheme.typography.titleLarge)
            }
        }

        OutlinedButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Photo,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choose from Gallery", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun PhotoReview(
    imageBytes: ByteArray,
    viewModel: PhotoReviewViewModel,
    onConfirm: (EntryType, String) -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by remember { mutableStateOf(EntryType.MEAL) }
    var notes by remember { mutableStateOf("") }

    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val transcribedText by viewModel.transcribedText.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleRecording()
        }
    }

    // Decode bitmap outside composable context
    val bitmap = remember(imageBytes) {
        try {
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Image preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("Error loading image", modifier = Modifier.align(Alignment.Center))
            }
        }

        // Controls
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Entry type selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    selected = selectedType == EntryType.MEAL,
                    onClick = { selectedType = EntryType.MEAL },
                    label = { Text("Meal") }
                )
                FilterChip(
                    selected = selectedType == EntryType.EXERCISE,
                    onClick = { selectedType = EntryType.EXERCISE },
                    label = { Text("Exercise") }
                )
                FilterChip(
                    selected = selectedType == EntryType.SLEEP,
                    onClick = { selectedType = EntryType.SLEEP },
                    label = { Text("Sleep") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notes field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            // Voice note transcription display (if exists)
            if (transcribedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Voice notes: $transcribedText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Voice recording button
            OutlinedButton(
                onClick = {
                    if (isRecording) {
                        viewModel.toggleRecording()
                    } else {
                        if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.toggleRecording()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTranscribing,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isRecording)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        Color.Transparent
                )
            ) {
                if (isTranscribing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Transcribing...")
                } else {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop recording" else "Record voice note"
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isRecording)
                            "Recording ${formatDuration(recordingDuration)}"
                        else
                            "Add Voice Note"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = onRetake) {
                    Icon(Icons.Default.Refresh, "Retake")
                    Spacer(Modifier.width(4.dp))
                    Text("Retake")
                }

                Row {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(selectedType, notes) }) {
                        Icon(Icons.Default.Check, "Confirm")
                        Spacer(Modifier.width(4.dp))
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return String.format("%d:%02d", seconds / 60, seconds % 60)
}
