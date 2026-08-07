package com.wellnesswingman.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.wellnesswingman.domain.checkin.PendingCheckInStore
import com.wellnesswingman.ui.screens.checkin.CheckInScreen
import com.wellnesswingman.ui.screens.main.MainScreen
import com.wellnesswingman.ui.theme.WellnessWingmanTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    val pendingCheckInStore: PendingCheckInStore = koinInject()
    val requestedSlot by pendingCheckInStore.requestedSlot.collectAsState()

    WellnessWingmanTheme {
        Navigator(MainScreen()) { navigator ->
            // A check-in notification deep link arrives on the Activity; the composition owns
            // the navigation. Consuming clears the request so a configuration change cannot
            // push the screen a second time.
            LaunchedEffect(requestedSlot) {
                val slot = pendingCheckInStore.consume() ?: return@LaunchedEffect
                navigator.push(CheckInScreen(slot))
            }

            SlideTransition(navigator)
        }
    }
}
