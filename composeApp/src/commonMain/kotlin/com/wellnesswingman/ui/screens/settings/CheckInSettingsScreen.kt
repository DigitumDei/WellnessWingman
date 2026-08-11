package com.wellnesswingman.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.ui.screens.checkin.CheckInScreen

class CheckInSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<CheckInSettingsViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        // Permission may have been changed in system settings while this screen was away.
        LaunchedEffect(Unit) { viewModel.refresh() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Daily check-ins") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Two short prompts a day for how you actually felt — the part your " +
                        "photos and your Polar data cannot capture.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (uiState.shouldWarnAboutNotifications) {
                    NotificationsBlockedWarning()
                }

                CheckInSlotSettings(
                    label = "Morning",
                    description = "How did you sleep? How do you feel?",
                    enabled = uiState.morningEnabled,
                    time = uiState.morningTime,
                    onEnabledChange = viewModel::setMorningEnabled,
                    onTimeChange = viewModel::setMorningTime,
                    onOpenNow = { navigator.push(CheckInScreen(CheckInSlot.MORNING)) }
                )

                HorizontalDivider()

                CheckInSlotSettings(
                    label = "Evening",
                    description = "How did the day feel? Anything you didn't log?",
                    enabled = uiState.eveningEnabled,
                    time = uiState.eveningTime,
                    onEnabledChange = viewModel::setEveningEnabled,
                    onTimeChange = viewModel::setEveningTime,
                    onOpenNow = { navigator.push(CheckInScreen(CheckInSlot.EVENING)) }
                )

                Text(
                    text = "Reminders arrive close to the time you choose, usually within about " +
                        "ten minutes. They are not exact alarms, so they never interrupt " +
                        "battery saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NotificationsBlockedWarning() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column {
                Text(
                    text = "Check-ins can't be delivered",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Notifications are turned off for WellnessWingman. Enable them in " +
                        "your system settings, or open a check-in from here whenever you like.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun CheckInSlotSettings(
    label: String,
    description: String,
    enabled: Boolean,
    time: String,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (String) -> Boolean,
    onOpenNow: () -> Unit
) {
    // Held locally so a half-typed time such as "0" does not overwrite the stored setting;
    // only a fully valid "HH:mm" is committed.
    var timeDraft by remember(time) { mutableStateOf(time) }
    var timeIsValid by remember(time) { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        OutlinedTextField(
            value = timeDraft,
            onValueChange = { newValue ->
                timeDraft = newValue
                timeIsValid = onTimeChange(newValue)
            },
            label = { Text("Time") },
            placeholder = { Text("HH:mm") },
            supportingText = if (!timeIsValid) {
                { Text("Use 24-hour time, for example 07:00") }
            } else {
                null
            },
            isError = !timeIsValid,
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(onClick = onOpenNow) {
            Text("Open $label check-in now")
        }
    }
}
