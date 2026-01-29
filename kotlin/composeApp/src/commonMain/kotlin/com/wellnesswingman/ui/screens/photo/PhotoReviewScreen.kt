package com.wellnesswingman.ui.screens.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.detail.EntryDetailScreen

class PhotoReviewScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<PhotoReviewViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        // Handle navigation based on state
        LaunchedEffect(uiState) {
            when (val state = uiState) {
                is PhotoReviewUiState.Success -> {
                    navigator.replace(EntryDetailScreen(state.entryId))
                }
                is PhotoReviewUiState.Cancelled -> {
                    navigator.pop()
                }
                else -> {}
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Add Photo") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.cancel() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is PhotoReviewUiState.Initial -> InitialState(
                    onCameraClick = { viewModel.captureFromCamera() },
                    onGalleryClick = { viewModel.pickFromGallery() },
                    modifier = Modifier.padding(paddingValues)
                )
                is PhotoReviewUiState.Capturing -> LoadingState(
                    message = "Opening camera...",
                    modifier = Modifier.padding(paddingValues)
                )
                is PhotoReviewUiState.Picking -> LoadingState(
                    message = "Opening gallery...",
                    modifier = Modifier.padding(paddingValues)
                )
                is PhotoReviewUiState.Review -> ReviewState(
                    photoBytes = state.photoBytes,
                    onConfirm = { entryType, notes ->
                        viewModel.confirmPhoto(entryType, notes)
                    },
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.padding(paddingValues)
                )
                is PhotoReviewUiState.Processing -> LoadingState(
                    message = "Creating entry...",
                    modifier = Modifier.padding(paddingValues)
                )
                is PhotoReviewUiState.Error -> ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.padding(paddingValues)
                )
                else -> {} // Success and Cancelled handled by LaunchedEffect
            }
        }
    }
}

@Composable
fun InitialState(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Choose a photo source",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
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
                    Icons.Default.Camera,
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
                    Icons.Default.PhotoLibrary,
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
fun ReviewState(
    photoBytes: ByteArray,
    onConfirm: (EntryType, String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by remember { mutableStateOf(EntryType.MEAL) }
    var notes by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Photo preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // TODO: Convert ByteArray to ImageBitmap for display
            // This requires platform-specific implementation
            Text(
                text = "Photo Preview\n(${photoBytes.size} bytes)",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Controls
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Entry Type",
                    style = MaterialTheme.typography.titleMedium
                )

                // Entry type selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == EntryType.MEAL,
                        onClick = { selectedType = EntryType.MEAL },
                        label = { Text("Meal") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == EntryType.EXERCISE,
                        onClick = { selectedType = EntryType.EXERCISE },
                        label = { Text("Exercise") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == EntryType.SLEEP,
                        onClick = { selectedType = EntryType.SLEEP },
                        label = { Text("Sleep") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Notes input
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }

                    Button(
                        onClick = { onConfirm(selectedType, notes) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
