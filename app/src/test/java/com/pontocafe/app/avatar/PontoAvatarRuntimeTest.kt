package com.pontocafe.app.avatar

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PontoAvatarRuntimeTest {
    @After
    fun tearDown() {
        PontoAvatarRuntime.clearCatalog()
    }

    @Test
    fun `independent catalog overrides stale face catalog avatar`() {
        PontoAvatarRuntime.updateCatalog(mapOf("employee-1" to "https://avatar.test/new?v=2"))

        PontoAvatarRuntime.recognized("employee-1", "https://avatar.test/old?v=1")

        assertEquals("https://avatar.test/new?v=2", PontoAvatarRuntime.lastRecognizedAvatarUrl)
    }

    @Test
    fun `just uploaded avatar is visible immediately and deletion becomes placeholder`() {
        PontoAvatarRuntime.updateCatalog(mapOf("employee-1" to "https://avatar.test/old?v=1"))
        PontoAvatarRuntime.avatarUpdated("employee-1", "https://avatar.test/new?v=2")
        PontoAvatarRuntime.recognized("employee-1")
        assertEquals("https://avatar.test/new?v=2", PontoAvatarRuntime.lastRecognizedAvatarUrl)

        PontoAvatarRuntime.avatarUpdated("employee-1", null)
        PontoAvatarRuntime.recognized("employee-1", "https://avatar.test/old?v=1")
        assertNull(PontoAvatarRuntime.lastRecognizedAvatarUrl)
    }

    @Test
    fun `stale refresh cannot replace a just uploaded avatar`() {
        val oldUrl = "https://avatar.test/old?v=1"
        val newUrl = "https://avatar.test/new?v=2"
        PontoAvatarRuntime.updateCatalog(mapOf("employee-1" to oldUrl))
        PontoAvatarRuntime.avatarUpdated("employee-1", newUrl)

        PontoAvatarRuntime.updateCatalog(mapOf("employee-1" to oldUrl))
        PontoAvatarRuntime.recognized("employee-1")
        assertEquals(newUrl, PontoAvatarRuntime.lastRecognizedAvatarUrl)

        PontoAvatarRuntime.updateCatalog(mapOf("employee-1" to newUrl))
        PontoAvatarRuntime.recognized("employee-1")
        assertEquals(newUrl, PontoAvatarRuntime.lastRecognizedAvatarUrl)
    }

    @Test
    fun `stale refresh cannot restore a just deleted avatar`() {
        val oldUrl = "https://avatar.test/old?v=1"
        PontoAvatarRuntime.updateCatalog(mapOf("employee-1" to oldUrl))
        PontoAvatarRuntime.avatarUpdated("employee-1", null)

        PontoAvatarRuntime.updateCatalog(mapOf("employee-1" to oldUrl))
        PontoAvatarRuntime.recognized("employee-1")
        assertNull(PontoAvatarRuntime.lastRecognizedAvatarUrl)

        PontoAvatarRuntime.updateCatalog(emptyMap())
        PontoAvatarRuntime.recognized("employee-1")
        assertNull(PontoAvatarRuntime.lastRecognizedAvatarUrl)
    }

    @Test
    fun `receipt clear does not discard visual catalog`() {
        PontoAvatarRuntime.updateCatalog(mapOf("employee-1" to "https://avatar.test/current?v=3"))
        PontoAvatarRuntime.recognized("employee-1")
        PontoAvatarRuntime.clear()
        assertNull(PontoAvatarRuntime.lastRecognizedAvatarUrl)

        PontoAvatarRuntime.recognized("employee-1")
        assertEquals("https://avatar.test/current?v=3", PontoAvatarRuntime.lastRecognizedAvatarUrl)
    }
}
