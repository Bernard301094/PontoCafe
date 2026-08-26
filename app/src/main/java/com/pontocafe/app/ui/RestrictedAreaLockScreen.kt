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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        val compactHeight = maxHeight < 600.dp
        val compactContent = maxWidth < 420.dp || LocalDensity.current.fontScale >= 1.3f
        val horizontalPadding = if (maxWidth < 360.dp) 14.dp else 20.dp
        val cardPadding = if (compactHeight) 20.dp else 24.dp
        val iconBoxSize = if (compactHeight) 50.dp else 58.dp
        val iconSize = if (compactHeight) 23.dp else 26.dp

        Box(
            modifier = Modifier
                .size(if (compactHeight) 220.dp else 300.dp)
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
                .padding(horizontal = horizontalPadding),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 12 }),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = if (compactHeight) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge,
                color = PontoCafePremium.glassStrong,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, PontoCafePremium.border),
                shadowElevation = if (compactHeight) 10.dp else 16.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (maxHeight - 32.dp).coerceAtLeast(240.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(cardPadding),
                    verticalArrangement = Arrangement.spacedBy(if (compactHeight) 14.dp else 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(iconBoxSize),
                            shape = MaterialTheme.shapes.medium,
                            color = PontoCafePremium.glowSoft,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(iconSize),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = "ÁREA PROTEGIDA",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Ponto Café",
                                modifier = Modifier.semantics { heading() },
                                style = if (compactHeight) {
                                    MaterialTheme.typography.titleLarge
                                } else {
                                    MaterialTheme.typography.headlineSmall
                                },
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Acesso administrativo seguro",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = CircleShape,
                                color = LocalPontoCafeSemanticColors.current.successContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = LocalPontoCafeSemanticColors.current.onSuccessContainer,
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = profileLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Sessão ativa neste dispositivo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!compactContent) Surface(
                                shape = CircleShape,
                                color = LocalPontoCafeSemanticColors.current.successContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                LocalPontoCafeSemanticColors.current.success,
                                                CircleShape,
                                            ),
                                    )
                                    Text(
                                        "Protegida",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalPontoCafeSemanticColors.current.onSuccessContainer,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Desbloqueie para voltar ao painel exatamente de onde parou.",
                        style = if (compactHeight) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    erro?.let { message ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    liveRegion = LiveRegionMode.Assertive
                                    stateDescription = message
                                },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                            ),
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }

                    Button(
                        onClick = ::forceRequestUnlock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                stateDescription = if (promptRequested) {
                                    "Autenticação do sistema aberta"
                                } else {
                                    "Aguardando desbloqueio"
                                }
                            },
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(vertical = if (compactHeight) 14.dp else 16.dp),
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
                            text = if (promptRequested) "Abrir autenticação novamente" else "Desbloquear agora",
                            modifier = Modifier.padding(start = 9.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    OutlinedButton(
                        onClick = onBackToPonto,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(vertical = if (compactHeight) 13.dp else 15.dp),
                        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Voltar ao Ponto Café",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }

                    if (!compactHeight) {
                        Spacer(Modifier.height(1.dp))
                    }
                    Text(
                        text = "Proteção local do aparelho · biometria ou credencial do sistema",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
