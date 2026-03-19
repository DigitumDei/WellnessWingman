package com.wellnesswingman.platform

import androidx.compose.runtime.Composable

@Composable
actual fun LaunchOAuthBrowser(url: String?, onLaunched: () -> Unit) {
    // No-op on desktop — Polar integration is Android-only
}
