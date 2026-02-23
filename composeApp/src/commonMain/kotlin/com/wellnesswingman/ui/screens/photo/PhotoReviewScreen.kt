package com.wellnesswingman.ui.screens.photo

import cafe.adriel.voyager.core.screen.Screen

/**
 * Factory function to create platform-specific PhotoReviewScreen.
 * On Android, this uses native camera/gallery integration.
 * On other platforms, this falls back to the ViewModel-based flow.
 */
expect fun createPhotoReviewScreen(): Screen
