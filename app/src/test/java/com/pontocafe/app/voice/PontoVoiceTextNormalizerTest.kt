package com.pontocafe.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class PontoVoiceTextNormalizerTest {
    @Test
    fun `horario de retorno fica natural em portugues`() {
        assertEquals(
            "Pausa iniciada. Retorne até oito e quarenta e sete.",
            PontoVoiceTextNormalizer.normalize("Pausa iniciada. Retorne até 08:47."),
        )
    }

    @Test
    fun `hora cheia usa singular e plural corretamente`() {
        assertEquals("Retorne até uma hora.", PontoVoiceTextNormalizer.normalize("Retorne até 01:00."))
        assertEquals("Retorne até oito horas.", PontoVoiceTextNormalizer.normalize("Retorne até 08:00."))
    }

    @Test
    fun `texto sem horario permanece inalterado`() {
        val text = "Retorno registrado com sucesso."
        assertEquals(text, PontoVoiceTextNormalizer.normalize(text))
    }
}
