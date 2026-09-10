package com.pontocafe.app.domain

object PontoCafeRules {
    const val STANDARD_COFFEE_LIMIT_SECONDS: Int = 15 * 60
    const val MIN_COFFEE_LIMIT_SECONDS: Int = 60
    const val MAX_COFFEE_LIMIT_SECONDS: Int = 120 * 60

    fun durationSeconds(minutes: Int, seconds: Int): Int {
        require(minutes >= 0) { "Minutos inválidos." }
        require(seconds in 0..59) { "Segundos inválidos." }
        val total = minutes * 60 + seconds
        require(total in MIN_COFFEE_LIMIT_SECONDS..MAX_COFFEE_LIMIT_SECONDS) {
            "O tempo deve ficar entre 1 e 120 minutos."
        }
        return total
    }

    fun splitDuration(totalSeconds: Int): Pair<Int, Int> {
        val safe = totalSeconds.coerceAtLeast(0)
        return safe / 60 to safe % 60
    }

    fun formatDuration(totalSeconds: Int): String {
        val (minutes, seconds) = splitDuration(totalSeconds)
        return "%02d:%02d".format(minutes, seconds)
    }
}
