package com.wellnesswingman.ui.screens.main

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
actual fun ThumbnailDisplay(imageBytes: ByteArray?, modifier: Modifier) {
    if (imageBytes != null) {
        val imageBitmap = remember(imageBytes) {
            try {
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }

        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Entry thumbnail",
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp),
                contentScale = ContentScale.Crop
            )
        }
    }
}
