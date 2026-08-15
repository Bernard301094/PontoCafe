package com.pontocafe.app.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RestrictedAreaLockScreen(
    activity: FragmentActivity,
    profileLabel: String,
    onUnlocked: () -> Unit,
    onBackToPonto: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var erro by remember { mutableStateOf<String?>(null) }
    var promptRequested by remember { mutableStateOf(false) }
    var autoUnlockPending by remember { mutableStateOf(true) }
    val latestUnlocked by rememberUpdatedState(onUnlocked)

    val executor = remember(activity) { ContextCompat.getMainExecutor(activity) }
    val biometricPrompt = remember(activity) {
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    promptRequested = false
                    autoUnlockPending = false
                    erro = null
                    latestUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    promptRequested = false

                    when (errorCode) {
                        BiometricPrompt.ERROR_CANCELED -> Unit
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> autoUnlockPending = false
                        else -> {
                            autoUnlockPending = false
                            erro = errString.toString()
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    erro = "Não foi possível confirmar sua identidade. Tente novamente."
                }
            },
        )
    }

    fun requestUnlock() {
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // A tela de bloqueio pode ser composta ainda durante ON_START.
            // Nesse momento o BiometricPrompt pode aceitar authenticate() sem
            // chegar a exibir nada, deixando o botão aparentemente travado.
            autoUnlockPending = true
            promptRequested = false
            return
        }
        if (promptRequested) return

        promptRequested = true
        autoUnlockPending = false
        erro = null

        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear Ponto Café")
            .setSubtitle("Confirme sua identidade para continuar em $profileLabel")
            .setConfirmationRequired(false)

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            promptBuilder.setDeviceCredentialAllowed(true)
        }

        runCatching { biometricPrompt.authenticate(promptBuilder.build()) }
            .onFailure {
                promptRequested = false
                autoUnlockPending = false
                erro = "Configure uma impressão digital, PIN, padrão ou senha de bloqueio no celular para proteger esta área."
            }
    }

    fun forceRequestUnlock() {
        scope.launch {
            // Recupera inclusive um prompt que tenha ficado pendente no
            // FragmentManager após minimizar/restaurar o aplicativo.
            if (promptRequested) {
                biometricPrompt.cancelAuthentication()
                promptRequested = false
                delay(120)
            }
            autoUnlockPending = true
            requestUnlock()
        }
    }

    DisposableEffect(lifecycleOwner, biometricPrompt) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (autoUnlockPending && !promptRequested) requestUnlock()
                }

                Lifecycle.Event.ON_STOP -> {
                    // Nunca preserve um "prompt em andamento" quando a Activity
                    // já não está visível. Ao voltar, um prompt novo será criado.
                    autoUnlockPending = true
                    if (promptRequested) biometricPrompt.cancelAuthentication()
                    promptRequested = false
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            biometricPrompt.cancelAuthentication()
        }
    }

    LaunchedEffect(lifecycleOwner) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && autoUnlockPending) {
            // Deixa a Activity concluir a restauração antes de abrir a UI do sistema.
            delay(100)
            requestUnlock()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PontoCafeHeader("Área protegida")
                Text(
                    profileLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Sua sessão continua aberta. Use a impressão digital ou o bloqueio do próprio celular para voltar exatamente de onde parou.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                erro?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Button(onClick = ::forceRequestUnlock, modifier = Modifier.fillMaxWidth()) {
                    Text(if (promptRequested) "Abrir autenticação novamente" else "Desbloquear com biometria ou PIN")
                }
                OutlinedButton(onClick = onBackToPonto, modifier = Modifier.fillMaxWidth()) {
                    Text("Voltar ao Ponto Café")
                }
            }
        }
    }
}
