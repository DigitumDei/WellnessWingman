package com.wellnesswingman.ui.screens.checkin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.ui.components.LoadingIndicator
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.datetime.LocalDate
import com.wellnesswingman.ui.screens.chat.HealthChatThreadScreen
import com.wellnesswingman.ui.screens.detail.VoiceRecordingButton
import org.koin.core.parameter.parametersOf

/**
 * Captures a morning or evening check-in.
 *
 * Reached from the check-in notification deep link, or from inside the app for the current day —
 * a missed notification must not mean a lost check-in.
 */
data class CheckInScreen(
    val slot: CheckInSlot,
    /** The day being checked in about; null means today. */
    val date: LocalDate? = null
) : Screen {

    override val key: ScreenKey get() = "CheckInScreen:${slot.toStorageString()}:${date ?: "today"}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<CheckInViewModel> { parametersOf(slot, date) }
        val uiState by viewModel.uiState.collectAsState()
        val commentsState by viewModel.commentsState.collectAsState()

        val busy = commentsState.isRecording || commentsState.isTranscribing

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(uiState.title)
                            // Named explicitly when answering for a past day, so a backfilled
                            // answer cannot be mistaken for today's.
                            if (uiState.isBackfill) {
                                Text(
                                    text = DateTimeUtil.formatDateFull(uiState.date),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            if (uiState.isLoading) {
                LoadingIndicator()
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                uiState.questions.forEach { question ->
                    Text(
                        text = question,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = if (uiState.isBackfill) {
                        "In your own words — there are no scores here. This will be saved " +
                            "against ${DateTimeUtil.formatDate(uiState.date)}, not today."
                    } else {
                        "In your own words — there are no scores here."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = commentsState.text,
                    onValueChange = viewModel::updateText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    placeholder = { Text("Type, or use the microphone") },
                    enabled = !busy
                )

                commentsState.transcriptionError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                uiState.saveError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VoiceRecordingButton(
                        onToggleRecording = viewModel::toggleRecording,
                        isRecording = commentsState.isRecording,
                        isTranscribing = commentsState.isTranscribing,
                        recordingDurationSeconds = commentsState.recordingDurationSeconds,
                        enabled = !uiState.isSaving,
                        modifier = Modifier
                    )

                    Button(
                        onClick = { viewModel.save { navigator.pop() } },
                        enabled = commentsState.text.isNotBlank() && !busy && !uiState.isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (uiState.hasSavedAnswer) "Update" else "Save")
                    }
                }

                if (uiState.hasSavedAnswer) {
                    Text(
                        text = "Saved. Answering again replaces today's ${
                            uiState.slot.toStorageString().lowercase()
                        } check-in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                // Offered once there is something to talk about. Saving first keeps the
                // check-in itself independent of whether the chat succeeds.
                OutlinedButton(
                    onClick = {
                        viewModel.talkAboutThis { conversationExternalId ->
                            navigator.push(HealthChatThreadScreen(conversationExternalId))
                        }
                    },
                    enabled = commentsState.text.isNotBlank() &&
                        !busy &&
                        !uiState.isSaving &&
                        !uiState.isStartingConversation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isStartingConversation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Starting conversation…")
                    } else {
                        Text(
                            if (uiState.conversationExternalId != null) "Continue the conversation"
                            else "Talk about this"
                        )
                    }
                }

                uiState.conversationError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
