package com.wellnesswingman.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.calendar.day.DayDetailScreen
import com.wellnesswingman.ui.screens.main.EntryCard
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.days

class WeekViewScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<WeekViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        val currentWeek by viewModel.currentWeekStart.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Week View") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.previousWeek() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Week")
                        }
                        IconButton(onClick = { viewModel.today() }) {
                            Icon(Icons.Default.Today, contentDescription = "Today")
                        }
                        IconButton(onClick = { viewModel.nextWeek() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Week")
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is WeekUiState.Loading -> LoadingIndicator(Modifier.padding(paddingValues))
                is WeekUiState.Success -> WeekContent(
                    weekStart = state.weekStart,
                    entriesByDate = state.entriesByDate,
                    onDateClick = { date -> navigator.push(DayDetailScreen(date)) },
                    onEntryClick = { entry -> navigator.push(com.wellnesswingman.ui.screens.detail.EntryDetailScreen(entry.entryId)) },
                    modifier = Modifier.padding(paddingValues)
                )
                is WeekUiState.Error -> ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.loadWeek(currentWeek) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun WeekContent(
    weekStart: LocalDate,
    entriesByDate: Map<LocalDate, List<TrackedEntry>>,
    onDateClick: (LocalDate) -> Unit,
    onEntryClick: (TrackedEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Week header
        item {
            val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
            Text(
                text = "${DateTimeUtil.formatDate(weekStart)} - ${DateTimeUtil.formatDate(weekEnd)}",
                style = MaterialTheme.typography.titleLarge
            )
        }

        // Days of the week
        items(7) { dayOffset ->
            val date = weekStart.plus(dayOffset, DateTimeUnit.DAY)
            val entries = entriesByDate[date] ?: emptyList()
            val isToday = date == Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date

            DaySection(
                date = date,
                entries = entries,
                isToday = isToday,
                onDateClick = { onDateClick(date) },
                onEntryClick = onEntryClick
            )
        }
    }
}

@Composable
fun DaySection(
    date: LocalDate,
    entries: List<TrackedEntry>,
    isToday: Boolean,
    onDateClick: () -> Unit,
    onEntryClick: (TrackedEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Date header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDateClick),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = date.dayOfWeek.name.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = DateTimeUtil.formatDate(date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    text = "${entries.size} ${if (entries.size == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // Entries for this day
            if (entries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                entries.forEach { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = { onEntryClick(entry) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}
