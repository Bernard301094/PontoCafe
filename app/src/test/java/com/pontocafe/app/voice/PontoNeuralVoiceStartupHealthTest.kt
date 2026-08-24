package com.pontocafe.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class PontoNeuralVoiceStartupHealthTest {

    @Test
    fun `motor pronto confirma saudavel imediatamente, mesmo perto do timeout`() {
        assertEquals(
            NaturalVoiceHealthCheckStep.CONFIRMED_READY,
            nextNaturalVoiceHealthCheckStep(
                availability = PontoNeuralVoiceAvailability.READY,
                elapsedMillis = 0L,
                timeoutMillis = 4_000L,
            ),
        )
        assertEquals(
            NaturalVoiceHealthCheckStep.CONFIRMED_READY,
            nextNaturalVoiceHealthCheckStep(
                availability = PontoNeuralVoiceAvailability.READY,
                elapsedMillis = 3_999L,
                timeoutMillis = 4_000L,
            ),
        )
    }

    @Test
    fun `motor com falha confirmada e detectado de imediato, sem esperar o timeout`() {
        assertEquals(
            NaturalVoiceHealthCheckStep.CONFIRMED_FAILED,
            nextNaturalVoiceHealthCheckStep(
                availability = PontoNeuralVoiceAvailability.FAILED,
                elapsedMillis = 0L,
                timeoutMillis = 4_000L,
            ),
        )
    }

    @Test
    fun `preparacao lenta continua sendo verificada antes do timeout`() {
        for (availability in listOf(PontoNeuralVoiceAvailability.IDLE, PontoNeuralVoiceAvailability.PREPARING)) {
            assertEquals(
                availability.name,
                NaturalVoiceHealthCheckStep.STILL_CHECKING,
                nextNaturalVoiceHealthCheckStep(availability, elapsedMillis = 1_000L, timeoutMillis = 4_000L),
            )
        }
    }

    @Test
    fun `carregamento lento apos o timeout nunca e tratado como falha`() {
        for (availability in listOf(PontoNeuralVoiceAvailability.IDLE, PontoNeuralVoiceAvailability.PREPARING)) {
            assertEquals(
                availability.name,
                NaturalVoiceHealthCheckStep.TIMED_OUT_ASSUME_READY,
                nextNaturalVoiceHealthCheckStep(availability, elapsedMillis = 4_000L, timeoutMillis = 4_000L),
            )
        }
    }
}
