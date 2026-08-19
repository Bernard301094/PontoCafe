package com.pontocafe.app.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var contentVisible by remember { mutableStateOf(false) }
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
        contentVisible = true
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && autoUnlockPending) {
            delay(100)
            requestUnlock()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(PontoCafePremium.glowSoft, Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )

        AnimatedVisibility(
            visible = contentVisible,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 22.dp),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 10 }),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = PontoCafePremium.glassStrong,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, PontoCafePremium.border),
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PontoCafePremium.glowSoft,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                    ) {
                        Box(
                            modifier = Modifier.size(76.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    ) {
                        Text(
                            text = "ÁREA PROTEGIDA",
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = "Ponto Café",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Acesso seguro ao painel administrativo",
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(24.dp))

                    Text(
                        text = profileLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )

                    Surface(
                        modifier = Modifier.padding(top = 9.dp),
                        shape = CircleShape,
                        color = LocalPontoCafeSemanticColors.current.successContainer,
                        border = BorderStroke(
                            1.dp,
                            LocalPontoCafeSemanticColors.current.success.copy(alpha = 0.16f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        LocalPontoCafeSemanticColors.current.success,
                                        CircleShape,
                                    ),
                            )
                            Text(
                                "Sessão protegida",
                                style = MaterialTheme.typography.labelMedium,
                                color = LocalPontoCafeSemanticColors.current.onSuccessContainer,
                            )
                        }
                    }

                    Text(
                        text = "Sua sessão continua ativa neste dispositivo. Confirme sua identidade para voltar exatamente de onde parou.",
                        modifier = Modifier.padding(top = 20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    erro?.let { message ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                        ) {
                            Text(
                                message,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Spacer(Modifier.height(26.dp))

                    Button(
                        onClick = ::forceRequestUnlock,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 17.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(21.dp),
                        )
                        Text(
                            text = if (promptRequested) "Abrir autenticação novamente" else "Desbloquear",
                            modifier = Modifier.padding(start = 9.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    OutlinedButton(
                        onClick = onBackToPonto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                    ) {
                        Text(
                            "Continuar no Ponto Café",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Text(
                        text = "Protegido localmente neste aparelho",
                        modifier = Modifier.padding(top = 18.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
