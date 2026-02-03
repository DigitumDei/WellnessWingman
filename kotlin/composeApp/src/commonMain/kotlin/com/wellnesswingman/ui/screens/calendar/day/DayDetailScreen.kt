package com.wellnesswingman.ui.screens.calendar.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import com.wellnesswingman.data.model.NutritionTotals
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.ui.components.EmptyState
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.detail.EntryDetailScreen
import com.wellnesswingman.ui.screens.main.DailyNutritionCard
import com.wellnesswingman.ui.screens.main.EntryCard
import com.wellnesswingman.ui.screens.main.SummaryCardState
import com.wellnesswingman.ui.screens.summary.DailySummaryScreen
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class DayDetailScreen(val date: LocalDate) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<DayDetailViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        val summaryCardState by viewModel.summaryCardState.collectAsState()
        val isGeneratingSummary by viewModel.isGeneratingSummary.collectAsState()

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
                    }
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is DayDetailUiState.Loading -> LoadingIndicator(Modifier.padding(paddingValues))
                is DayDetailUiState.Success -> DayEntryList(
                    date = date,
                    entries = state.entries,
                    nutritionTotals = state.nutritionTotals,
                    hasCompletedMeals = state.hasCompletedMeals,
                    summaryCardState = summaryCardState,
                    isGeneratingSummary = isGeneratingSummary,
                    onEntryClick = { entry -> navigator.push(EntryDetailScreen(entry.entryId)) },
                    onGenerateSummary = { viewModel.generateDailySummary() },
                    onViewSummary = { navigator.push(DailySummaryScreen()) },
                    onPreviousDay = { navigator.replace(DayDetailScreen(date.plus(-1, DateTimeUnit.DAY))) },
                    onNextDay = { navigator.replace(DayDetailScreen(date.plus(1, DateTimeUnit.DAY))) },
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
    date: LocalDate,
    entries: List<TrackedEntry>,
    nutritionTotals: NutritionTotals,
    hasCompletedMeals: Boolean,
    summaryCardState: SummaryCardState,
    isGeneratingSummary: Boolean,
    onEntryClick: (TrackedEntry) -> Unit,
    onGenerateSummary: () -> Unit,
    onViewSummary: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Date Navigation
        item(key = "date_nav") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousDay) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                }
                Text(
                    text = DateTimeUtil.formatDateFull(date),
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = onNextDay) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
                }
            }
        }

        // Daily Nutrition Summary Card
        if (hasCompletedMeals) {
            item(key = "nutrition_summary") {
                DailyNutritionCard(
                    nutritionTotals = nutritionTotals,
                    summaryCardState = summaryCardState,
                    isGeneratingSummary = isGeneratingSummary,
                    onGenerateSummary = onGenerateSummary,
                    onViewSummary = onViewSummary
                )
            }
        }

        // Entries header
        item(key = "entries_header") {
            Text(
                text = "Entries",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = if (hasCompletedMeals) 8.dp else 0.dp)
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
