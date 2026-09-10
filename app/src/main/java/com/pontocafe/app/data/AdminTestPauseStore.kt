package com.pontocafe.app.data

import com.pontocafe.app.domain.PontoCafeRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simulação visual criada pelo Administrador para conferir a tela Ao vivo.
 *
 * Este estado existe somente na memória do processo Android: não chama API,
 * não grava SharedPreferences, não entra na fila offline e não gera auditoria.
 * Ao encerrar o processo/app, o teste desaparece automaticamente.
 */
data class AdminTestPause(
    val id: String,
    val adminName: String,
    val startedAtMillis: Long,
    val limitSeconds: Int,
)

object AdminTestPauseStore {
    private val _active = MutableStateFlow<AdminTestPause?>(null)
    val active: StateFlow<AdminTestPause?> = _active.asStateFlow()

    fun start(
        adminName: String,
        limitSeconds: Int = PontoCafeRules.STANDARD_COFFEE_LIMIT_SECONDS,
    ) {
        val now = System.currentTimeMillis()
        _active.value = AdminTestPause(
            id = "admin-test-$now",
            adminName = adminName.trim().ifBlank { "Administrador" },
            startedAtMillis = now,
            limitSeconds = limitSeconds.coerceAtLeast(1),
        )
    }

    fun stop() {
        _active.value = null
    }
}
