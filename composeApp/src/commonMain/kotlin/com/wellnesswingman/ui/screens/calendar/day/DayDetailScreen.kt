package com.wellnesswingman.ui.screens.calendar.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.NutritionTotals
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.domain.polar.PolarDayContext
import com.wellnesswingman.ui.components.EmptyState
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.detail.EntryDetailScreen
import com.wellnesswingman.ui.screens.main.DailyNutritionCard
import com.wellnesswingman.ui.screens.main.EntryCard
import com.wellnesswingman.ui.screens.main.SummaryCardState
import kotlin.math.round
import com.wellnesswingman.ui.screens.summary.DailySummaryScreen
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class DayDetailScreen(val date: LocalDate) : Screen {
    override val key: ScreenKey get() = "DayDetailScreen:$date"
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
                    thumbnails = state.thumbnails,
                    nutritionTotals = state.nutritionTotals,
                    hasCompletedMeals = state.hasCompletedMeals,
                    polarContext = state.polarContext,
                    summaryCardState = summaryCardState,
                    isGeneratingSummary = isGeneratingSummary,
                    onEntryClick = { entry -> navigator.push(EntryDetailScreen(entry.entryId)) },
                    onGenerateSummary = { viewModel.generateDailySummary() },
                    onViewSummary = { navigator.push(DailySummaryScreen(date)) },
                    onPreviousDay = { navigator.replace(DayDetailScreen(date.plus(-1, DateTimeUnit.DAY))) },
                    onNextDay = { navigator.replace(DayDetailScreen(date.plus(1, DateTimeUnit.DAY))) },
                    modifier = Modifier.padding(paddingValues)
                )
                is DayDetailUiState.Empty -> Column(
                    modifier = Modifier.padding(paddingValues).fillMaxSize(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navigator.replace(DayDetailScreen(date.plus(-1, DateTimeUnit.DAY))) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                        }
                        Text(
                            text = DateTimeUtil.formatDateFull(date),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        IconButton(onClick = { navigator.replace(DayDetailScreen(date.plus(1, DateTimeUnit.DAY))) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
                        }
                    }
                    EmptyState(
                        message = "No entries for ${DateTimeUtil.formatDate(date)}"
                    )
                }
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
    thumbnails: Map<Long, ByteArray>,
    nutritionTotals: NutritionTotals,
    hasCompletedMeals: Boolean,
    polarContext: PolarDayContext,
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

        daySummarySection(
            entries = entries,
            nutritionTotals = nutritionTotals,
            hasCompletedMeals = hasCompletedMeals,
            polarContext = polarContext,
            summaryCardState = summaryCardState,
            isGeneratingSummary = isGeneratingSummary,
            onGenerateSummary = onGenerateSummary,
            onViewSummary = onViewSummary
        )

        if (polarContext.hasData) {
            item(key = "polar_summary") {
                PolarMetricsCard(polarContext = polarContext)
            }
        }

        // Entries header
        if (entries.isNotEmpty()) {
            item(key = "entries_header") {
                Text(
                    text = "Entries",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = if (hasCompletedMeals || polarContext.hasData) 8.dp else 0.dp)
                )
            }
        }

        items(entries, key = { it.entryId }) { entry ->
            EntryCard(
                entry = entry,
                thumbnailBytes = thumbnails[entry.entryId],
                onClick = { onEntryClick(entry) }
            )
        }
    }
}

private fun LazyListScope.daySummarySection(
    entries: List<TrackedEntry>,
    nutritionTotals: NutritionTotals,
    hasCompletedMeals: Boolean,
    polarContext: PolarDayContext,
    summaryCardState: SummaryCardState,
    isGeneratingSummary: Boolean,
    onGenerateSummary: () -> Unit,
    onViewSummary: () -> Unit
) {
    when {
        hasCompletedMeals -> item(key = "nutrition_summary") {
            DailyNutritionCard(
                nutritionTotals = nutritionTotals,
                summaryCardState = summaryCardState,
                isGeneratingSummary = isGeneratingSummary,
                onGenerateSummary = onGenerateSummary,
                onViewSummary = onViewSummary
            )
        }
        summaryCardState != SummaryCardState.Hidden -> item(key = "daily_summary_action") {
            DailySummaryActionCard(
                description = daySummaryActionDescription(
                    hasTrackedEntries = entries.isNotEmpty(),
                    hasPolarData = polarContext.hasData
                ),
                summaryCardState = summaryCardState,
                isGeneratingSummary = isGeneratingSummary,
                onGenerateSummary = onGenerateSummary,
                onViewSummary = onViewSummary
            )
        }
    }
}

@Composable
internal fun DailySummaryActionCard(
    description: String,
    summaryCardState: SummaryCardState,
    isGeneratingSummary: Boolean,
    onGenerateSummary: () -> Unit,
    onViewSummary: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Daily Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
            when (summaryCardState) {
                is SummaryCardState.NoSummary, is SummaryCardState.Error -> {
                    Button(
                        onClick = onGenerateSummary,
                        enabled = !isGeneratingSummary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Get More Details")
                    }
                    if (summaryCardState is SummaryCardState.Error) {
                        Text(
                            text = summaryCardState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is SummaryCardState.Generating -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating summary...")
                    }
                }
                is SummaryCardState.HasSummary -> {
                    OutlinedButton(
                        onClick = onViewSummary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Summary")
                    }
                }
                is SummaryCardState.Hidden -> Unit
            }
        }
    }
}

internal fun daySummaryActionDescription(hasTrackedEntries: Boolean, hasPolarData: Boolean): String =
    when {
        hasTrackedEntries && hasPolarData ->
            "Generate a daily recap from today's tracked entries and synced Polar data."
        hasPolarData ->
            "Generate a daily recap using your synced Polar data for this day."
        else ->
            "Generate a daily recap for this day."
    }

@Composable
internal fun PolarMetricsCard(
    polarContext: PolarDayContext,
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Polar Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Source: Polar sync",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            polarContext.totalSteps?.let {
                Text("Steps: $it", style = MaterialTheme.typography.bodyMedium)
            }
            polarContext.sleepDurationHours?.let { hours ->
                val scoreSuffix = polarContext.sleepScore?.let { ", score ${it.toInt()}" }.orEmpty()
                Text("Sleep: ${formatOneDecimal(hours)}h$scoreSuffix", style = MaterialTheme.typography.bodyMedium)
            }
            polarContext.nightlyRecharge.maxByOrNull { it.data.recoveryIndicator }?.data?.let { recharge ->
                Text(
                    "Recovery: ANS ${recharge.ansRate}/5, recovery ${recharge.recoveryIndicator}, HRV ${recharge.hrvRmssd} ms",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (polarContext.trainingSessions.isNotEmpty()) {
                Text("Training Sessions", style = MaterialTheme.typography.titleSmall)
                polarContext.trainingSessions.forEach { session ->
                    Text(
                        text = buildString {
                            append("${formatOneDecimal(session.data.durationSeconds / 60.0)} min")
                            if (session.data.calories > 0) append(" | ${session.data.calories} kcal")
                            if (session.data.distanceMeters > 0) append(" | ${formatOneDecimal(session.data.distanceMeters / 1000.0)} km")
                            if (session.data.averageHeartRate > 0) append(" | Avg HR ${session.data.averageHeartRate}")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun formatOneDecimal(value: Double): String = (round(value * 10.0) / 10.0).toString()
