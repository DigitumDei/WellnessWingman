package com.wellnesswingman.ui.screens.detail

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wellnesswingman.platform.decodeWithExifRotation

@Composable
actual fun ImageDisplay(imageBytes: ByteArray?) {
    if (imageBytes != null) {
        Card {
            val imageBitmap = remember(imageBytes) {
                try {
                    decodeWithExifRotation(imageBytes)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }

            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Entry photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
actual fun VoiceRecordingButton(
    onToggleRecording: () -> Unit,
    isRecording: Boolean,
    isTranscribing: Boolean,
    recordingDurationSeconds: Int,
    enabled: Boolean,
    modifier: Modifier
) {
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleRecording()
        }
    }

    OutlinedButton(
        onClick = {
            if (isRecording) {
                onToggleRecording()
            } else {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    onToggleRecording()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        modifier = modifier,
        enabled = enabled && !isTranscribing,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isRecording)
                MaterialTheme.colorScheme.errorContainer
            else
                Color.Transparent
        )
    ) {
        if (isTranscribing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Transcribing...")
        } else {
            Icon(
                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop recording" else "Record voice note"
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isRecording)
                    "Recording ${formatDuration(recordingDurationSeconds)}"
                else
                    "Add Voice Note"
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    return String.format("%d:%02d", seconds / 60, seconds % 60)
}

