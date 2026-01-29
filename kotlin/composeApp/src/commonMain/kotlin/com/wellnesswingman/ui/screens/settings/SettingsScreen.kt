package com.wellnesswingman.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

                Divider()

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

                Divider()

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
