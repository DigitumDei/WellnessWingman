package com.wellnesswingman.ui.screens.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import kotlin.math.abs
import org.koin.core.parameter.parametersOf

@Composable
expect fun ImageDisplay(imageBytes: ByteArray?)

@Composable
expect fun VoiceRecordingButton(
    onToggleRecording: () -> Unit,
    isRecording: Boolean,
    isTranscribing: Boolean,
    recordingDurationSeconds: Int,
    enabled: Boolean,
    modifier: Modifier
)

data class EntryDetailScreen(val entryId: Long) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<EntryDetailViewModel> { parametersOf(entryId) }
        val uiState by viewModel.uiState.collectAsState()
        val correctionState by viewModel.correctionState.collectAsState()

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
                    correctionState = correctionState,
                    onRetryAnalysis = { viewModel.retryAnalysis() },
                    onEnterCorrectionMode = { viewModel.enterCorrectionMode() },
                    onExitCorrectionMode = { viewModel.exitCorrectionMode() },
                    onUpdateCorrectionText = { viewModel.updateCorrectionText(it) },
                    onSubmitCorrection = { viewModel.submitCorrection() },
                    onToggleRecording = { viewModel.toggleRecording() },
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
    correctionState: CorrectionState,
    onRetryAnalysis: () -> Unit,
    onEnterCorrectionMode: () -> Unit,
    onExitCorrectionMode: () -> Unit,
    onUpdateCorrectionText: (String) -> Unit,
    onSubmitCorrection: () -> Unit,
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
        // Image (if available) - rendered platform-specifically
        ImageDisplay(state.imageBytes)

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

        // API key warning if analysis was skipped
        if (state.entry.processingStatus.name == "SKIPPED") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "AI Analysis Not Configured",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = "This entry was not analyzed because no API key is configured. Go to Settings to add your OpenAI or Gemini API key to enable AI-powered meal analysis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRetryAnalysis,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onErrorContainer,
                            contentColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry Analysis")
                    }
                }
            }
        }

        // Update/Retry analysis for FAILED or COMPLETED entries
        if (state.entry.processingStatus.name == "FAILED" || state.entry.processingStatus.name == "COMPLETED") {
            if (correctionState.isActive) {
                // Correction mode UI
                CorrectionModeSection(
                    correctionState = correctionState,
                    onUpdateText = onUpdateCorrectionText,
                    onSubmit = onSubmitCorrection,
                    onCancel = onExitCorrectionMode,
                    onToggleRecording = onToggleRecording
                )
            } else {
                OutlinedButton(
                    onClick = onEnterCorrectionMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Update Analysis")
                }
                if (state.entry.processingStatus.name == "FAILED") {
                    Button(
                        onClick = onRetryAnalysis,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry Analysis")
                    }
                }
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
            is ParsedAnalysis.Meal -> MealAnalysisCard(
                result = parsed.result,
                unifiedWarnings = parsed.unifiedWarnings,
                unifiedConfidence = parsed.unifiedConfidence
            )
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
                } else {
                    // Analysis exists but failed to parse - show raw JSON
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Analysis Parse Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "The analysis completed but couldn't be parsed. Raw response:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = state.analysis.insightsJson,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CorrectionModeSection(
    correctionState: CorrectionState,
    onUpdateText: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onToggleRecording: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add any details we missed so the analysis can be corrected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = correctionState.correctionText,
                onValueChange = onUpdateText,
                label = { Text("Describe what needs to change (or use the mic below)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                enabled = !correctionState.isSubmitting
            )

            // Voice recording button (platform-specific for permission handling)
            VoiceRecordingButton(
                onToggleRecording = onToggleRecording,
                isRecording = correctionState.isRecording,
                isTranscribing = correctionState.isTranscribing,
                recordingDurationSeconds = correctionState.recordingDurationSeconds,
                enabled = !correctionState.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )

            // Transcription error display
            if (correctionState.transcriptionError != null) {
                Text(
                    text = correctionState.transcriptionError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (correctionState.isSubmitting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Submitting correction...")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !correctionState.isSubmitting
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.weight(1f),
                    enabled = correctionState.canSubmit
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send correction")
                }
            }
        }
    }
}

@Composable
fun MealAnalysisCard(
    result: com.wellnesswingman.data.model.analysis.MealAnalysisResult,
    unifiedWarnings: List<String> = emptyList(),
    unifiedConfidence: Double? = null
) {
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

            val combinedWarnings = (unifiedWarnings + result.warnings)
                .filter { it.isNotBlank() }
            val confidenceValue = when {
                unifiedConfidence != null && unifiedConfidence > 0.0 -> unifiedConfidence
                result.confidence > 0.0 -> result.confidence
                else -> null
            }

            if (combinedWarnings.isNotEmpty()) {
                Text(
                    text = "Analysis Notes",
                    style = MaterialTheme.typography.titleMedium
                )
                combinedWarnings.forEach { warning ->
                    Text(
                        text = "• $warning",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            confidenceValue?.let {
                Text(
                    text = "Confidence: ${formatPercent(it)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (result.foodItems.isEmpty()) {
                Text(
                    text = "No Food Detected",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "This image doesn't appear to contain any food items.",
                    style = MaterialTheme.typography.bodyMedium
                )

                result.healthInsights?.summary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (result.healthInsights?.summary.isNullOrBlank() && combinedWarnings.isEmpty()) {
                    Text(
                        text = "Tip: This app analyzes photos of meals. Try capturing a photo of your food for nutritional insights!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@Column
            }

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
                    val portionText = item.portionSize?.let { " ($it)" } ?: ""
                    val caloriesText = item.calories?.let { "${formatNumber(it)} kcal" } ?: "calories unknown"
                    val confidenceText = if (item.confidence > 0.0) {
                        " (confidence ${formatPercent(item.confidence)})"
                    } else {
                        ""
                    }
                    Text(
                        text = "• ${item.name}$portionText - $caloriesText$confidenceText",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            result.healthInsights?.let { insights ->
                Text(
                    text = "Health Insights",
                    style = MaterialTheme.typography.titleMedium
                )

                insights.healthScore?.let {
                    Text(
                        text = "Health Score: ${formatNumber(it)}/10",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                insights.summary?.let {
                    Text(text = "Summary: $it", style = MaterialTheme.typography.bodyMedium)
                }

                if (insights.positives.isNotEmpty()) {
                    Text(text = "Positives:", style = MaterialTheme.typography.bodyMedium)
                    insights.positives.forEach { positive ->
                        Text(text = "• $positive", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (insights.improvements.isNotEmpty()) {
                    Text(text = "Improvements:", style = MaterialTheme.typography.bodyMedium)
                    insights.improvements.forEach { improvement ->
                        Text(text = "• $improvement", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (insights.recommendations.isNotEmpty()) {
                    Text(text = "Recommendations:", style = MaterialTheme.typography.bodyMedium)
                    insights.recommendations.forEach { recommendation ->
                        Text(text = "• $recommendation", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun NutritionInfo(nutrition: com.wellnesswingman.data.model.analysis.NutritionEstimate) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        nutrition.totalCalories?.let {
            Text("Calories: ${formatNumber(it)} kcal", style = MaterialTheme.typography.bodyMedium)
        }
        nutrition.protein?.let {
            Text("Protein: ${formatNumber(it)} g", style = MaterialTheme.typography.bodyMedium)
        }
        nutrition.carbohydrates?.let {
            Text("Carbs: ${formatNumber(it)} g", style = MaterialTheme.typography.bodyMedium)
        }
        nutrition.fat?.let {
            Text("Fat: ${formatNumber(it)} g", style = MaterialTheme.typography.bodyMedium)
        }
        nutrition.fiber?.let {
            Text("Fiber: ${formatNumber(it)} g", style = MaterialTheme.typography.bodyMedium)
        }
        nutrition.sugar?.let {
            Text("Sugar: ${formatNumber(it)} g", style = MaterialTheme.typography.bodyMedium)
        }
        nutrition.sodium?.let {
            Text("Sodium: ${formatNumber(it)} mg", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatNumber(value: Double): String {
    val rounded = if (abs(value - value.toInt()) < 0.01) {
        value.toInt().toString()
    } else {
        String.format("%.2f", value)
    }
    return rounded
}

private fun formatPercent(value: Double): String {
    val percent = value * 100
    return if (abs(percent - percent.toInt()) < 0.1) {
        "${percent.toInt()}%"
    } else {
        "${String.format("%.1f", percent)}%"
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
            metrics.averageHeartRate?.let {
                Text("Avg Heart Rate: ${it.toInt()} bpm", style = MaterialTheme.typography.bodyMedium)
            }
            metrics.steps?.let {
                Text("Steps: ${it.toInt()}", style = MaterialTheme.typography.bodyMedium)
            }

            result.insights?.let { insights ->
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.titleMedium
                )

                insights.summary?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }

                if (insights.positives.isNotEmpty()) {
                    Text(text = "Positives:", style = MaterialTheme.typography.bodyMedium)
                    insights.positives.forEach { positive ->
                        Text(text = "• $positive", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (insights.improvements.isNotEmpty()) {
                    Text(text = "Improvements:", style = MaterialTheme.typography.bodyMedium)
                    insights.improvements.forEach { improvement ->
                        Text(text = "• $improvement", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (insights.recommendations.isNotEmpty()) {
                    Text(text = "Recommendations:", style = MaterialTheme.typography.bodyMedium)
                    insights.recommendations.forEach { recommendation ->
                        Text(text = "• $recommendation", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (result.warnings.isNotEmpty()) {
                Text(
                    text = "Warnings",
                    style = MaterialTheme.typography.titleMedium
                )
                result.warnings.forEach { warning ->
                    Text(text = "⚠ $warning", style = MaterialTheme.typography.bodyMedium)
                }
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
