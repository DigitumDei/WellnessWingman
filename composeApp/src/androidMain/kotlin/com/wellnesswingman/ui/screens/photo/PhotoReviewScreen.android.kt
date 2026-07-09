package com.wellnesswingman.ui.screens.photo

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.wellnesswingman.domain.capture.CapturePhase
import com.wellnesswingman.domain.capture.CaptureSource
import com.wellnesswingman.domain.capture.PendingCapture
import com.wellnesswingman.domain.capture.PendingCaptureStore
import com.wellnesswingman.domain.capture.PhotoEntryProcessor
import com.wellnesswingman.platform.decodeWithExifRotation
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.detail.EntryDetailScreen
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

/**
 * Provides the Android-specific PhotoReviewScreen implementation.
 */
actual fun createPhotoReviewScreen(): Screen = PhotoReviewScreen()

/**
 * Android-specific PhotoReviewScreen with actual camera and gallery support.
 *
 * The in-progress workflow (photo file path, notes, workflow phase, resulting
 * entry id) is held in [rememberSaveable] state so it survives orientation
 * changes (activity recreation) without relying on in-memory composition state
 * or the ScreenModel scope. The pending-capture record is also persisted via
 * [PendingCaptureStore] so the workflow can be reconciled after process death.
 *
 * Image bytes are re-read from the persisted file path on recreation rather
 * than being retained only in composition. The heavy normalization, preview
 * generation, and entry insertion run in the app-scoped [PhotoEntryProcessor]
 * (which survives configuration changes) and are idempotent on the derived blob
 * path, so rotation during processing never creates a duplicate entry.
 */
private class PhotoReviewScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = getScreenModel<PhotoReviewViewModel>()
        val pendingCaptureStore: PendingCaptureStore = koinInject()
        val photoEntryProcessor: PhotoEntryProcessor = koinInject()
        val coroutineScope = rememberCoroutineScope()

        // Lifecycle-safe workflow state: survives configuration changes via the
        // saved state registry (Bundle). These are the durable source of truth.
        // Non-null primitives/strings with sentinels are used so they are
        // always saveable by the default state saver (nullable state is not
        // restored reliably across all configurations).
        var photoFilePath by rememberSaveable { mutableStateOf("") }
        var notes by rememberSaveable { mutableStateOf("") }
        var workflowPhase by rememberSaveable { mutableStateOf(WORKFLOW_IDLE) }
        var resultEntryId by rememberSaveable { mutableStateOf(0L) }
        var apiKeyMissing by rememberSaveable { mutableStateOf(false) }
        var errorMessage by rememberSaveable { mutableStateOf("") }

        // Recomputed (not persisted) — loaded from the persisted file path.
        var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
        var showPermissionDialog by remember { mutableStateOf(false) }

        // Commit the workflow: clear the recovery record, delete the now-redundant
        // pending photo file (the normalized blob already lives under photos/), and
        // navigate to the entry detail screen. Shared by the auto-navigate and the
        // API-key-missing dialog paths so cleanup runs exactly once. Guarded
        // against multiple triggers to prevent duplicate navigation actions.
        var isCommitting by remember { mutableStateOf(false) }
        val commitAndNavigate: (Long) -> Unit = { entryId ->
            if (!isCommitting) {
                isCommitting = true
                coroutineScope.launch {
                pendingCaptureStore.clear()
                val pendingPath = photoFilePath
                if (pendingPath.isNotEmpty()) {
                    withContext(Dispatchers.IO) { runCatching { File(pendingPath).delete() } }
                }
                navigator.replace(EntryDetailScreen(entryId))
                }
            }
        }

        // Re-read image bytes whenever the persisted photo path or review phase
        // changes. Re-keying on the phase handles the camera case, where the
        // file is created before launch but only populated on return. Runs off
        // the main thread to avoid blocking the UI.
        LaunchedEffect(photoFilePath, workflowPhase) {
            val path = photoFilePath
            imageBytes = if (path.isNotEmpty() && workflowPhase == WORKFLOW_REVIEW) {
                withContext(Dispatchers.IO) {
                    try {
                        val file = File(path)
                        if (file.exists()) file.readBytes().takeIf { it.isNotEmpty() } else null
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Napier.w("Failed to read photo file $path", e)
                        null
                    }
                }
            } else null
        }

        // Recovery on first composition (e.g. process death): if no saveable
        // workflow state survived, restore from the persisted pending capture.
        LaunchedEffect(Unit) {
            if (photoFilePath.isEmpty()) {
                val pending = pendingCaptureStore.get()
                if (pending != null) {
                    val file = File(pending.photoFilePath)
                    if (file.exists()) {
                        photoFilePath = pending.photoFilePath
                        notes = pending.notes
                        resultEntryId = pending.entryId ?: 0L
                        apiKeyMissing = pending.apiKeyMissing
                        workflowPhase = when (pending.phase) {
                            CapturePhase.DONE -> if (pending.entryId != null) WORKFLOW_DONE else WORKFLOW_PROCESSING
                            CapturePhase.PROCESSING -> WORKFLOW_PROCESSING
                            CapturePhase.CAPTURED -> WORKFLOW_REVIEW
                        }
                    } else {
                        // File gone; nothing to recover. Clear stale record.
                        pendingCaptureStore.clear()
                    }
                }
            }
        }

        // Processing: run the idempotent app-scoped processor. Re-entrant across
        // recreation because the processor coalesces calls for the same file and
        // returns the existing entry id if one was already persisted.
        LaunchedEffect(workflowPhase, photoFilePath) {
            if (workflowPhase == WORKFLOW_PROCESSING && resultEntryId == 0L && photoFilePath.isNotEmpty()) {
                val path = photoFilePath
                pendingCaptureStore.update { it?.copy(phase = CapturePhase.PROCESSING, notes = notes) }
                try {
                    val result = photoEntryProcessor.process(path, notes)
                    resultEntryId = result.entryId
                    apiKeyMissing = result.apiKeyMissing
                    workflowPhase = WORKFLOW_DONE
                    pendingCaptureStore.update { it?.copy(phase = CapturePhase.DONE, entryId = result.entryId, apiKeyMissing = result.apiKeyMissing) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Napier.e("Failed to create entry from photo", e)
                    errorMessage = e.message ?: "Unknown error"
                    workflowPhase = WORKFLOW_ERROR
                }
            }
        }

        // Done: navigate to the entry detail and clear the recovery record.
        // Only auto-navigates when analysis could be queued; the API-key-missing
        // case is gated by the dialog, which calls commitAndNavigate on dismiss.
        LaunchedEffect(workflowPhase, resultEntryId) {
            if (workflowPhase == WORKFLOW_DONE && resultEntryId != 0L && !apiKeyMissing) {
                commitAndNavigate(resultEntryId)
            }
        }

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
            if (success && photoFilePath.isNotEmpty()) {
                // Photo was written to our pending file; advance to review.
                workflowPhase = WORKFLOW_REVIEW
            } else {
                // Camera cancelled — clean up temp file and recovery state.
                coroutineScope.launch {
                    val path = photoFilePath
                    if (path.isNotEmpty()) {
                        withContext(Dispatchers.IO) { runCatching { File(path).delete() } }
                    }
                    pendingCaptureStore.clear()
                    photoFilePath = ""
                    notes = ""
                    workflowPhase = WORKFLOW_IDLE
                }
            }
        }

        // Gallery launcher
        val galleryLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val pendingDir = pendingCaptureStore.getPendingPhotosDirectory()
                    val timestamp = System.currentTimeMillis()
                    val destPath = "$pendingDir/gallery_$timestamp.jpg"
                    val copied = withContext(Dispatchers.IO) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                File(destPath).parentFile?.mkdirs()
                                inputStream.use { input ->
                                    File(destPath).outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                File(destPath).length() > 0L
                            } else {
                                false
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Napier.e("Failed to copy gallery image", e)
                            false
                        }
                    }
                    if (copied) {
                        photoFilePath = destPath
                        workflowPhase = WORKFLOW_REVIEW
                        pendingCaptureStore.save(
                            PendingCapture(
                                photoFilePath = destPath,
                                capturedAtMillis = timestamp,
                                source = CaptureSource.GALLERY
                            )
                        )
                    }
                }
            }
        }

        val onCameraClick: () -> Unit = {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                // Create a temp file in the persistent pending directory.
                val pendingDir = File(pendingCaptureStore.getPendingPhotosDirectory())
                val photoFile = File(pendingDir, "photo_${System.currentTimeMillis()}.jpg")
                val photoUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    photoFile
                )

                // Persist recovery state and the photo path BEFORE launching the
                // camera, so the workflow survives process death or recreation
                // while the system camera is in the foreground.
                photoFilePath = photoFile.absolutePath
                coroutineScope.launch {
                    pendingCaptureStore.save(
                        PendingCapture(
                            photoFilePath = photoFile.absolutePath,
                            capturedAtMillis = System.currentTimeMillis(),
                            source = CaptureSource.CAMERA
                        )
                    )
                    cameraLauncher.launch(photoUri)
                }
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        val onGalleryClick = {
            galleryLauncher.launch("image/*")
        }

        val onRetake: () -> Unit = {
            coroutineScope.launch {
                val path = photoFilePath
                if (path.isNotEmpty()) {
                    withContext(Dispatchers.IO) { runCatching { File(path).delete() } }
                }
                pendingCaptureStore.clear()
                photoFilePath = ""
                notes = ""
                resultEntryId = 0L
                apiKeyMissing = false
                errorMessage = ""
                workflowPhase = WORKFLOW_IDLE
            }
        }

        val onCancel: () -> Unit = {
            coroutineScope.launch {
                val path = photoFilePath
                if (path.isNotEmpty()) {
                    withContext(Dispatchers.IO) { runCatching { File(path).delete() } }
                }
                pendingCaptureStore.clear()
                navigator.pop()
            }
        }

        val onConfirm: (String) -> Unit = { confirmNotes ->
            notes = confirmNotes
            resultEntryId = 0L
            apiKeyMissing = false
            errorMessage = ""
            workflowPhase = WORKFLOW_PROCESSING
        }

        val onRetry: () -> Unit = {
            // Return to the review so the user can re-confirm. The processor is
            // idempotent, so re-submitting is safe even if a partial entry exists.
            resultEntryId = 0L
            errorMessage = ""
            workflowPhase = WORKFLOW_REVIEW
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Add Entry") },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (workflowPhase) {
                WORKFLOW_IDLE -> {
                    CaptureOptions(
                        onCameraClick = onCameraClick,
                        onGalleryClick = onGalleryClick,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                WORKFLOW_REVIEW -> {
                    val bytes = imageBytes
                    if (bytes != null) {
                        PhotoReview(
                            imageBytes = bytes,
                            notes = notes,
                            onNotesChange = { notes = it },
                            viewModel = viewModel,
                            onConfirm = onConfirm,
                            onRetake = onRetake,
                            onCancel = onCancel,
                            modifier = Modifier.padding(paddingValues)
                        )
                    } else {
                        LoadingIndicator(Modifier.padding(paddingValues))
                    }
                }
                WORKFLOW_PROCESSING -> {
                    LoadingIndicator(Modifier.padding(paddingValues))
                }
                WORKFLOW_DONE -> {
                    if (resultEntryId != 0L) {
                        if (apiKeyMissing) {
                            AlertDialog(
                                onDismissRequest = { commitAndNavigate(resultEntryId) },
                                title = { Text("API Key Required") },
                                text = { Text("No API key is configured. Your entry was saved but cannot be analyzed. Please add an API key in Settings to enable analysis.") },
                                confirmButton = {
                                    TextButton(onClick = { commitAndNavigate(resultEntryId) }) {
                                        Text("OK")
                                    }
                                }
                            )
                        } else {
                            // Navigation handled by the done LaunchedEffect; show
                            // a loading indicator while the transition runs.
                            LoadingIndicator(Modifier.padding(paddingValues))
                        }
                    } else {
                        LoadingIndicator(Modifier.padding(paddingValues))
                    }
                }
                WORKFLOW_ERROR -> {
                    ErrorMessage(
                        message = errorMessage.ifEmpty { "Unknown error" },
                        onRetry = onRetry,
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

private const val WORKFLOW_IDLE = "idle"
private const val WORKFLOW_REVIEW = "review"
private const val WORKFLOW_PROCESSING = "processing"
private const val WORKFLOW_DONE = "done"
private const val WORKFLOW_ERROR = "error"

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
    notes: String,
    onNotesChange: (String) -> Unit,
    viewModel: PhotoReviewViewModel,
    onConfirm: (String) -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val transcriptionError by viewModel.transcriptionError.collectAsState()

    // Append transcriptions into the hoisted notes field. Capture the latest
    // notes value via rememberUpdatedState so the collector always appends
    // to the current text rather than the stale snapshot captured at composition.
    val currentNotes by rememberUpdatedState(notes)
    LaunchedEffect(Unit) {
        viewModel.newTranscription.collect { transcription ->
            onNotesChange(if (currentNotes.isBlank()) transcription else "$currentNotes\n$transcription")
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleRecording()
        }
    }

    // Decode bitmap outside composable context, respecting EXIF orientation
    val bitmap = remember(imageBytes) {
        try {
            decodeWithExifRotation(imageBytes)
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
            // Notes field
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

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

            // Transcription error display
            if (transcriptionError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = transcriptionError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
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
                    Button(
                        onClick = { onConfirm(notes) },
                        enabled = !isTranscribing && !isRecording
                    ) {
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
