package com.pontocafe.app.voice

import com.pontocafe.app.ComprovantePonto
import com.pontocafe.app.TipoComprovantePonto
import org.junit.Assert.assertEquals
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
    fun `estados transitórios esperam antes de falar e possuem cooldown longo`() {
        val noFace = PontoVoicePromptPolicy.kiosk(PontoVoiceKioskCue.NO_FACE)
        val look = PontoVoicePromptPolicy.kiosk(PontoVoiceKioskCue.LOOK_AT_CAMERA)
        val multiple = PontoVoicePromptPolicy.kiosk(PontoVoiceKioskCue.MULTIPLE_FACES)

        assertEquals(PontoVoicePriority.LOW, noFace.priority)
        assertTrue(noFace.stabilityDelayMillis >= 5_000L)
        assertTrue(noFace.cooldownMillis >= 30_000L)
        assertTrue(look.stabilityDelayMillis >= 1_500L)
        assertTrue(look.cooldownMillis >= 15_000L)
        assertTrue(multiple.stabilityDelayMillis >= 1_000L)
    }

    @Test
    fun `liveness limita narracao a tres instrucoes por ciclo`() {
        val gate = PontoVoiceGate()
        val session = "scan:1"
        val cues = listOf(
            PontoVoiceKioskCue.BLINK,
            PontoVoiceKioskCue.OPEN_EYES,
            PontoVoiceKioskCue.TURN_LEFT,
            PontoVoiceKioskCue.CENTER_FACE,
        )

        cues.take(3).forEachIndexed { index, cue ->
            val prompt = PontoVoicePromptPolicy.kiosk(cue)
            val now = 1_000L + index
            assertTrue(gate.canSpeak(prompt, now, session))
            gate.markSpoken(prompt, now, session)
        }

        val fourth = PontoVoicePromptPolicy.kiosk(cues.last())
        assertFalse(gate.canSpeak(fourth, 2_000L, session))
        assertTrue(gate.canSpeak(fourth, 2_001L, "scan:2"))
    }

    @Test
    fun `cooldown impede repetir a mesma fala mesmo trocando de ciclo`() {
        val gate = PontoVoiceGate()
        val prompt = PontoVoicePromptPolicy.kiosk(PontoVoiceKioskCue.BLINK)
        val session = "scan:9"

        assertTrue(gate.canSpeak(prompt, 10_000L, session))
        gate.markSpoken(prompt, 10_000L, session)
        assertFalse(gate.canSpeak(prompt, 10_500L, session))

        // Este era o furo: o quiosque passa "scan:${scanCycle}" e scanCycle sobe a
        // cada tentativa de leitura, então prepareSession limpava o cooldown antes
        // de ele ser consultado. Trocar de ciclo NÃO pode liberar a mesma fala.
        assertFalse(gate.canSpeak(prompt, 10_500L, "scan:10"))

        // Passado o cooldown declarado na política, volta a poder falar.
        assertTrue(gate.canSpeak(prompt, 10_000L + prompt.cooldownMillis, "scan:10"))
    }

    @Test
    fun `nao detectar varias vezes seguidas nao repete a mesma instrucao`() {
        val gate = PontoVoiceGate()
        val prompt = PontoVoicePromptPolicy.kiosk(PontoVoiceKioskCue.NO_FACE)

        // Dez ciclos seguidos sem detectar, um por segundo: é o caso relatado em
        // operação. A frase deve sair uma vez só dentro da janela de cooldown.
        var spoken = 0
        repeat(10) { cycle ->
            val now = 1_000L + cycle * 1_000L
            if (gate.canSpeak(prompt, now, "scan:$cycle")) {
                gate.markSpoken(prompt, now, "scan:$cycle")
                spoken += 1
            }
        }

        assertEquals(1, spoken)
    }

    @Test
    fun `inicio da pausa fala prazo de retorno e estado offline`() {
        val online = receipt(
            tipo = TipoComprovantePonto.INICIO,
            retornoAte = "08:47",
        )
        val offline = online.copy(pendenteSincronizacao = true)

        val onlinePrompt = PontoVoicePromptPolicy.receipt(online)
        val offlinePrompt = PontoVoicePromptPolicy.receipt(offline)

        assertEquals(PontoVoicePriority.RESULT, onlinePrompt.priority)
        assertTrue(onlinePrompt.text.contains("Retorne até 08:47"))
        assertFalse(onlinePrompt.text.contains("sincronizado", ignoreCase = true))
        assertTrue(offlinePrompt.text.contains("salvo neste aparelho", ignoreCase = true))
        assertTrue(offlinePrompt.text.contains("sincronizado", ignoreCase = true))
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
        assertEquals(
            PontoVoicePriority.CRITICAL,
            PontoVoicePromptPolicy.blocked("PAUSAS_DO_DIA_JA_UTILIZADAS").priority,
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
