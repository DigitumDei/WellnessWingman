package com.wellnesswingman.ui.screens.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

actual fun createPhotoReviewScreen(): Screen = DesktopPhotoReviewScreen()

private class DesktopPhotoReviewScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<PhotoReviewViewModel>()
        val uiState by viewModel.uiState.collectAsState()

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
                    modifier = Modifier.padding(paddingValues)
                )

                is PhotoReviewUiState.Capturing,
                is PhotoReviewUiState.Picking,
                is PhotoReviewUiState.Processing -> LoadingIndicator(Modifier.padding(paddingValues))

                is PhotoReviewUiState.Review -> DesktopPhotoReview(
                    imageBytes = state.photoBytes,
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
    }
}

@Composable
private fun DesktopCaptureOptions(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Choose an image source",
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
    }
}

@Composable
private fun DesktopPhotoReview(
    imageBytes: ByteArray,
    onConfirm: (String) -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var notes by remember { mutableStateOf("") }

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
