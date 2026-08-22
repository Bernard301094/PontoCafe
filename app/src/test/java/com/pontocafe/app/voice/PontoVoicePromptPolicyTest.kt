package com.pontocafe.app.voice

import com.pontocafe.app.ComprovantePonto
import com.pontocafe.app.TipoComprovantePonto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PontoVoicePromptPolicyTest {

    @Test
    fun `liveness fala instrucoes acionaveis em portugues`() {
        val expected = mapOf(
            PontoVoiceKioskCue.LOOK_AT_CAMERA to "Olhe para a câmera",
            PontoVoiceKioskCue.BLINK to "Pisque uma vez",
            PontoVoiceKioskCue.OPEN_EYES to "Agora abra os olhos",
            PontoVoiceKioskCue.TURN_LEFT to "esquerda",
            PontoVoiceKioskCue.TURN_RIGHT to "direita",
            PontoVoiceKioskCue.CENTER_FACE to "Volte ao centro",
            PontoVoiceKioskCue.MULTIPLE_FACES to "Apenas uma pessoa por vez",
            PontoVoiceKioskCue.FACE_NOT_RECOGNIZED to "Rosto não reconhecido",
        )

        expected.forEach { (cue, phrase) ->
            assertTrue(
                "A fala de $cue deve conter '$phrase'",
                PontoVoicePromptPolicy.kiosk(cue).text.contains(phrase, ignoreCase = true),
            )
        }
    }

    @Test
    fun `inicio da pausa fala prazo de retorno e estado offline`() {
        val online = receipt(
            tipo = TipoComprovantePonto.INICIO,
            retornoAte = "08:47",
        )
        val offline = online.copy(pendenteSincronizacao = true)

        val onlinePrompt = PontoVoicePromptPolicy.receipt(online).text
        val offlinePrompt = PontoVoicePromptPolicy.receipt(offline).text

        assertTrue(onlinePrompt.contains("Retorne até 08:47"))
        assertFalse(onlinePrompt.contains("sincronizado", ignoreCase = true))
        assertTrue(offlinePrompt.contains("salvo neste aparelho", ignoreCase = true))
        assertTrue(offlinePrompt.contains("sincronizado", ignoreCase = true))
    }

    @Test
    fun `retorno excedido e bloqueios nunca soam como sucesso simples`() {
        val exceeded = receipt(
            tipo = TipoComprovantePonto.RETORNO,
            excedeuLimite = true,
        )

        assertTrue(
            PontoVoicePromptPolicy.receipt(exceeded).text.contains("limite da pausa foi excedido", ignoreCase = true),
        )
        assertTrue(
            PontoVoicePromptPolicy.blocked("PAUSAS_DO_DIA_JA_UTILIZADAS").text
                .contains("Não há mais pausa disponível", ignoreCase = true),
        )
        assertTrue(
            PontoVoicePromptPolicy.blocked("FORA_HORARIO_NAO_LIBERADO").text
                .contains("Solicite uma liberação", ignoreCase = true),
        )
    }

    private fun receipt(
        tipo: TipoComprovantePonto,
        retornoAte: String? = null,
        excedeuLimite: Boolean = false,
    ) = ComprovantePonto(
        tipo = tipo,
        nome = "Pessoa Teste",
        horarioRegistrado = "08:40",
        retornoAte = retornoAte,
        duracaoSegundos = if (tipo == TipoComprovantePonto.RETORNO) 500 else null,
        limiteSegundos = 450,
        excedeuLimite = excedeuLimite,
        foraHorario = false,
        pendenteSincronizacao = false,
    )
}
