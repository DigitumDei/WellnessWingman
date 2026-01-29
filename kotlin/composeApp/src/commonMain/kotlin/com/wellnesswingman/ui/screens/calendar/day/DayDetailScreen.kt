package com.wellnesswingman.ui.screens.calendar.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
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
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.ui.components.EmptyState
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.detail.EntryDetailScreen
import com.wellnesswingman.ui.screens.main.EntryCard
import com.wellnesswingman.ui.screens.summary.DailySummaryScreen
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.datetime.LocalDate

data class DayDetailScreen(val date: LocalDate) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<DayDetailViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        // Load entries for this date
        viewModel.loadDay(date)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(DateTimeUtil.formatDate(date)) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { navigator.push(DailySummaryScreen()) }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "View Summary")
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is DayDetailUiState.Loading -> LoadingIndicator(Modifier.padding(paddingValues))
                is DayDetailUiState.Success -> DayEntryList(
                    entries = state.entries,
                    onEntryClick = { entry -> navigator.push(EntryDetailScreen(entry.entryId)) },
                    modifier = Modifier.padding(paddingValues)
                )
                is DayDetailUiState.Empty -> EmptyState(
                    message = "No entries for ${DateTimeUtil.formatDate(date)}",
                    modifier = Modifier.padding(paddingValues)
                )
                is DayDetailUiState.Error -> ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.loadDay(date) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun DayEntryList(
    entries: List<TrackedEntry>,
    onEntryClick: (TrackedEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "${entries.size} ${if (entries.size == 1) "entry" else "entries"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(entries, key = { it.entryId }) { entry ->
            EntryCard(
                entry = entry,
                onClick = { onEntryClick(entry) }
            )
        }
    }
}
