package com.pontocafe.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O formato do QR existe duas vezes: aqui e em
 * `backend/src/domain/qr-payload.ts`. Uma divergência entre os dois não daria
 * erro nenhum — daria uma câmara que lê e nunca reconhece, sem nada no ecrã a
 * dizer porquê. Estes casos são a corda que prende as duas pontas.
 */
class QrPayloadTest {

    private val colaborador = "3f8a1c02-9b4d-4e77-8a10-6c5e2d9f1b33"

    @Test
    fun `le o payload que o backend gera`() {
        val lido = QrPayload.parse("PONTOCAFE1|$colaborador|A7K2M9")

        assertEquals(colaborador, lido?.colaboradorId)
        assertEquals("A7K2M9", lido?.codigo)
    }

    @Test
    fun `tolera as confusoes visuais que o alfabeto ja tolera no teclado`() {
        // I e L valem 1, O vale 0. Um QR impresso e relido por uma câmara suja
        // não pode falhar por um símbolo que o Crockford Base32 excluiu
        // justamente por ser indistinguível.
        val lido = QrPayload.parse("PONTOCAFE1|$colaborador|AIKOM9")

        assertEquals("A1K0M9", lido?.codigo)
    }

    @Test
    fun `aceita o uuid em maiusculas e devolve-o normalizado`() {
        val lido = QrPayload.parse("PONTOCAFE1|${colaborador.uppercase()}|A7K2M9")

        assertEquals(colaborador, lido?.colaboradorId)
    }

    @Test
    fun `ignora em silencio o que nao e um QR do Ponto Cafe`() {
        // A câmara lê tudo o que estiver à frente. Nenhum destes é um erro para
        // mostrar a quem ainda está a apontar.
        assertNull(QrPayload.parse("https://exemplo.com"))
        assertNull(QrPayload.parse("WIFI:S:cafe;T:WPA;P:segredo;;"))
        assertNull(QrPayload.parse(""))
        assertNull(QrPayload.parse("PONTOCAFE1|$colaborador"))
        assertNull(QrPayload.parse("PONTOCAFE1|$colaborador|A7K2M9|extra"))
    }

    @Test
    fun `recusa uma versao de formato que nao conhece`() {
        // Recusar é o comportamento seguro: interpretar um formato futuro com
        // as regras de hoje podia abrir a pausa da pessoa errada.
        assertNull(QrPayload.parse("PONTOCAFE2|$colaborador|A7K2M9"))
        assertNull(QrPayload.parse("PONTOCAFE|$colaborador|A7K2M9"))
    }

    @Test
    fun `recusa um colaborador que nao e uuid`() {
        assertNull(QrPayload.parse("PONTOCAFE1|nao-e-uuid|A7K2M9"))
        assertNull(QrPayload.parse("PONTOCAFE1||A7K2M9"))
    }

    @Test
    fun `recusa um codigo com tamanho errado ou fora do alfabeto`() {
        assertNull(QrPayload.parse("PONTOCAFE1|$colaborador|A7K2M"))
        assertNull(QrPayload.parse("PONTOCAFE1|$colaborador|A7K2M99"))
        // U está fora do alfabeto de propósito, para nenhum sorteio produzir
        // uma palavra ofensiva. Não há mapeamento para ele.
        assertNull(QrPayload.parse("PONTOCAFE1|$colaborador|A7K2MU"))
    }
}
