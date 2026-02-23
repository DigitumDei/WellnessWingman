package com.wellnesswingman.ui.screens.calendar

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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

class WeekViewScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<WeekViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        val currentWeek by viewModel.currentWeekStart.collectAsState()
        val weeklySummaryState by viewModel.weeklySummaryState.collectAsState()
        val entryCounts by viewModel.entryCounts.collectAsState()

        // Swipe gesture handling
        var swipeOffset by remember { mutableStateOf(0f) }
        val swipeThreshold = 100f // pixels

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
                            Icon(Icons.Default.Event, contentDescription = "Today")
                        }
                        IconButton(onClick = { viewModel.nextWeek() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Week")
                        }
                    }
                )
            },
            modifier = Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffset > swipeThreshold) {
                            viewModel.previousWeek()
                        } else if (swipeOffset < -swipeThreshold) {
                            viewModel.nextWeek()
                        }
                        swipeOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        swipeOffset += dragAmount
                    }
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is WeekUiState.Loading -> LoadingIndicator(Modifier.padding(paddingValues))
                is WeekUiState.Success -> WeekContent(
                    weekStart = state.weekStart,
                    entriesByDate = state.entriesByDate,
                    weeklySummaryState = weeklySummaryState,
                    entryCounts = entryCounts,
                    onDateClick = { date -> navigator.push(DayDetailScreen(date)) },
                    onEntryClick = { entry -> navigator.push(com.wellnesswingman.ui.screens.detail.EntryDetailScreen(entry.entryId)) },
                    onGenerateSummary = { viewModel.generateWeeklySummary() },
                    onRegenerateSummary = { viewModel.regenerateWeeklySummary() },
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
    weeklySummaryState: WeeklySummaryState,
    entryCounts: EntryCounts,
    onDateClick: (LocalDate) -> Unit,
    onEntryClick: (TrackedEntry) -> Unit,
    onGenerateSummary: () -> Unit,
    onRegenerateSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Weekly Summary Card
        item(key = "weekly_summary") {
            WeeklySummaryCard(
                weekStart = weekStart,
                weeklySummaryState = weeklySummaryState,
                entryCounts = entryCounts,
                onGenerateSummary = onGenerateSummary,
                onRegenerateSummary = onRegenerateSummary
            )
        }

        // Week header
        item(key = "week_header") {
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
fun WeeklySummaryCard(
    weekStart: LocalDate,
    weeklySummaryState: WeeklySummaryState,
    entryCounts: EntryCounts,
    onGenerateSummary: () -> Unit,
    onRegenerateSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Weekly Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Entry counts row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EntryCountItem(count = entryCounts.mealCount, label = "Meals", emoji = "\uD83C\uDF7D\uFE0F")
                EntryCountItem(count = entryCounts.exerciseCount, label = "Exercise", emoji = "\uD83C\uDFCB\uFE0F")
                EntryCountItem(count = entryCounts.sleepCount, label = "Sleep", emoji = "\uD83D\uDE34")
                EntryCountItem(count = entryCounts.otherCount, label = "Other", emoji = "\uD83D\uDCDD")
            }

            HorizontalDivider()

            // State-based content
            when (weeklySummaryState) {
                is WeeklySummaryState.Hidden -> {
                    // Don't show anything
                }
                is WeeklySummaryState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                is WeeklySummaryState.NoSummary -> {
                    if (entryCounts.totalEntries > 0) {
                        Button(
                            onClick = onGenerateSummary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Generate Weekly Insights")
                        }
                    } else {
                        Text(
                            text = "No entries this week. Start tracking to get insights!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is WeeklySummaryState.Generating -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generating weekly insights...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is WeeklySummaryState.HasSummary -> {
                    // Highlights section
                    if (weeklySummaryState.highlightsList.isNotEmpty()) {
                        Text(
                            text = "Highlights",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        weeklySummaryState.highlightsList.take(3).forEach { highlight ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = "\u2022 ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = highlight,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Recommendations section
                    if (weeklySummaryState.recommendationsList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Recommendations",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        weeklySummaryState.recommendationsList.take(3).forEach { rec ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = "\u2192 ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = rec,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Regenerate button
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRegenerateSummary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Regenerate Analysis")
                    }
                }
                is WeeklySummaryState.Error -> {
                    Text(
                        text = weeklySummaryState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onGenerateSummary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}

@Composable
fun EntryCountItem(
    count: Int,
    label: String,
    emoji: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
