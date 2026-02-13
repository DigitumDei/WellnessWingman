package com.wellnesswingman.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.repository.LlmProvider

class SettingsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<SettingsViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        // Show snackbar on save success
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(uiState.saveSuccess) {
            if (uiState.saveSuccess) {
                snackbarHostState.showSnackbar("Settings saved successfully")
                viewModel.clearSaveSuccess()
            }
        }

        LaunchedEffect(uiState.exportImportMessage) {
            uiState.exportImportMessage?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearExportImportMessage()
            }
        }

        // File picker
        val launchFilePicker = rememberFilePicker { filePath ->
            viewModel.importData(filePath)
        }

        var showImportConfirmDialog by remember { mutableStateOf(false) }

        if (showImportConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showImportConfirmDialog = false },
                title = { Text("Import Data") },
                text = { Text("This will import data from a WellnessWingman export file. Existing entries with the same IDs will be updated. Continue?") },
                confirmButton = {
                    TextButton(onClick = {
                        showImportConfirmDialog = false
                        launchFilePicker()
                    }) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // LLM Provider Selection
                Text(
                    text = "LLM Provider",
                    style = MaterialTheme.typography.titleLarge
                )

                ProviderSelector(
                    selectedProvider = uiState.selectedProvider,
                    onProviderSelected = { viewModel.selectProvider(it) }
                )

                HorizontalDivider()

                // OpenAI API Key
                Text(
                    text = "OpenAI Configuration",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = uiState.openAiApiKey,
                    onValueChange = { viewModel.updateOpenAiApiKey(it) },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.openAiModel,
                    onValueChange = { viewModel.updateOpenAiModel(it) },
                    label = { Text("Model") },
                    placeholder = { Text("gpt-4o") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                HorizontalDivider()

                // Gemini API Key
                Text(
                    text = "Google Gemini Configuration",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = uiState.geminiApiKey,
                    onValueChange = { viewModel.updateGeminiApiKey(it) },
                    label = { Text("API Key") },
                    placeholder = { Text("AIza...") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.geminiModel,
                    onValueChange = { viewModel.updateGeminiModel(it) },
                    label = { Text("Model") },
                    placeholder = { Text("gemini-1.5-flash") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                HorizontalDivider()

                // Diagnostics
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedButton(
                    onClick = { viewModel.shareDiagnosticLogs() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share Diagnostic Logs")
                }

                HorizontalDivider()

                // Data Management
                Text(
                    text = "Data Management",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Export and import your data for backup or migration between devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { viewModel.exportData() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isExporting && !uiState.isImporting
                ) {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Exporting...")
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Data")
                    }
                }

                OutlinedButton(
                    onClick = { showImportConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isExporting && !uiState.isImporting
                ) {
                    if (uiState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Importing...")
                    } else {
                        Text("Import Data")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Save Button
                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Settings")
                }
            }
        }
    }
}

@Composable
fun ProviderSelector(
    selectedProvider: LlmProvider,
    onProviderSelected: (LlmProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        LlmProvider.entries.forEach { provider ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                RadioButton(
                    selected = selectedProvider == provider,
                    onClick = { onProviderSelected(provider) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = when (provider) {
                            LlmProvider.OPENAI -> "GPT-4o-mini with vision and Whisper"
                            LlmProvider.GEMINI -> "Gemini 1.5 Flash with vision"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
