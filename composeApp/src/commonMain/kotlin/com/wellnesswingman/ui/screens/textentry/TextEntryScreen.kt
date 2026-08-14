package com.wellnesswingman.ui.screens.textentry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.ui.screens.detail.EntryDetailScreen
import com.wellnesswingman.ui.screens.detail.VoiceRecordingButton

/**
 * Adds an entry from a written or spoken description, with no photo.
 *
 * For when the thing happened but the photo did not: a meal eaten out, a run whose watch died.
 * The description goes to the same analysis that reads photos, which decides for itself whether
 * it is a meal, exercise or something else.
 */
class TextEntryScreen(private val initialText: String = "") : Screen {

    override val key: ScreenKey get() = "TextEntryScreen:${initialText.hashCode()}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<TextEntryViewModel> {
            org.koin.core.parameter.parametersOf(initialText)
        }
        val uiState by viewModel.uiState.collectAsState()
        val commentsState by viewModel.commentsState.collectAsState()

        val busy = commentsState.isRecording || commentsState.isTranscribing

        // Replace rather than push: the description screen has served its purpose once the entry
        // exists, and backing out of the detail screen should not return to a form that would
        // create a second entry.
        val openEntry: (Long) -> Unit = { entryId ->
            navigator.replace(EntryDetailScreen(entryId))
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Describe an entry") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "What did you have, or what did you do?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "The more specific the better — portions, times and distances all " +
                        "improve the estimate. Without a photo there is nothing else to go on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = commentsState.text,
                    onValueChange = viewModel::updateText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    placeholder = {
                        Text("e.g. two slices of pepperoni pizza and a beer, around 8pm")
                    },
                    enabled = !busy && !uiState.isSaving
                )

                commentsState.transcriptionError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                uiState.saveError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VoiceRecordingButton(
                        onToggleRecording = viewModel::toggleRecording,
                        isRecording = commentsState.isRecording,
                        isTranscribing = commentsState.isTranscribing,
                        recordingDurationSeconds = commentsState.recordingDurationSeconds,
                        enabled = !uiState.isSaving,
                        modifier = Modifier
                    )

                    Button(
                        onClick = { viewModel.save(openEntry) },
                        enabled = commentsState.text.isNotBlank() && !busy && !uiState.isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (uiState.isSaving) "Saving…" else "Save")
                    }
                }
            }

            // Saved, but nothing will analyse it. Said before landing on a detail screen that
            // would otherwise sit empty with no explanation.
            if (uiState.apiKeyMissing) {
                AlertDialog(
                    onDismissRequest = { viewModel.acknowledgeApiKeyMissing(openEntry) },
                    title = { Text("API Key Required") },
                    text = {
                        Text(
                            "No API key is configured. Your entry was saved but cannot be " +
                                "analyzed. Please add an API key in Settings to enable analysis."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.acknowledgeApiKeyMissing(openEntry) }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}
