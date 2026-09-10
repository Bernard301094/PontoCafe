package com.pontocafe.app.ui

import com.pontocafe.app.data.PausaSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupervisorLiveAlertsTest {

    @Test
    fun `nenhuma mudanca nao gera alerta`() {
        val alert = selectSupervisorLiveAlert(
            novas = emptyList(),
            retornos = emptyList(),
            novosExcessos = emptyList(),
            novosCriticos = emptyList(),
            novosAvisos = emptyList(),
            alertId = 1L,
        )

        assertNull(alert)
    }

    @Test
    fun `uma nova saida gera alerta do tipo SAIDA com o id informado`() {
        val alert = selectSupervisorLiveAlert(
            novas = listOf(pausa("1", "Ana")),
            retornos = emptyList(),
            novosExcessos = emptyList(),
            novosCriticos = emptyList(),
            novosAvisos = emptyList(),
            alertId = 42L,
        )

        assertEquals(SupervisorLiveAlertType.SAIDA.name, alert?.type)
        assertEquals(42L, alert?.id)
    }

    @Test
    fun `um retorno isolado gera alerta do tipo RETORNO`() {
        val alert = selectSupervisorLiveAlert(
            novas = emptyList(),
            retornos = listOf(pausa("1", "Ana")),
            novosExcessos = emptyList(),
            novosCriticos = emptyList(),
            novosAvisos = emptyList(),
            alertId = 1L,
        )

        assertEquals(SupervisorLiveAlertType.RETORNO.name, alert?.type)
    }

    @Test
    fun `saidas e retornos simultaneos geram alerta misto`() {
        val alert = selectSupervisorLiveAlert(
            novas = listOf(pausa("1", "Ana")),
            retornos = listOf(pausa("2", "Bruno")),
            novosExcessos = emptyList(),
            novosCriticos = emptyList(),
            novosAvisos = emptyList(),
            alertId = 1L,
        )

        assertEquals(SupervisorLiveAlertType.MISTO.name, alert?.type)
    }

    @Test
    fun `excesso de limite tem prioridade sobre qualquer outra condicao`() {
        val alert = selectSupervisorLiveAlert(
            novas = listOf(pausa("1", "Ana")),
            retornos = listOf(pausa("2", "Bruno")),
            novosExcessos = listOf(pausa("3", "Carla")),
            novosCriticos = listOf(pausa("4", "Diego")),
            novosAvisos = listOf(pausa("5", "Elis")),
            alertId = 1L,
        )

        assertEquals(SupervisorLiveAlertType.EXCESSO.name, alert?.type)
    }

    @Test
    fun `estado critico tem prioridade sobre aviso mas nao sobre excesso`() {
        val alert = selectSupervisorLiveAlert(
            novas = emptyList(),
            retornos = emptyList(),
            novosExcessos = emptyList(),
            novosCriticos = listOf(pausa("4", "Diego")),
            novosAvisos = listOf(pausa("5", "Elis")),
            alertId = 1L,
        )

        assertEquals(SupervisorLiveAlertType.CRITICO.name, alert?.type)
    }

    private fun pausa(id: String, nome: String) = PausaSupervisor(
        id = id,
        periodo = "MANHA",
        inicioLocal = "10:00",
        limiteSegundos = 900,
        foraHorario = false,
        colaboradorId = "colab-$id",
        nome = nome,
        setor = "Produção",
    )
}
