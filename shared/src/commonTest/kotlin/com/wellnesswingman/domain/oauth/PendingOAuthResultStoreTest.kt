package com.wellnesswingman.domain.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PendingOAuthResultStoreTest {

    @Test
    fun `result is initially null`() {
        val store = PendingOAuthResultStore()
        assertNull(store.result.value)
    }

    @Test
    fun `deliver sets sessionId and state`() {
        val store = PendingOAuthResultStore()
        store.deliver("session-123", "state-abc")

        val result = store.result.value
        assertNotNull(result)
        assertEquals("session-123", result.sessionId)
        assertEquals("state-abc", result.state)
        assertNull(result.error)
    }

    @Test
    fun `deliverError sets error field`() {
        val store = PendingOAuthResultStore()
        store.deliverError("access_denied")

        val result = store.result.value
        assertNotNull(result)
        assertEquals("access_denied", result.error)
        assertNull(result.sessionId)
        assertNull(result.state)
    }

    @Test
    fun `consume returns result and clears it`() {
        val store = PendingOAuthResultStore()
        store.deliver("session-123", "state-abc")

        val consumed = store.consume()
        assertNotNull(consumed)
        assertEquals("session-123", consumed.sessionId)

        assertNull(store.result.value)
        assertNull(store.consume())
    }

    @Test
    fun `consume returns null when no result`() {
        val store = PendingOAuthResultStore()
        assertNull(store.consume())
    }

    @Test
    fun `deliver overwrites previous result`() {
        val store = PendingOAuthResultStore()
        store.deliver("first", "state-1")
        store.deliver("second", "state-2")

        val result = store.result.value
        assertNotNull(result)
        assertEquals("second", result.sessionId)
    }
}
