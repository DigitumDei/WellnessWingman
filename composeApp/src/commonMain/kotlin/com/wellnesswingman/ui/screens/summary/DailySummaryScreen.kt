package com.wellnesswingman.ui.screens.summary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.detail.VoiceRecordingButton
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.datetime.LocalDate

data class DailySummaryScreen(val date: LocalDate) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<DailySummaryViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        val isGenerating by viewModel.isGenerating.collectAsState()
        val commentsState by viewModel.commentsState.collectAsState()

        viewModel.loadSummary(date)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Daily Summary") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (uiState is DailySummaryUiState.Success) {
                            IconButton(
                                onClick = { viewModel.regenerateSummary() },
                                enabled = !isGenerating
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Regenerate")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is DailySummaryUiState.Loading -> LoadingIndicator(Modifier.padding(paddingValues))
                is DailySummaryUiState.Generating -> GeneratingState(
                    date = state.date,
                    modifier = Modifier.padding(paddingValues)
                )
                is DailySummaryUiState.Success -> SummaryContent(
                    summary = state.summary,
                    date = state.date,
                    commentsState = commentsState,
                    onCommentsChange = viewModel::updateCommentsText,
                    onSaveComments = viewModel::saveComments,
                    onToggleRecording = viewModel::toggleRecording,
                    modifier = Modifier.padding(paddingValues)
                )
                is DailySummaryUiState.NoSummary -> NoSummaryState(
                    date = state.date,
                    onGenerate = { viewModel.generateSummary() },
                    isGenerating = isGenerating,
                    commentsState = commentsState,
                    onCommentsChange = viewModel::updateCommentsText,
                    onSaveComments = viewModel::saveComments,
                    onToggleRecording = viewModel::toggleRecording,
                    modifier = Modifier.padding(paddingValues)
                )
                is DailySummaryUiState.NoEntries -> NoEntriesState(
                    date = state.date,
                    modifier = Modifier.padding(paddingValues)
                )
                is DailySummaryUiState.Error -> ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.generateSummary() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun SummaryContent(
    summary: DailySummary,
    date: LocalDate,
    commentsState: CommentsState,
    onCommentsChange: (String) -> Unit,
    onSaveComments: () -> Unit,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date header
        Text(
            text = DateTimeUtil.formatDate(date),
            style = MaterialTheme.typography.headlineSmall
        )

        // Highlights
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Highlights",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = summary.highlights,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Recommendations
        if (summary.recommendations.isNotBlank()) {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Recommendations",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = summary.recommendations,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // User comments
        UserCommentsSection(
            commentsState = commentsState,
            onCommentsChange = onCommentsChange,
            onSaveComments = onSaveComments,
            onToggleRecording = onToggleRecording
        )
    }
}

@Composable
fun NoSummaryState(
    date: LocalDate,
    onGenerate: () -> Unit,
    isGenerating: Boolean,
    commentsState: CommentsState,
    onCommentsChange: (String) -> Unit,
    onSaveComments: () -> Unit,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "No summary for ${DateTimeUtil.formatDate(date)}",
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = onGenerate,
                enabled = !isGenerating
            ) {
                Text("Generate Summary")
            }
        }

        UserCommentsSection(
            commentsState = commentsState,
            onCommentsChange = onCommentsChange,
            onSaveComments = onSaveComments,
            onToggleRecording = onToggleRecording
        )
    }
}

@Composable
fun UserCommentsSection(
    commentsState: CommentsState,
    onCommentsChange: (String) -> Unit,
    onSaveComments: () -> Unit,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "My Notes",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = commentsState.text,
                onValueChange = onCommentsChange,
                label = { Text("Add notes about your day (or use the mic)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
                enabled = !commentsState.isRecording && !commentsState.isTranscribing
            )

            if (commentsState.transcriptionError != null) {
                Text(
                    text = commentsState.transcriptionError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceRecordingButton(
                    onToggleRecording = onToggleRecording,
                    isRecording = commentsState.isRecording,
                    isTranscribing = commentsState.isTranscribing,
                    recordingDurationSeconds = commentsState.recordingDurationSeconds,
                    enabled = true,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = onSaveComments,
                    enabled = commentsState.hasUnsavedChanges && !commentsState.isRecording && !commentsState.isTranscribing
                ) {
                    Text(if (commentsState.hasUnsavedChanges) "Save" else "Saved")
                }
            }
        }
    }
}

@Composable
fun NoEntriesState(
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No entries logged for ${DateTimeUtil.formatDate(date)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
fun GeneratingState(
    date: LocalDate,
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
                text = "Generating summary...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
