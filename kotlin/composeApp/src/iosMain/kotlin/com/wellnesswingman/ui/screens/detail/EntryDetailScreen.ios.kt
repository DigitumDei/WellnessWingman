package com.wellnesswingman.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun ImageDisplay(imageBytes: ByteArray?) {
    // iOS image display not implemented yet
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
    // iOS voice recording not implemented yet
}
