package com.wellnesswingman.ui.screens.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator

class NutritionalProfilesScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<NutritionalProfilesViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        var deleteTarget by remember { mutableStateOf<NutritionalProfile?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Saved Nutrition Profiles") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        navigator.push(
                            NutritionLabelScanScreen(onSaved = viewModel::loadProfiles)
                        )
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Scan nutrition label")
                }
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is NutritionalProfilesUiState.Loading -> LoadingIndicator(Modifier.padding(paddingValues))
                is NutritionalProfilesUiState.Error -> ErrorMessage(
                    message = state.message,
                    onRetry = viewModel::loadProfiles,
                    modifier = Modifier.padding(paddingValues)
                )
                is NutritionalProfilesUiState.Success -> {
                    if (state.profiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No saved nutrition profiles yet.")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.profiles, key = { it.profileId }) { profile ->
                                NutritionalProfileRow(
                                    profile = profile,
                                    onClick = {
                                        navigator.push(
                                            NutritionLabelScanScreen(
                                                profileId = profile.profileId,
                                                onSaved = viewModel::loadProfiles
                                            )
                                        )
                                    },
                                    onDelete = { deleteTarget = profile }
                                )
                            }
                        }
                    }
                }
            }
        }

        deleteTarget?.let { profile ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete profile") },
                text = { Text("Delete ${profile.primaryName}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteProfile(profile.profileId)
                            deleteTarget = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun NutritionalProfileRow(
    profile: NutritionalProfile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = profile.primaryName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            profile.servingSize?.let {
                Text(
                    text = "Serving: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (profile.aliases.isNotEmpty()) {
                Text(
                    text = "Aliases: ${profile.aliases.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = buildString {
                    append(profile.calories?.let { "${it.toInt()} kcal" } ?: "Calories n/a")
                    append(" • ")
                    append(profile.protein?.let { "${it}g protein" } ?: "Protein n/a")
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            TextButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text("Delete")
            }
        }
    }
}
