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
    fun `generic 403 remains conservative account recovery failure`() {
        val error = httpError(403)

        assertTrue(SupervisorRepository.isAccessDenied(error))
        assertTrue(SupervisorRepository.isAuthFailure(error))
        assertTrue(
            SupervisorRepository.sessionRecoveryMessage(error)
                .contains("Não foi possível validar o acesso de Supervisor"),
        )
    }

    @Test
    fun `feature role denial does not invalidate supervisor session`() {
        val error = httpError(403, "{\"codigo\":\"AUTH_ROLE_DENIED\"}")

        assertFalse(SupervisorRepository.isAccessDenied(error))
        assertFalse(SupervisorRepository.isAuthFailure(error))
    }

    @Test
    fun `mandatory initial password change does not invalidate supervisor session`() {
        SupervisorPasswordChangeRuntime.clear()
        val error = httpError(403, "{\"codigo\":\"PASSWORD_CHANGE_REQUIRED\"}")

        assertFalse(SupervisorRepository.isAccessDenied(error))
        assertFalse(SupervisorRepository.isAuthFailure(error))
        assertTrue(SupervisorPasswordChangeRuntime.required)
        SupervisorPasswordChangeRuntime.clear()
    }

    @Test
    fun `disabled supervisor account is an account level authorization failure`() {
        val error = httpError(403, "{\"codigo\":\"AUTH_ACCOUNT_DISABLED\"}")

        assertTrue(SupervisorRepository.isAccessDenied(error))
        assertTrue(SupervisorRepository.isAuthFailure(error))
        assertTrue(
            SupervisorRepository.sessionRecoveryMessage(error)
                .contains("conta de Supervisor está desativada"),
        )
    }

    @Test
    fun `server failure is not treated as authentication expiry`() {
        val error = httpError(500)

        assertFalse(SupervisorRepository.isSessionExpired(error))
        assertFalse(SupervisorRepository.isAccessDenied(error))
        assertFalse(SupervisorRepository.isAuthFailure(error))
    }

    private fun httpError(code: Int, json: String = "{}"): HttpException {
        val body = json.toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, body))
    }
}
