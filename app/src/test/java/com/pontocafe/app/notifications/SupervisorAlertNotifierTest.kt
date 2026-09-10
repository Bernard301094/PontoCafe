package com.pontocafe.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SupervisorAlertNotifierTest {

    @Test
    fun `mesma chave unica produz sempre o mesmo id de notificacao`() {
        val first = SupervisorAlertNotifier.stableNotificationId("SAIDA", "Saída", "Ana saiu", uniqueKey = 100L)
        val second = SupervisorAlertNotifier.stableNotificationId("SAIDA", "Saída", "Ana saiu", uniqueKey = 100L)

        assertEquals(first, second)
    }

    @Test
    fun `eventos distintos com texto identico nao colidem quando tem chaves diferentes`() {
        val first = SupervisorAlertNotifier.stableNotificationId("SAIDA", "Saída para o café", "Ana bateu o ponto e saiu para o café.", uniqueKey = 1L)
        val second = SupervisorAlertNotifier.stableNotificationId("SAIDA", "Saída para o café", "Ana bateu o ponto e saiu para o café.", uniqueKey = 2L)

        assertNotEquals(first, second)
    }

    @Test
    fun `sem chave unica o id permanece estavel para o mesmo conteudo`() {
        val first = SupervisorAlertNotifier.stableNotificationId("TESTE", "Título", "Mensagem")
        val second = SupervisorAlertNotifier.stableNotificationId("TESTE", "Título", "Mensagem")

        assertEquals(first, second)
    }
}
