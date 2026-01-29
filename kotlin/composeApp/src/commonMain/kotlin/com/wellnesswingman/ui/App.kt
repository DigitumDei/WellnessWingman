package com.wellnesswingman.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.wellnesswingman.ui.screens.main.MainScreen
import com.wellnesswingman.ui.theme.WellnessWingmanTheme

@Composable
fun App() {
    WellnessWingmanTheme {
        Navigator(MainScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}
