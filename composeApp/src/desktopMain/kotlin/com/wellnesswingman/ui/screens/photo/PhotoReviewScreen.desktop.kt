package com.wellnesswingman.ui.screens.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.detail.EntryDetailScreen
import com.wellnesswingman.ui.screens.textentry.TextEntryScreen
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

actual fun createPhotoReviewScreen(): Screen = DesktopPhotoReviewScreen()

private class DesktopPhotoReviewScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<PhotoReviewViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        val previousEntriesState by viewModel.previousEntriesState.collectAsState()
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
        var showPreviousEntries by remember { mutableStateOf(false) }
        var isPreparingPrevious by remember { mutableStateOf(false) }
        var previousCopyError by remember { mutableStateOf("") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Add Entry") }
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is PhotoReviewUiState.Initial,
                is PhotoReviewUiState.Cancelled -> DesktopCaptureOptions(
                    onCameraClick = viewModel::captureFromCamera,
                    onGalleryClick = viewModel::pickFromGallery,
                    onCopyPreviousClick = {
                        showPreviousEntries = true
                        previousCopyError = ""
                        viewModel.loadPreviousEntries()
                    },
                    modifier = Modifier.padding(paddingValues),
                    // Replace, not push: this screen's job was to choose a source, and backing
                    // out of the description should return to the day, not here.
                    onDescribeClick = { navigator.replace(TextEntryScreen()) }
                )

                is PhotoReviewUiState.Capturing,
                is PhotoReviewUiState.Picking,
                is PhotoReviewUiState.Processing -> LoadingIndicator(Modifier.padding(paddingValues))

                is PhotoReviewUiState.Review -> DesktopPhotoReview(
                    imageBytes = state.photoBytes,
                    initialNotes = state.initialNotes,
                    onConfirm = viewModel::confirmPhoto,
                    onRetake = viewModel::retry,
                    onCancel = {
                        viewModel.cancel()
                        navigator.pop()
                    },
                    modifier = Modifier.padding(paddingValues)
                )

                is PhotoReviewUiState.Success -> {
                    if (state.apiKeyMissing) {
                        AlertDialog(
                            onDismissRequest = {
                                navigator.replace(EntryDetailScreen(state.entryId))
                            },
                            title = { Text("API Key Required") },
                            text = {
                                Text(
                                    "Your entry was saved, but analysis is disabled until an API key is configured in Settings."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    navigator.replace(EntryDetailScreen(state.entryId))
                                }) {
                                    Text("OK")
                                }
                            }
                        )
                    } else {
                        LaunchedEffect(state.entryId) {
                            navigator.replace(EntryDetailScreen(state.entryId))
                        }
                    }
                }

                is PhotoReviewUiState.Error -> ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }

        if (showPreviousEntries) {
            DesktopPreviousEntriesDialog(
                state = previousEntriesState,
                isPreparing = isPreparingPrevious,
                onDismiss = { showPreviousEntries = false },
                onSelect = { entry ->
                    if (entry.blobPath == null) {
                        showPreviousEntries = false
                        navigator.replace(TextEntryScreen(entry.userNotes.orEmpty()))
                    } else if (!isPreparingPrevious) {
                        isPreparingPrevious = true
                        coroutineScope.launch {
                            try {
                                if (!viewModel.preparePreviousEntry(entry)) {
                                    previousCopyError = "That previous photo is no longer available."
                                }
                            } finally {
                                isPreparingPrevious = false
                                showPreviousEntries = false
                            }
                        }
                    }
                }
            )
        }

        if (previousCopyError.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { previousCopyError = "" },
                title = { Text("Could not copy entry") },
                text = { Text(previousCopyError) },
                confirmButton = {
                    TextButton(onClick = { previousCopyError = "" }) { Text("OK") }
                }
            )
        }
    }
}

@Composable
private fun DesktopCaptureOptions(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onCopyPreviousClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDescribeClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "How do you want to add this?",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onCameraClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Take Photo")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onGalleryClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Choose from Gallery")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onCopyPreviousClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Copy from previous (last ${PhotoReviewViewModel.DEFAULT_PREVIOUS_ENTRY_LIMIT})"
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        // For when the thing happened but the photo did not.
        OutlinedButton(
            onClick = onDescribeClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Describe it instead (no photo)")
        }
    }
}

@Composable
private fun DesktopPhotoReview(
    imageBytes: ByteArray,
    initialNotes: String,
    onConfirm: (String) -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var notes by remember(imageBytes, initialNotes) { mutableStateOf(initialNotes) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Image selected",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Preview is not implemented on desktop yet. Selected image size: ${imageBytes.size} bytes.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onConfirm(notes) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Entry")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRetake,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Choose Another Photo")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun DesktopPreviousEntriesDialog(
    state: PreviousEntriesState,
    isPreparing: Boolean,
    onDismiss: () -> Unit,
    onSelect: (TrackedEntry) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.isLoading && !isPreparing) onDismiss() },
        title = { Text("Copy from previous") },
        text = {
            when {
                state.isLoading || isPreparing -> CircularProgressIndicator()
                state.error != null -> Text(state.error)
                state.entries.isEmpty() -> Text("No previous entries found.")
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(state.entries, key = { it.entryId }) { entry ->
                        val hasPhoto = entry.blobPath != null
                        val hasText = !entry.userNotes.isNullOrBlank()
                        TextButton(
                            onClick = { onSelect(entry) },
                            enabled = hasPhoto || hasText,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = androidx.compose.ui.Alignment.Start
                            ) {
                                Text(
                                    DateTimeUtil.formatDateTime(
                                        entry.capturedAt,
                                        TimeZone.currentSystemDefault()
                                    ),
                                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    entry.userNotes?.take(90)?.ifBlank { "No notes" } ?: "No notes",
                                    maxLines = 2,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    if (hasPhoto) "Photo + notes" else "Text only",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !state.isLoading && !isPreparing) { Text("Cancel") }
        }
    )
}
