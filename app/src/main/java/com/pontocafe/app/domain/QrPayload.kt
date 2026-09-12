package com.pontocafe.app.domain

/**
 * O conteúdo do QR do café, do lado que lê.
 *
 * Espelha `backend/src/domain/qr-payload.ts`. Formato:
 * `PONTOCAFE1|<uuid do colaborador>|<código de 6>`
 *
 * A câmara lê tudo o que estiver à frente dela — um QR de wi-fi, um link, a
 * etiqueta de uma caixa. Por isso [parse] devolve null em vez de lançar: para
 * quem chama, "isto não é um QR do Ponto Café" é o caso normal enquanto a
 * pessoa ainda está a apontar, e não um erro para mostrar no ecrã.
 *
 * A versão vai no prefixo para que esta versão do aparelho recuse em silêncio
 * um formato futuro, em vez de o interpretar mal e abrir a pausa da pessoa
 * errada.
 */
object QrPayload {
    private const val PREFIX = "PONTOCAFE1"
    private const val SEPARATOR = '|'
    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    data class Lido(val colaboradorId: String, val codigo: String)

    fun parse(raw: String): Lido? {
        val partes = raw.trim().split(SEPARATOR)
        if (partes.size != 3) return null
        if (partes[0] != PREFIX) return null
        if (!UUID_REGEX.matches(partes[1])) return null

        val codigo = normalizarCodigo(partes[2]) ?: return null
        return Lido(colaboradorId = partes[1].lowercase(), codigo = codigo)
    }

    /**
     * Espelha `normalizeAccessCode` do backend, e **não** o saneamento do
     * teclado.
     *
     * A diferença é o corte. `AccessCode.sanitizeInput` faz `take(6)` porque
     * num teclado a sétima tecla simplesmente não entra — não há como digitar a
     * mais. Aqui a string chega inteira de uma vez: truncá-la faria um QR
     * adulterado com sete caracteres passar por um código válido de seis, que é
     * exactamente o que um formato de troca não pode deixar acontecer.
     *
     * O que se mantém é a tolerância visual: I e L valem 1, O vale 0, e
     * espaços, pontos, hífens e sublinhados são ruído de impressão.
     */
    private fun normalizarCodigo(bruto: String): String? {
        val limpo = bruto
            .uppercase()
            .replace(Regex("[\\s._-]"), "")
            .map { if (it == 'I' || it == 'L') '1' else if (it == 'O') '0' else it }
            .joinToString("")

        if (limpo.length != AccessCode.LENGTH) return null
        if (!limpo.all { AccessCode.ALPHABET.contains(it) }) return null
        return limpo
    }
}
