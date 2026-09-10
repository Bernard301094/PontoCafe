package com.pontocafe.app.domain

/**
 * Regras do código de acesso ao café, do lado do quiosque.
 *
 * São as mesmas do backend (`backend/src/domain/access-code.ts`) e existem aqui
 * por uma razão de teclado, não de segurança: o campo precisa recusar de
 * imediato o que nunca poderia ser um código e precisa aceitar as confusões
 * previsíveis de quem lê um papel — I e L viram 1, O vira 0. Quem valida o
 * código de verdade continua a ser o servidor.
 */
object AccessCode {
    /** Crockford Base32: dígitos e letras sem I, L, O e U. */
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH = 6

    /** Converte uma tecla digitada no símbolo canónico, ou null se não existir. */
    fun canonicalChar(char: Char): Char? {
        val upper = char.uppercaseChar()
        val mapped = when (upper) {
            'I', 'L' -> '1'
            'O' -> '0'
            else -> upper
        }
        return if (ALPHABET.contains(mapped)) mapped else null
    }

    /** Aplica [canonicalChar] a tudo o que veio do teclado e corta no tamanho. */
    fun sanitizeInput(raw: String): String =
        raw.mapNotNull { canonicalChar(it) }.joinToString("").take(LENGTH)

    fun isComplete(value: String): Boolean =
        value.length == LENGTH && value.all { ALPHABET.contains(it) }

    /** `A7K2M9` -> `A7K-2M9`, o formato em que o Supervisor dita o código. */
    fun format(value: String): String =
        if (value.length == LENGTH) "${value.take(3)}-${value.drop(3)}" else value
}
