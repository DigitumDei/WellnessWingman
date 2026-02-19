package com.wellnesswingman.ui.screens.weighthistory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.WeightRecord
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class WeightHistoryScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<WeightHistoryViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        var recordToDelete by remember { mutableStateOf<WeightRecord?>(null) }

        // Delete confirmation dialog
        recordToDelete?.let { record ->
            AlertDialog(
                onDismissRequest = { recordToDelete = null },
                title = { Text("Delete Weight Record") },
                text = {
                    Text(
                        "Delete the %.1f %s record? This cannot be undone.".format(
                            record.weightValue, record.weightUnit
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteWeightRecord(record.weightRecordId)
                        recordToDelete = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (uiState.showLogDialog) {
            LogWeightDialog(
                value = uiState.logWeightValue,
                unit = uiState.logWeightUnit,
                onValueChange = { viewModel.updateLogWeightValue(it) },
                onUnitChange = { viewModel.updateLogWeightUnit(it) },
                onConfirm = {
                    val weight = uiState.logWeightValue.toDoubleOrNull()
                    if (weight != null && weight > 0) {
                        viewModel.logWeight(weight, uiState.logWeightUnit)
                    }
                },
                onDismiss = { viewModel.dismissLogDialog() }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Weight History") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { viewModel.showLogDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = "Log Weight")
                }
            }
        ) { paddingValues ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No weight records yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap + to log your first measurement",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.records, key = { it.weightRecordId }) { record ->
                        WeightRecordItem(
                            record = record,
                            onDelete = { recordToDelete = record }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightRecordItem(
    record: WeightRecord,
    onDelete: () -> Unit
) {
    val localDateTime = record.recordedAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val dateStr = "%04d-%02d-%02d %02d:%02d".format(
        localDateTime.year,
        localDateTime.monthNumber,
        localDateTime.dayOfMonth,
        localDateTime.hour,
        localDateTime.minute
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "%.1f %s".format(record.weightValue, record.weightUnit),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        if (record.source == "LlmDetected") "Auto" else "Manual",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.padding(end = 8.dp)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun LogWeightDialog(
    value: String,
    unit: String,
    onValueChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("Weight") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("kg", "lbs").forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { onUnitChange(u) },
                            label = { Text(u) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = value.toDoubleOrNull()?.let { it > 0 } == true
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
