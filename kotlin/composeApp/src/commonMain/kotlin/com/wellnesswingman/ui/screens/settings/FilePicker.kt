package com.wellnesswingman.ui.screens.settings

import androidx.compose.runtime.Composable

/**
 * Platform-specific file picker that returns the picked file path via callback.
 * Returns a lambda that launches the file picker when invoked.
 */
@Composable
expect fun rememberFilePicker(onFilePicked: (String) -> Unit): () -> Unit
