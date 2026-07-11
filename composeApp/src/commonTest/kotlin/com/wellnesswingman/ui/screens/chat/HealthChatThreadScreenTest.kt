package com.wellnesswingman.ui.screens.chat

import androidx.compose.ui.test.junit4.runComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertTrue

class HealthChatThreadScreenTest {

    @Test
    fun `ApiKeyMissing state displays instruction text and both buttons`() = runComposeUiTest {
        setContent {
            ApiKeyMissingState(
                onOpenSettings = {},
                onRetry = {},
            )
        }
        onNodeWithText("Set an API key for the selected provider before starting a health chat.").assertExists()
        onNodeWithText("Open LLM settings").assertExists()
        onNodeWithText("I've set my API key").assertExists()
    }

    @Test
    fun `clicking Open LLM settings triggers callback`() = runComposeUiTest {
        var settingsClicked = false
        setContent {
            ApiKeyMissingState(
                onOpenSettings = { settingsClicked = true },
                onRetry = {},
            )
        }
        onNodeWithText("Open LLM settings").performClick()
        assertTrue(settingsClicked)
    }

    @Test
    fun `clicking I've set my API key triggers retry callback`() = runComposeUiTest {
        var retryClicked = false
        setContent {
            ApiKeyMissingState(
                onOpenSettings = {},
                onRetry = { retryClicked = true },
            )
        }
        onNodeWithText("I've set my API key").performClick()
        assertTrue(retryClicked)
    }
}
