package com.pontocafe.app.voice

/**
 * Ajustes de texto exclusivamente para fala. A cópia exibida na interface e os
 * contratos de negócio continuam intactos; apenas a forma enviada ao motor TTS
 * é tornada mais natural em pt-BR.
 */
internal object PontoVoiceTextNormalizer {
    private val clockPattern = Regex("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)")

    fun normalize(text: String): String = clockPattern.replace(text.trim()) { match ->
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        speakClock(hour, minute)
    }

    private fun speakClock(hour: Int, minute: Int): String {
        val spokenHour = when (hour) {
            1 -> "uma"
            2 -> "duas"
            else -> number(hour, feminineOneTwo = false)
        }
        if (minute == 0) {
            return if (hour == 1) "$spokenHour hora" else "$spokenHour horas"
        }
        return "$spokenHour e ${number(minute, feminineOneTwo = false)}"
    }

    private fun number(value: Int, feminineOneTwo: Boolean): String {
        require(value in 0..59)
        val units = if (feminineOneTwo) {
            listOf("zero", "uma", "duas", "três", "quatro", "cinco", "seis", "sete", "oito", "nove")
        } else {
            listOf("zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove")
        }
        if (value < 10) return units[value]
        val teens = mapOf(
            10 to "dez",
            11 to "onze",
            12 to "doze",
            13 to "treze",
            14 to "quatorze",
            15 to "quinze",
            16 to "dezesseis",
            17 to "dezessete",
            18 to "dezoito",
            19 to "dezenove",
        )
        teens[value]?.let { return it }
        val tens = mapOf(
            20 to "vinte",
            30 to "trinta",
            40 to "quarenta",
            50 to "cinquenta",
        )
        val base = (value / 10) * 10
        val unit = value % 10
        val prefix = tens.getValue(base)
        return if (unit == 0) prefix else "$prefix e ${units[unit]}"
    }
}
