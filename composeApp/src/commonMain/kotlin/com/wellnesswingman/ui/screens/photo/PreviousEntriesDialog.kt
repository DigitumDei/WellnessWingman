package com.wellnesswingman.ui.screens.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.util.DateTimeUtil
import kotlinx.datetime.TimeZone

/** Shared recent-entry picker used by the Android and desktop add-entry flows. */
@Composable
fun PreviousEntriesDialog(
    state: PreviousEntriesState,
    isBusy: Boolean = false,
    onDismiss: () -> Unit,
    onSelect: (TrackedEntry) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.isLoading && !isBusy) onDismiss() },
        title = { Text("Copy from previous") },
        text = {
            when {
                state.isLoading || isBusy -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                state.error != null -> Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error
                )
                state.entries.isEmpty() -> Text("No previous entries found.")
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.entries, key = { it.entryId }) { entry ->
                        val hasPhoto = entry.blobPath != null
                        val hasText = !entry.userNotes.isNullOrBlank()
                        TextButton(
                            onClick = { onSelect(entry) },
                            enabled = hasPhoto || hasText,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = DateTimeUtil.formatDateTime(
                                        entry.capturedAt,
                                        TimeZone.currentSystemDefault()
                                    ),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = entry.userNotes?.take(90)
                                        ?.ifBlank { "No notes" }
                                        ?: "No notes",
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (hasPhoto) "Photo + notes" else "Text only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !state.isLoading && !isBusy) {
                Text("Cancel")
            }
        }
    )
}
