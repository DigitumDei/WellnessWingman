package com.wellnesswingman.domain.capture

import com.wellnesswingman.platform.FileSystem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PendingCaptureStoreTest {

    private val files = mutableMapOf<String, ByteArray>()
    private val directories = mutableSetOf("/app/data")

    private val fileSystem: FileSystem = mockk(relaxed = true) {
        every { getAppDataDirectory() } returns "/app/data"
        every { exists(any()) } answers {
            val p = firstArg<String>()
            files.containsKey(p) || directories.contains(p)
        }
        every { createDirectory(any()) } answers {
            directories.add(firstArg()); true
        }
        coEvery { writeBytes(any(), any()) } answers {
            files[firstArg()] = secondArg()
        }
        coEvery { readBytes(any()) } answers {
            files[firstArg()] ?: ByteArray(0)
        }
        coEvery { delete(any()) } answers {
            files.remove(firstArg()) != null
        }
    }

    private val store = PendingCaptureStore(fileSystem)

    @Test
    fun `get returns null when no pending capture exists`() = runTest {
        assertNull(store.get())
    }

    @Test
    fun `save and get round-trips pending capture with workflow fields`() = runTest {
        val capture = PendingCapture(
            photoFilePath = "/app/data/pending_photos/photo_1.jpg",
            capturedAtMillis = 1000L,
            notes = "lunch notes",
            phase = CapturePhase.PROCESSING,
            entryId = 42L,
            source = CaptureSource.GALLERY
        )
        store.save(capture)

        val recovered = store.get()
        assertNotNull(recovered)
        assertEquals(capture, recovered)
    }

    @Test
    fun `update advances phase and records entry id without losing other fields`() = runTest {
        val initial = PendingCapture(
            photoFilePath = "/app/data/pending_photos/photo_2.jpg",
            capturedAtMillis = 2000L,
            source = CaptureSource.CAMERA
        )
        store.save(initial)

        store.update { it?.copy(phase = CapturePhase.DONE, entryId = 7L, notes = "updated") }

        val recovered = store.get()
        assertNotNull(recovered)
        assertEquals(CapturePhase.DONE, recovered.phase)
        assertEquals(7L, recovered.entryId)
        assertEquals("updated", recovered.notes)
        assertEquals(CaptureSource.CAMERA, recovered.source)
        assertEquals(initial.photoFilePath, recovered.photoFilePath)
    }

    @Test
    fun `update writes nothing when transform returns null`() = runTest {
        val capture = PendingCapture(
            photoFilePath = "/app/data/pending_photos/photo_3.jpg",
            capturedAtMillis = 3000L
        )
        store.save(capture)

        store.update { null }

        assertNotNull(store.get(), "Returning null from update should leave the record intact")
    }

    @Test
    fun `update on missing record writes the supplied value`() = runTest {
        store.update { PendingCapture("/app/data/pending_photos/photo_4.jpg", 4000L) }

        val recovered = store.get()
        assertNotNull(recovered)
        assertEquals("/app/data/pending_photos/photo_4.jpg", recovered.photoFilePath)
    }

    @Test
    fun `clear removes the pending capture record`() = runTest {
        store.save(PendingCapture("/app/data/pending_photos/photo_5.jpg", 5000L))
        assertNotNull(store.get())

        store.clear()

        assertNull(store.get())
    }

    @Test
    fun `apiKeyMissing survives round-trip`() = runTest {
        val capture = PendingCapture(
            photoFilePath = "/app/data/pending_photos/photo_key.jpg",
            capturedAtMillis = 1000L,
            apiKeyMissing = true
        )
        store.save(capture)

        val recovered = store.get()
        assertNotNull(recovered)
        assertTrue(recovered.apiKeyMissing)
    }

    @Test
    fun `getPendingPhotosDirectory creates and returns the pending photos dir`() {
        val dir = store.getPendingPhotosDirectory()
        assertEquals("/app/data/pending_photos", dir)
        assertEquals(setOf("/app/data", "/app/data/pending_photos"), directories)
    }
}
