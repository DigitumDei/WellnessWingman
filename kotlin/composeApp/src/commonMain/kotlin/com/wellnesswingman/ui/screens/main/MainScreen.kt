package com.wellnesswingman.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
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
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.ui.components.EmptyState
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.detail.EntryDetailScreen
import com.wellnesswingman.ui.screens.settings.SettingsScreen
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.datetime.TimeZone

class MainScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<MainViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        val isRefreshing by viewModel.isRefreshing.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("WellnessWingman") },
                    actions = {
                        IconButton(onClick = { navigator.push(SettingsScreen()) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { /* TODO: Navigate to add entry */ }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Entry")
                }
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is MainUiState.Loading -> LoadingIndicator(Modifier.padding(paddingValues))
                is MainUiState.Empty -> EmptyState(
                    message = "No entries yet. Start tracking your wellness!",
                    modifier = Modifier.padding(paddingValues)
                )
                is MainUiState.Error -> ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.loadEntries() },
                    modifier = Modifier.padding(paddingValues)
                )
                is MainUiState.Success -> EntryList(
                    entries = state.entries,
                    onEntryClick = { entry -> navigator.push(EntryDetailScreen(entry.entryId)) },
                    onRefresh = { viewModel.refresh() },
                    isRefreshing = isRefreshing,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun EntryList(
    entries: List<TrackedEntry>,
    onEntryClick: (TrackedEntry) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(entries, key = { it.entryId }) { entry ->
            EntryCard(
                entry = entry,
                onClick = { onEntryClick(entry) }
            )
        }
    }
}

@Composable
fun EntryCard(
    entry: TrackedEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.entryType.name,
                    style = MaterialTheme.typography.titleMedium
                )

                StatusChip(status = entry.processingStatus)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = DateTimeUtil.formatDateTime(entry.capturedAt, TimeZone.currentSystemDefault()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (entry.userNotes != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = entry.userNotes!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun StatusChip(status: ProcessingStatus) {
    val (text, color) = when (status) {
        ProcessingStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.secondary
        ProcessingStatus.PROCESSING -> "Processing" to MaterialTheme.colorScheme.primary
        ProcessingStatus.COMPLETED -> "Completed" to MaterialTheme.colorScheme.tertiary
        ProcessingStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
        ProcessingStatus.SKIPPED -> "Skipped" to MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
