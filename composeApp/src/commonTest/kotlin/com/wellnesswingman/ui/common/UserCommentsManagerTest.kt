package com.wellnesswingman.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserCommentsManagerTest {

    @Test
    fun `optional text limit applies to loaded and edited text`() {
        assertEquals("12345", limitCommentText("123456", 5))
        assertEquals("123456", limitCommentText("123456", null))
    }

    @Test
    fun `transcription reports when the appended text is truncated`() {
        val result = appendTranscriptionText("1234", "56789", 5)

        assertEquals("1234", result.text)
        assertTrue(result.wasTruncated)
    }

    @Test
    fun `transcription preserves full text when it fits`() {
        val result = appendTranscriptionText("1234", "567", 10)

        assertEquals("1234\n567", result.text)
        assertFalse(result.wasTruncated)
    }
}
