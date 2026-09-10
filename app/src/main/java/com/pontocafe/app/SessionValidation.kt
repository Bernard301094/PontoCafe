package com.pontocafe.app

import com.pontocafe.app.data.AdminRepository
import com.pontocafe.app.data.SupervisorRepository
import retrofit2.HttpException

private fun Throwable.invalidStoredSession(): Boolean =
    this is HttpException && (code() == 401 || code() == 403)

suspend fun AdminRepository.validarSessaoSalva(): Boolean {
    if (!hasSession()) return false
    return try {
        users()
        true
    } catch (error: Throwable) {
        if (error.invalidStoredSession()) {
            clearSession()
            false
        } else {
            // Falha de rede/servidor não deve apagar uma sessão potencialmente válida.
            true
        }
    }
}

suspend fun SupervisorRepository.validarSessaoSalva(): Boolean {
    if (!hasSession()) return false
    return try {
        pausasAtivas()
        true
    } catch (error: Throwable) {
        if (error.invalidStoredSession()) {
            clearActiveSession()
            false
        } else {
            true
        }
    }
}
