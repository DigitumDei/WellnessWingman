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

class LlmProviderSettingsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<SettingsViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(uiState.saveSuccess) {
            if (uiState.saveSuccess) {
                snackbarHostState.showSnackbar("LLM settings saved successfully")
                viewModel.clearSaveSuccess()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("LLM Provider") },
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
                // Provider Selection
                Text(
                    text = "Provider",
                    style = MaterialTheme.typography.titleLarge
                )

                ProviderSelector(
                    selectedProvider = uiState.selectedProvider,
                    onProviderSelected = { viewModel.selectProvider(it) }
                )

                HorizontalDivider()

                // OpenAI Configuration
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

                // Gemini Configuration
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

                Spacer(modifier = Modifier.weight(1f))

                // Save Button
                Button(
                    onClick = { viewModel.saveLlmSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save LLM Settings")
                }
            }
        }
    }
}
