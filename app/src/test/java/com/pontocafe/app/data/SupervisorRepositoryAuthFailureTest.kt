package com.pontocafe.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SupervisorRepositoryAuthFailureTest {
    @Test
    fun `401 means saved session ended and asks for password again`() {
        val error = httpError(401)

        assertTrue(SupervisorRepository.isSessionExpired(error))
        assertTrue(SupervisorRepository.isAuthFailure(error))
        assertTrue(
            SupervisorRepository.sessionRecoveryMessage(error)
                .contains("Digite sua senha novamente"),
        )
    }

    @Test
    fun `403 means supervisor access is no longer allowed`() {
        val error = httpError(403)

        assertTrue(SupervisorRepository.isAccessDenied(error))
        assertTrue(SupervisorRepository.isAuthFailure(error))
        assertTrue(
            SupervisorRepository.sessionRecoveryMessage(error)
                .contains("não possui mais acesso de Supervisor"),
        )
    }

    @Test
    fun `server failure is not treated as authentication expiry`() {
        val error = httpError(500)

        assertFalse(SupervisorRepository.isSessionExpired(error))
        assertFalse(SupervisorRepository.isAccessDenied(error))
        assertFalse(SupervisorRepository.isAuthFailure(error))
    }

    private fun httpError(code: Int): HttpException {
        val body = "{}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, body))
    }
}
