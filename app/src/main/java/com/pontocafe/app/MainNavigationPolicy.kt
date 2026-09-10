package com.pontocafe.app

/**
 * Pure top-level navigation/auth-gate decisions, kept outside MainActivity so
 * they are unit-testable without Compose/Robolectric. Mirrors the exact
 * branch logic previously inlined in MainActivity's `when` blocks.
 */

/**
 * A persisted top-level area with no matching, still-valid session is stale:
 * the process died (OOM kill, task-swipe, crash, kiosk reboot) while
 * Admin/Supervisor/the account-selector chooser was open, without the user
 * ever tapping "voltar ao Ponto". Resuming into it on a cold start would
 * skip the entire device-authorization/voice-provisioning gate, so it must
 * never be trusted without a real session behind it. LOGIN has no session
 * concept at all and never resumes unattended.
 */
internal fun resolveInitialArea(
    savedArea: AreaRestrita?,
    protectedSessionAtLaunch: Boolean,
): AreaRestrita? = when (savedArea) {
    AreaRestrita.ADMIN, AreaRestrita.SUPERVISOR -> savedArea.takeIf { protectedSessionAtLaunch }
    AreaRestrita.LOGIN -> null
    null -> null
}

internal enum class PontoScreenRoute {
    CHECKING_DEVICE,
    DEVICE_CHECK_FAILED,
    NEEDS_TOKEN_SETUP,
    NEEDS_VOICE_SETUP,
    READY,
}

/** Mirrors MainActivity's Ponto-flow `when` branch order exactly. */
internal fun determinePontoScreenRoute(
    deviceAuthorizationState: DeviceAuthorizationState,
    deviceConfigured: Boolean,
    naturalVoiceReadyForSession: Boolean,
): PontoScreenRoute = when {
    deviceAuthorizationState == DeviceAuthorizationState.CHECKING -> PontoScreenRoute.CHECKING_DEVICE
    deviceAuthorizationState == DeviceAuthorizationState.TEMPORARY_FAILURE -> PontoScreenRoute.DEVICE_CHECK_FAILED
    !deviceConfigured -> PontoScreenRoute.NEEDS_TOKEN_SETUP
    !naturalVoiceReadyForSession -> PontoScreenRoute.NEEDS_VOICE_SETUP
    else -> PontoScreenRoute.READY
}
