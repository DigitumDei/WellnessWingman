package com.wellnesswingman.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class UserCommentsManagerTest {

    @Test
    fun `optional text limit applies to loaded and edited text`() {
        assertEquals("12345", limitCommentText("123456", 5))
        assertEquals("123456", limitCommentText("123456", null))
    }
}
