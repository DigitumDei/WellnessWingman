package com.wellnesswingman.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.NutritionTotals
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.ui.components.EmptyState
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.calendar.WeekViewScreen
import com.wellnesswingman.ui.screens.detail.EntryDetailScreen
import com.wellnesswingman.ui.screens.photo.createPhotoReviewScreen
import com.wellnesswingman.ui.screens.settings.SettingsScreen
import com.wellnesswingman.ui.screens.summary.DailySummaryScreen
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.datetime.TimeZone

@Composable
expect fun ThumbnailDisplay(imageBytes: ByteArray?, modifier: Modifier = Modifier)

class MainScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<MainViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        val isRefreshing by viewModel.isRefreshing.collectAsState()
        val summaryCardState by viewModel.summaryCardState.collectAsState()
        val isGeneratingSummary by viewModel.isGeneratingSummary.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Today") },
                    actions = {
                        IconButton(onClick = { navigator.push(WeekViewScreen()) }) {
                            Icon(Icons.Default.CalendarViewMonth, contentDescription = "Calendar")
                        }
                        IconButton(onClick = { navigator.push(SettingsScreen()) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { navigator.push(createPhotoReviewScreen()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Entry")
                }
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is MainUiState.Loading -> LoadingIndicator(Modifier.padding(paddingValues))
                is MainUiState.Empty -> EmptyState(
                    message = "No entries for today. Start tracking your wellness!",
                    modifier = Modifier.padding(paddingValues)
                )
                is MainUiState.Error -> ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.loadEntries() },
                    modifier = Modifier.padding(paddingValues)
                )
                is MainUiState.Success -> EntryList(
                    entries = state.entries,
                    thumbnails = state.thumbnails,
                    nutritionTotals = state.nutritionTotals,
                    hasCompletedMeals = state.hasCompletedMeals,
                    summaryCardState = summaryCardState,
                    isGeneratingSummary = isGeneratingSummary,
                    onEntryClick = { entry -> navigator.push(EntryDetailScreen(entry.entryId)) },
                    onGenerateSummary = { viewModel.generateDailySummary() },
                    onViewSummary = { navigator.push(DailySummaryScreen()) },
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
    thumbnails: Map<Long, ByteArray>,
    nutritionTotals: NutritionTotals,
    hasCompletedMeals: Boolean,
    summaryCardState: SummaryCardState,
    isGeneratingSummary: Boolean,
    onEntryClick: (TrackedEntry) -> Unit,
    onGenerateSummary: () -> Unit,
    onViewSummary: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                thumbnailBytes = thumbnails[entry.entryId],
                onClick = { onEntryClick(entry) }
            )
        }
    }
}

@Composable
fun DailyNutritionCard(
    nutritionTotals: NutritionTotals,
    summaryCardState: SummaryCardState,
    isGeneratingSummary: Boolean,
    onGenerateSummary: () -> Unit,
    onViewSummary: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Daily Nutrition",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Macros Row (Calories, Protein, Carbs, Fat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NutritionItem(
                    value = "${nutritionTotals.calories.toInt()}",
                    label = "Cals"
                )
                NutritionItem(
                    value = "${nutritionTotals.protein.toInt()}g",
                    label = "Prot"
                )
                NutritionItem(
                    value = "${nutritionTotals.carbs.toInt()}g",
                    label = "Carbs"
                )
                NutritionItem(
                    value = "${nutritionTotals.fat.toInt()}g",
                    label = "Fat"
                )
            }

            // Micros Row (Fiber, Sugar, Sodium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NutritionItem(
                    value = "${nutritionTotals.fiber.toInt()}g",
                    label = "Fiber",
                    fontSize = 16
                )
                NutritionItem(
                    value = "${nutritionTotals.sugar.toInt()}g",
                    label = "Sugar",
                    fontSize = 16
                )
                NutritionItem(
                    value = "${nutritionTotals.sodium.toInt()}mg",
                    label = "Sodium",
                    fontSize = 16
                )
            }

            // Summary button/status
            when (summaryCardState) {
                is SummaryCardState.Hidden -> { /* Don't show anything */ }
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generating analysis...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is SummaryCardState.HasSummary -> {
                    OutlinedButton(
                        onClick = onViewSummary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Analysis")
                    }
                }
            }
        }
    }
}

@Composable
fun NutritionItem(
    value: String,
    label: String,
    fontSize: Int = 18,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EntryCard(
    entry: TrackedEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailBytes: ByteArray? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail image header
            if (thumbnailBytes != null) {
                ThumbnailDisplay(
                    imageBytes = thumbnailBytes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            }

            // Text content
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
