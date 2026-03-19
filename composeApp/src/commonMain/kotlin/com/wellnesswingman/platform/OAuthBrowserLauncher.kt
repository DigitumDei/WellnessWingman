package com.wellnesswingman.platform

import androidx.compose.runtime.Composable

/**
 * Platform-specific browser launcher for OAuth flows.
 * On Android, launches a Chrome Custom Tab. No-op on other platforms.
 */
@Composable
expect fun LaunchOAuthBrowser(url: String?, onLaunched: () -> Unit)
