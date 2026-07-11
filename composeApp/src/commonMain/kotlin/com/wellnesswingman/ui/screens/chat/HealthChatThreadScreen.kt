package com.wellnesswingman.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.ChatMessageStatus
import com.wellnesswingman.data.model.ChatRole
import com.wellnesswingman.data.model.HealthChatMessage
import com.wellnesswingman.ui.components.EmptyState
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.ui.screens.settings.LlmProviderSettingsScreen
import org.koin.core.parameter.parametersOf

data class HealthChatThreadScreen(val conversationExternalId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<HealthChatThreadViewModel> { parametersOf(conversationExternalId) }
        val state by viewModel.uiState.collectAsState()
        val isSending by viewModel.isSending.collectAsState()
        val draft by viewModel.draft.collectAsState()
        val sendError by viewModel.sendError.collectAsState()

        LaunchedEffect(navigator.size) {
            if (state is HealthChatThreadUiState.ApiKeyMissing) {
                viewModel.recheckConfiguration()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Health Chat") },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            snackbarHost = {
                if (sendError != null) {
                    androidx.compose.material3.Snackbar { Text(sendError.orEmpty()) }
                }
            },
            bottomBar = {
                if (state is HealthChatThreadUiState.Success || state is HealthChatThreadUiState.Empty) {
                    ChatComposer(draft, viewModel::updateDraft, viewModel::send, isSending)
                }
            },
        ) { padding ->
            when (val ui = state) {
                HealthChatThreadUiState.Loading -> LoadingIndicator(Modifier.padding(padding))
                HealthChatThreadUiState.Empty -> EmptyState("Ask about your entries, nutrition, or wellness goals.", Modifier.padding(padding))
                is HealthChatThreadUiState.Success -> MessageList(ui.messages, Modifier.padding(padding))
                HealthChatThreadUiState.ApiKeyMissing -> ApiKeyMissingState(
                    onOpenSettings = { navigator.push(LlmProviderSettingsScreen()) },
                    onRetry = { viewModel.recheckConfiguration() },
                    modifier = Modifier.padding(padding),
                )
                is HealthChatThreadUiState.Error -> ErrorMessage(ui.message, viewModel::load, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun MessageList(messages: List<HealthChatMessage>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(messages.filter { it.role != ChatRole.TOOL }, key = { it.messageId }) { message ->
            val label = if (message.role == ChatRole.USER) "You" else "Assistant"
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (message.status == ChatMessageStatus.ERROR) "Message could not be completed." else message.content,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(draft: String, onChange: (String) -> Unit, onSend: () -> Unit, isSending: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(draft, onChange, Modifier.weight(1f), label = { Text("Message") }, enabled = !isSending)
        Spacer(Modifier.width(8.dp))
        Button(onClick = onSend, enabled = draft.isNotBlank() && !isSending) {
            if (isSending) CircularProgressIndicator() else Text("Send")
        }
    }
}

@Composable
private fun ApiKeyMissingState(onOpenSettings: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Set an API key for the selected provider before starting a health chat.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onOpenSettings) { Text("Open LLM settings") }
        Button(onClick = onRetry) { Text("I've set my API key") }
    }
}
