package com.pontocafe.app.voice

import com.pontocafe.app.ComprovantePonto
import com.pontocafe.app.TipoComprovantePonto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PontoVoicePromptPolicyTest {

    @Test
    fun `o quiosque fala instrucoes acionaveis em portugues`() {
        val expected = mapOf(
            PontoVoiceKioskCue.ESCOLHER_PESSOA to "Toque no seu nome",
            PontoVoiceKioskCue.DIGITAR_CODIGO_SAIDA to "seis caracteres",
            PontoVoiceKioskCue.DIGITAR_CODIGO_RETORNO to "mesmo código",
        )

        expected.forEach { (cue, phrase) ->
            assertTrue(
                "A fala de $cue deve conter '$phrase'",
                PontoVoicePromptPolicy.kiosk(cue).text.contains(phrase, ignoreCase = true),
            )
        }
    }

    @Test
    fun `instrucoes de passo esperam antes de falar e nunca interrompem`() {
        val escolher = PontoVoicePromptPolicy.kiosk(PontoVoiceKioskCue.ESCOLHER_PESSOA)
        val saida = PontoVoicePromptPolicy.kiosk(PontoVoiceKioskCue.DIGITAR_CODIGO_SAIDA)

        // Quem acabou de chegar ao quiosque está lendo a tela, não esperando
        // narração: a fala só entra depois de o passo ficar estável.
        assertEquals(PontoVoicePriority.LOW, escolher.priority)
        assertTrue(escolher.stabilityDelayMillis >= 3_000L)
        assertTrue(escolher.cooldownMillis >= 30_000L)
        assertFalse(escolher.interrupt)

        assertEquals(PontoVoicePriority.INSTRUCTION, saida.priority)
        assertTrue(saida.stabilityDelayMillis >= 500L)
        assertTrue(saida.cooldownMillis >= 20_000L)
        assertFalse(saida.interrupt)
    }

    @Test
    fun `cooldown impede repetir a mesma fala mesmo trocando de ciclo`() {
        val gate = PontoVoiceGate()
        val prompt = PontoVoicePromptPolicy.kiosk(PontoVoiceKioskCue.DIGITAR_CODIGO_SAIDA)
        val session = "passo:9"

        assertTrue(gate.canSpeak(prompt, 10_000L, session))
        gate.markSpoken(prompt, 10_000L, session)
        assertFalse(gate.canSpeak(prompt, 10_500L, session))

        // Este era o furo: o quiosque passava uma chave de sessão que mudava a cada
        // ciclo, e prepareSession limpava o cooldown antes de ele ser consultado.
        // Trocar de sessão NÃO pode liberar a mesma fala.
        assertFalse(gate.canSpeak(prompt, 10_500L, "passo:10"))

        // Passado o cooldown declarado na política, volta a poder falar.
        assertTrue(gate.canSpeak(prompt, 10_000L + prompt.cooldownMillis, "passo:10"))
    }

    @Test
    fun `um passo que se repete nao repete a mesma instrucao`() {
        val gate = PontoVoiceGate()
        val prompt = PontoVoicePromptPolicy.kiosk(PontoVoiceKioskCue.ESCOLHER_PESSOA)

        // Dez recomposições seguidas, uma por segundo. A frase deve sair uma vez
        // só dentro da janela de cooldown.
        var spoken = 0
        repeat(10) { cycle ->
            val now = 1_000L + cycle * 1_000L
            if (gate.canSpeak(prompt, now, "passo:$cycle")) {
                gate.markSpoken(prompt, now, "passo:$cycle")
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
    fun `retorno excedido e recusas nunca soam como sucesso simples`() {
        val exceeded = receipt(
            tipo = TipoComprovantePonto.RETORNO,
            excedeuLimite = true,
        )

        assertTrue(
            PontoVoicePromptPolicy.receipt(exceeded).text.contains("limite da pausa foi excedido", ignoreCase = true),
        )
        assertEquals(
            PontoVoicePriority.CRITICAL,
            PontoVoicePromptPolicy.blocked("CODIGO_INVALIDO").priority,
        )
    }

    @Test
    fun `cada motivo de recusa pede a acao que resolve aquele caso`() {
        // Dizer "não foi possível registrar" para os quatro casos obrigaria a
        // pessoa a ler a tela para descobrir o que fazer -- exatamente o que a
        // voz existe para evitar.
        assertTrue(
            PontoVoicePromptPolicy.blocked("CODIGO_INVALIDO").text
                .contains("Confira os seis caracteres", ignoreCase = true),
        )
        assertTrue(
            PontoVoicePromptPolicy.blocked("CODIGO_EXPIRADO").text
                .contains("Peça um código novo", ignoreCase = true),
        )
        assertTrue(
            PontoVoicePromptPolicy.blocked("CODIGO_BLOQUEADO_TEMPORARIAMENTE").text
                .contains("Aguarde alguns minutos", ignoreCase = true),
        )
        assertTrue(
            PontoVoicePromptPolicy.blocked("PAUSA_PERIODO_JA_UTILIZADA").text
                .contains("já foi utilizada hoje", ignoreCase = true),
        )
        assertTrue(
            PontoVoicePromptPolicy.blocked("PAUSA_JA_ABERTA").text
                .contains("Registre o retorno", ignoreCase = true),
        )
        // Um código desconhecido cai no genérico em vez de ficar mudo.
        assertTrue(
            PontoVoicePromptPolicy.blocked(null).text
                .contains("Verifique a mensagem na tela", ignoreCase = true),
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
        carenciaSegundos = 60,
        excedeuLimite = excedeuLimite,
        foraHorario = false,
        pendenteSincronizacao = false,
    )
}
