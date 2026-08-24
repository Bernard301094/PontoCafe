package com.pontocafe.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainNavigationPolicyTest {

    // --- determinePontoScreenRoute -----------------------------------------

    @Test
    fun `dispositivo autorizado online ou offline vai direto para o Ponto`() {
        assertEquals(
            PontoScreenRoute.READY,
            determinePontoScreenRoute(
                deviceAuthorizationState = DeviceAuthorizationState.AUTHORIZED_ONLINE,
                deviceConfigured = true,
                naturalVoiceReadyForSession = true,
            ),
        )
        assertEquals(
            PontoScreenRoute.READY,
            determinePontoScreenRoute(
                deviceAuthorizationState = DeviceAuthorizationState.AUTHORIZED_OFFLINE,
                deviceConfigured = true,
                naturalVoiceReadyForSession = true,
            ),
        )
    }

    @Test
    fun `dispositivo sem token ou revogado mostra a tela de ativacao`() {
        assertEquals(
            PontoScreenRoute.NEEDS_TOKEN_SETUP,
            determinePontoScreenRoute(
                deviceAuthorizationState = DeviceAuthorizationState.NO_TOKEN,
                deviceConfigured = false,
                naturalVoiceReadyForSession = true,
            ),
        )
        assertEquals(
            PontoScreenRoute.NEEDS_TOKEN_SETUP,
            determinePontoScreenRoute(
                deviceAuthorizationState = DeviceAuthorizationState.REVOKED,
                deviceConfigured = false,
                naturalVoiceReadyForSession = true,
            ),
        )
    }

    @Test
    fun `CHECKING nunca pisca a tela de ativacao de token`() {
        for (deviceConfigured in listOf(false, true)) {
            for (voiceReady in listOf(false, true)) {
                assertEquals(
                    "deviceConfigured=$deviceConfigured voiceReady=$voiceReady",
                    PontoScreenRoute.CHECKING_DEVICE,
                    determinePontoScreenRoute(
                        deviceAuthorizationState = DeviceAuthorizationState.CHECKING,
                        deviceConfigured = deviceConfigured,
                        naturalVoiceReadyForSession = voiceReady,
                    ),
                )
            }
        }
    }

    @Test
    fun `falha temporaria de validacao mostra tela de erro, nunca a de token`() {
        for (deviceConfigured in listOf(false, true)) {
            for (voiceReady in listOf(false, true)) {
                assertEquals(
                    "deviceConfigured=$deviceConfigured voiceReady=$voiceReady",
                    PontoScreenRoute.DEVICE_CHECK_FAILED,
                    determinePontoScreenRoute(
                        deviceAuthorizationState = DeviceAuthorizationState.TEMPORARY_FAILURE,
                        deviceConfigured = deviceConfigured,
                        naturalVoiceReadyForSession = voiceReady,
                    ),
                )
            }
        }
    }

    @Test
    fun `voz natural pendente so aparece apos o dispositivo estar configurado`() {
        assertEquals(
            PontoScreenRoute.NEEDS_VOICE_SETUP,
            determinePontoScreenRoute(
                deviceAuthorizationState = DeviceAuthorizationState.AUTHORIZED_ONLINE,
                deviceConfigured = true,
                naturalVoiceReadyForSession = false,
            ),
        )
    }

    @Test
    fun `varredura exaustiva - tela de token so aparece quando genuinamente nao autorizado`() {
        for (authState in DeviceAuthorizationState.entries) {
            for (deviceConfigured in listOf(false, true)) {
                for (voiceReady in listOf(false, true)) {
                    val route = determinePontoScreenRoute(authState, deviceConfigured, voiceReady)
                    if (route == PontoScreenRoute.NEEDS_TOKEN_SETUP) {
                        val checking = authState == DeviceAuthorizationState.CHECKING
                        val temporaryFailure = authState == DeviceAuthorizationState.TEMPORARY_FAILURE
                        assertEquals(
                            "NEEDS_TOKEN_SETUP só é válido quando não está CHECKING/TEMPORARY_FAILURE " +
                                "e deviceConfigured é false (authState=$authState, deviceConfigured=$deviceConfigured)",
                            false,
                            checking || temporaryFailure || deviceConfigured,
                        )
                    }
                }
            }
        }
    }

    // --- resolveInitialArea --------------------------------------------------

    @Test
    fun `area restrita sem sessao valida nunca e retomada apos o app reiniciar`() {
        assertNull(resolveInitialArea(AreaRestrita.ADMIN, protectedSessionAtLaunch = false))
        assertNull(resolveInitialArea(AreaRestrita.SUPERVISOR, protectedSessionAtLaunch = false))
    }

    @Test
    fun `seletor de login nunca e retomado sozinho apos o app reiniciar`() {
        assertNull(resolveInitialArea(AreaRestrita.LOGIN, protectedSessionAtLaunch = true))
        assertNull(resolveInitialArea(AreaRestrita.LOGIN, protectedSessionAtLaunch = false))
    }

    @Test
    fun `nenhuma area salva permanece nula`() {
        assertNull(resolveInitialArea(null, protectedSessionAtLaunch = false))
        assertNull(resolveInitialArea(null, protectedSessionAtLaunch = true))
    }

    @Test
    fun `area com sessao ativa genuina e retomada corretamente apos recriacao do processo`() {
        assertEquals(AreaRestrita.ADMIN, resolveInitialArea(AreaRestrita.ADMIN, protectedSessionAtLaunch = true))
        assertEquals(AreaRestrita.SUPERVISOR, resolveInitialArea(AreaRestrita.SUPERVISOR, protectedSessionAtLaunch = true))
    }
}
