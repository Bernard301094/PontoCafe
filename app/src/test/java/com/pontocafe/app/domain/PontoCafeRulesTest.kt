package com.pontocafe.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PontoCafeRulesTest {
    @Test
    fun `limite padrao e quinze minutos`() {
        assertEquals(900, PontoCafeRules.STANDARD_COFFEE_LIMIT_SECONDS)
        assertEquals("15:00", PontoCafeRules.formatDuration(PontoCafeRules.STANDARD_COFFEE_LIMIT_SECONDS))
    }

    @Test
    fun `converte minutos e segundos sem perder precisao`() {
        assertEquals(900, PontoCafeRules.durationSeconds(15, 0))
        assertEquals(915, PontoCafeRules.durationSeconds(15, 15))
    }

    @Test
    fun `rejeita segundos invalidos`() {
        assertThrows(IllegalArgumentException::class.java) {
            PontoCafeRules.durationSeconds(15, 60)
        }
    }
}
