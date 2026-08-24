package com.pontocafe.app.voice

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Covers the integrity primitive that [PontoNeuralVoiceRuntime]'s model-reuse
 * check now always calls on every reuse (instead of trusting a marker file
 * alone) — see ensureModelInstalled() in PontoNeuralVoice.kt.
 */
class PontoNeuralVoiceModelIntegrityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun expectedSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { byte -> "%02x".format(byte) }
    }

    @Test
    fun `hash de um arquivo integro corresponde ao SHA-256 esperado`() {
        val bytes = ByteArray(4096) { index -> (index % 251).toByte() }
        val file = tempFolder.newFile("modelo-integro.onnx")
        file.writeBytes(bytes)

        assertEquals(expectedSha256(bytes), PontoNeuralVoiceRuntime.sha256(file))
    }

    @Test
    fun `um unico byte corrompido muda o hash, revelando uma instalacao quebrada`() {
        val bytes = ByteArray(4096) { index -> (index % 251).toByte() }
        val file = tempFolder.newFile("modelo-corrompido.onnx")
        file.writeBytes(bytes)
        val originalHash = PontoNeuralVoiceRuntime.sha256(file)

        val corrupted = bytes.copyOf()
        corrupted[2048] = (corrupted[2048] + 1).toByte()
        file.writeBytes(corrupted)

        assertNotEquals(originalHash, PontoNeuralVoiceRuntime.sha256(file))
        assertEquals(expectedSha256(corrupted), PontoNeuralVoiceRuntime.sha256(file))
    }
}
