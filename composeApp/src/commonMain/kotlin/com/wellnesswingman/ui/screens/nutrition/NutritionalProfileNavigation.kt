package com.wellnesswingman.ui.screens.nutrition

internal const val NEW_NUTRITIONAL_PROFILE_ID = -1L

internal fun profileIdParameter(profileId: Long?): Long = profileId ?: NEW_NUTRITIONAL_PROFILE_ID

internal fun profileIdFromParameter(profileId: Long?): Long? =
    profileId?.takeIf { it != NEW_NUTRITIONAL_PROFILE_ID }
