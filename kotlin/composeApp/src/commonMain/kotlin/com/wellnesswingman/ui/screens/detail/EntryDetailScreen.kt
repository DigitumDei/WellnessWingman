package com.wellnesswingman.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.datetime.TimeZone
import org.koin.core.parameter.parametersOf

data class EntryDetailScreen(val entryId: Long) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<EntryDetailViewModel> { parametersOf(entryId) }
        val uiState by viewModel.uiState.collectAsState()

        var showDeleteDialog by remember { mutableStateOf(false) }

        // Handle deletion
        LaunchedEffect(uiState) {
            if (uiState is EntryDetailUiState.Deleted) {
                navigator.pop()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Entry Details") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is EntryDetailUiState.Loading -> LoadingIndicator(Modifier.padding(paddingValues))
                is EntryDetailUiState.Error -> ErrorMessage(
                    message = state.message,
                    modifier = Modifier.padding(paddingValues)
                )
                is EntryDetailUiState.Success -> EntryDetailContent(
                    state = state,
                    modifier = Modifier.padding(paddingValues)
                )
                is EntryDetailUiState.Deleted -> {}
            }
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Entry") },
                text = { Text("Are you sure you want to delete this entry? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteEntry()
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun EntryDetailContent(
    state: EntryDetailUiState.Success,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Entry metadata
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = state.entry.entryType.name,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = DateTimeUtil.formatDateTime(
                        state.entry.capturedAt,
                        TimeZone.currentSystemDefault()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Status: ${state.entry.processingStatus.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // User notes
        if (state.entry.userNotes != null) {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "User Notes",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.entry.userNotes!!,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Analysis results
        when (val parsed = state.parsedAnalysis) {
            is ParsedAnalysis.Meal -> MealAnalysisCard(parsed.result)
            is ParsedAnalysis.Exercise -> ExerciseAnalysisCard(parsed.result)
            is ParsedAnalysis.Sleep -> SleepAnalysisCard(parsed.result)
            null -> {
                if (state.analysis == null) {
                    Card {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "No analysis available yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MealAnalysisCard(result: com.wellnesswingman.data.model.analysis.MealAnalysisResult) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Meal Analysis",
                style = MaterialTheme.typography.titleLarge
            )

            result.nutrition?.let { nutrition ->
                Text(
                    text = "Nutrition",
                    style = MaterialTheme.typography.titleMedium
                )
                NutritionInfo(nutrition)
            }

            if (result.foodItems.isNotEmpty()) {
                Text(
                    text = "Food Items",
                    style = MaterialTheme.typography.titleMedium
                )
                result.foodItems.forEach { item ->
                    Text(
                        text = "• ${item.name} ${item.portionSize?.let { "($it)" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            result.healthInsights?.let { insights ->
                Text(
                    text = "Health Insights",
                    style = MaterialTheme.typography.titleMedium
                )
                insights.summary?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun NutritionInfo(nutrition: com.wellnesswingman.data.model.analysis.NutritionEstimate) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        nutrition.totalCalories?.let {
            Text("Calories: ${it.toInt()} kcal", style = MaterialTheme.typography.bodyMedium)
        }
        nutrition.protein?.let {
            Text("Protein: ${it.toInt()}g", style = MaterialTheme.typography.bodyMedium)
        }
        nutrition.carbohydrates?.let {
            Text("Carbs: ${it.toInt()}g", style = MaterialTheme.typography.bodyMedium)
        }
        nutrition.fat?.let {
            Text("Fat: ${it.toInt()}g", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ExerciseAnalysisCard(result: com.wellnesswingman.data.model.analysis.ExerciseAnalysisResult) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Exercise Analysis",
                style = MaterialTheme.typography.titleLarge
            )

            result.activityType?.let {
                Text("Activity: $it", style = MaterialTheme.typography.bodyLarge)
            }

            Text(
                text = "Metrics",
                style = MaterialTheme.typography.titleMedium
            )

            val metrics = result.metrics
            metrics.durationMinutes?.let {
                Text("Duration: ${it.toInt()} minutes", style = MaterialTheme.typography.bodyMedium)
            }
            metrics.distance?.let {
                Text("Distance: $it ${metrics.distanceUnit ?: ""}", style = MaterialTheme.typography.bodyMedium)
            }
            metrics.calories?.let {
                Text("Calories: ${it.toInt()} kcal", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SleepAnalysisCard(result: com.wellnesswingman.data.model.analysis.SleepAnalysisResult) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sleep Analysis",
                style = MaterialTheme.typography.titleLarge
            )

            result.durationHours?.let {
                Text("Duration: ${String.format("%.1f", it)} hours", style = MaterialTheme.typography.bodyLarge)
            }

            result.sleepScore?.let {
                Text("Sleep Score: ${it.toInt()}/100", style = MaterialTheme.typography.bodyMedium)
            }

            result.qualitySummary?.let {
                Text(
                    text = "Quality",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
