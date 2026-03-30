package com.wellnesswingman.ui.screens.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun NutritionLabelCaptureButtons(
    onCameraClickFallback: () -> Unit,
    onGalleryClickFallback: () -> Unit,
    onImageSelected: (photoPath: String?, bytes: ByteArray) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onCameraClickFallback, modifier = Modifier.weight(1f)) {
            Text("Camera")
        }
        OutlinedButton(onClick = onGalleryClickFallback, modifier = Modifier.weight(1f)) {
            Text("Gallery")
        }
    }
}
