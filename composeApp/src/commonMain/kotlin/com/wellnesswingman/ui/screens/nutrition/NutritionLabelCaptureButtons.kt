package com.wellnesswingman.ui.screens.nutrition

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun NutritionLabelCaptureButtons(
    onCameraClickFallback: () -> Unit,
    onGalleryClickFallback: () -> Unit,
    onImageSelected: (photoPath: String?, bytes: ByteArray) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
)
