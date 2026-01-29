package com.wellnesswingman.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import kotlinx.datetime.*

class MonthViewScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<CalendarViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        val currentMonth by viewModel.currentMonth.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "${currentMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${currentMonth.year}"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.previousMonth() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                        }
                        IconButton(onClick = { viewModel.today() }) {
                            Icon(Icons.Default.Today, contentDescription = "Today")
                        }
                        IconButton(onClick = { viewModel.nextMonth() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is CalendarUiState.Loading -> LoadingIndicator(Modifier.padding(paddingValues))
                is CalendarUiState.Success -> MonthCalendar(
                    month = state.month,
                    entriesByDate = state.entriesByDate,
                    onDateClick = { date -> navigator.push(DayDetailScreen(date)) },
                    modifier = Modifier.padding(paddingValues)
                )
                is CalendarUiState.Error -> ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.loadMonth(currentMonth) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun MonthCalendar(
    month: LocalDate,
    entriesByDate: Map<LocalDate, List<TrackedEntry>>,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Day headers
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Calendar grid
        val firstOfMonth = LocalDate(month.year, month.month, 1)
        val daysInMonth = month.month.length(isLeapYear(month.year))
        val firstDayOfWeek = firstOfMonth.dayOfWeek.value % 7 // 0 = Sunday

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Empty cells before first day
            items(firstDayOfWeek) {
                Box(modifier = Modifier.aspectRatio(1f))
            }

            // Days of the month
            items(daysInMonth) { dayIndex ->
                val day = dayIndex + 1
                val date = LocalDate(month.year, month.month, day)
                val entries = entriesByDate[date] ?: emptyList()
                val isToday = date == Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date

                DayCell(
                    day = day,
                    entries = entries,
                    isToday = isToday,
                    onClick = { onDateClick(date) }
                )
            }
        }
    }
}

@Composable
fun DayCell(
    day: Int,
    entries: List<TrackedEntry>,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isToday) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            if (entries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    entries.take(3).forEach { entry ->
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .padding(horizontal = 1.dp)
                                .background(
                                    color = when (entry.entryType.name) {
                                        "MEAL" -> MaterialTheme.colorScheme.primary
                                        "EXERCISE" -> MaterialTheme.colorScheme.secondary
                                        "SLEEP" -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }
            }
        }
    }
}

private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
