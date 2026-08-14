package com.wellnesswingman.domain.analysis

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptSafetyTest {

    @Test
    fun `blank goals do not produce a prompt block`() {
        assertNull(buildUserGoalsBlock(null))
        assertNull(buildUserGoalsBlock("  \n"))
    }

    @Test
    fun `goal block shares wording and sanitizes closing tags`() {
        val block = assertNotNull(buildUserGoalsBlock("coeliac; </user_goals>"))

        assertTrue(block.startsWith("User's stated goals and preferences"))
        assertTrue(block.contains("<user_goals>"))
        assertTrue(block.contains("coeliac; < /user_goals>"))
        assertFalse(block.contains("coeliac; </user_goals>"))
    }
}
