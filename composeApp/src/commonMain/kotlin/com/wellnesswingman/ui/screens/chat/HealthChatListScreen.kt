package com.wellnesswingman.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.HealthChatConversation
import com.wellnesswingman.ui.components.EmptyState
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator

class HealthChatListScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<HealthChatListViewModel>()
        val state by viewModel.uiState.collectAsState()
        val startConversation = { navigator.push(HealthChatThreadScreen(viewModel.newConversationExternalId())) }

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
            floatingActionButton = {
                FloatingActionButton(onClick = startConversation) {
                    Icon(Icons.Default.Add, contentDescription = "New conversation")
                }
            },
        ) { padding ->
            when (val ui = state) {
                HealthChatListUiState.Loading -> LoadingIndicator(Modifier.padding(padding))
                is HealthChatListUiState.Error -> ErrorMessage(ui.message, viewModel::refresh, Modifier.padding(padding))
                is HealthChatListUiState.Success -> if (ui.conversations.isEmpty()) {
                    EmptyState("Start a private conversation about your wellness.", Modifier.padding(padding))
                } else {
                    ConversationList(ui.conversations, viewModel::deleteConversation, { navigator.push(HealthChatThreadScreen(it.externalId)) }, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<HealthChatConversation>,
    onDelete: (Long) -> Unit,
    onOpen: (HealthChatConversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(conversations, key = { it.conversationId }) { conversation ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(conversation) }) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            conversation.title.ifBlank { "New health conversation" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            conversation.lastMessageContent.orEmpty().ifBlank { "No messages yet" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onDelete(conversation.conversationId) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete conversation")
                    }
                }
            }
        }
    }
}
