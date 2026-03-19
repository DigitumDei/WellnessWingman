package com.wellnesswingman.platform

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun LaunchOAuthBrowser(url: String?, onLaunched: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(url) {
        if (url != null) {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, Uri.parse(url))
            onLaunched()
        }
    }
}
