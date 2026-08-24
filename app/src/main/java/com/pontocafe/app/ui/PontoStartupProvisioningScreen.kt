package com.pontocafe.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pontocafe.app.voice.PontoNeuralSpeechDecision
import com.pontocafe.app.voice.PontoNeuralSpeechEvent
import com.pontocafe.app.voice.PontoNeuralVoiceAvailability
import com.pontocafe.app.voice.PontoNeuralVoiceDiagnostics
import com.pontocafe.app.voice.PontoNeuralVoiceRuntime
import com.pontocafe.app.voice.PontoVoicePriority
import com.pontocafe.app.voice.PontoVoicePrompt
import kotlinx.coroutines.delay

private const val NATURAL_VOICE_PREFS = "pontocafe_natural_voice_setup"
private const val NATURAL_VOICE_KEY = "verified_voice_version"
private const val NATURAL_VOICE_VERSION = "faber-medium-playback-v1"

internal fun isNaturalVoiceProvisioned(context: Context): Boolean {
    val appContext = context.applicationContext
    val installed = PontoNeuralVoiceRuntime.diagnostics(appContext).modelInstalled
    if (!installed) return false
    return appContext
        .getSharedPreferences(NATURAL_VOICE_PREFS, Context.MODE_PRIVATE)
        .getString(NATURAL_VOICE_KEY, null) == NATURAL_VOICE_VERSION
}

private fun markNaturalVoiceProvisioned(context: Context) {
    context.applicationContext
        .getSharedPreferences(NATURAL_VOICE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(NATURAL_VOICE_KEY, NATURAL_VOICE_VERSION)
        .apply()
}

@Composable
internal fun PontoDeviceAuthorizationScreen(
    checking: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onAdminClick: () -> Unit,
    onSupervisorClick: () -> Unit,
) {
    PontoCafeResponsivePage(maxContentWidth = 640.dp) { responsive ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = responsive.pagePadding, vertical = PontoCafeSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PontoCafeScreenHeader(
                title = if (checking) "Validando dispositivo" else "Não foi possível validar",
                eyebrow = "Ponto Café",
            )

            PcSectionSurface(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (checking) {
                        CircularProgressIndicator()
                    }
                    PcHeroCard(
                        title = if (checking) {
                            "Confirmando autorização deste aparelho"
                        } else {
                            "A autorização ainda não pôde ser confirmada"
                        },
                        supportingText = if (checking) {
                            "Aguarde alguns instantes. O código de ativação não será solicitado enquanto este aparelho já possuir uma credencial salva."
                        } else {
                            error ?: "Verifique a conexão e tente validar novamente."
                        },
                        icon = Icons.Default.Devices,
                        tone = if (checking) PontoCafeTone.INFO else PontoCafeTone.WARNING,
                    )

                    if (!checking) {
                        PcPrimaryButton(
                            text = "Tentar validar novamente",
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(PontoCafeSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                ) {
                    Text("Acesso de gestão", style = MaterialTheme.typography.titleMedium)
                    PcSecondaryButton(
                        text = "Entrar como Administrador",
                        onClick = onAdminClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PcSecondaryButton(
                        text = "Entrar como Supervisor",
                        onClick = onSupervisorClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PontoNaturalVoiceProvisioningScreen(
    onReady: () -> Unit,
    onContinueWithAndroidVoice: () -> Unit,
    onAdminClick: () -> Unit,
    onSupervisorClick: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var diagnostics by remember {
        mutableStateOf<PontoNeuralVoiceDiagnostics>(PontoNeuralVoiceRuntime.diagnostics(appContext))
    }
    var verificationStarted by remember { mutableStateOf(false) }
    var verificationFailed by remember { mutableStateOf(false) }
    var verificationMessage by remember { mutableStateOf<String?>(null) }
    var completed by remember { mutableStateOf(false) }

    fun startPlaybackVerification() {
        if (verificationStarted || completed) return
        verificationStarted = true
        verificationFailed = false
        verificationMessage = "Testando a voz natural neste aparelho…"

        val prompt = PontoVoicePrompt(
            key = "natural-voice-provision:${System.nanoTime()}",
            text = "Voz natural Ponto Café instalada e pronta para uso.",
            priority = PontoVoicePriority.RESULT,
            cooldownMillis = 0L,
            interrupt = true,
        )

        val decision = runCatching {
            PontoNeuralVoiceRuntime.speak(
                context = appContext,
                prompt = prompt,
                sessionKey = null,
                onFailure = null,
                onEvent = { event ->
                    mainHandler.post {
                        when (event) {
                            PontoNeuralSpeechEvent.Queued ->
                                verificationMessage = "Gerando o teste com a voz natural…"
                            PontoNeuralSpeechEvent.Synthesizing ->
                                verificationMessage = "Sintetizando a voz natural localmente…"
                            PontoNeuralSpeechEvent.SynthesisCompleted ->
                                verificationMessage = "Áudio gerado. Iniciando reprodução…"
                            PontoNeuralSpeechEvent.PlaybackStarted ->
                                verificationMessage = "Reproduzindo a voz natural…"
                            PontoNeuralSpeechEvent.PlaybackCompleted -> {
                                markNaturalVoiceProvisioned(appContext)
                                completed = true
                                verificationFailed = false
                                verificationMessage = "Voz natural instalada, testada e ativada."
                                mainHandler.postDelayed(onReady, 650L)
                            }
                            is PontoNeuralSpeechEvent.Failed -> {
                                verificationStarted = false
                                verificationFailed = true
                                val latest = PontoNeuralVoiceRuntime.diagnostics(appContext)
                                diagnostics = latest
                                verificationMessage = latest.lastFailureReason
                                    ?: "A voz natural foi instalada, mas o teste de áudio falhou (${event.stage.name}/${event.diagnosticCode})."
                            }
                        }
                    }
                },
            )
        }.getOrDefault(PontoNeuralSpeechDecision.UNAVAILABLE)

        if (decision != PontoNeuralSpeechDecision.ACCEPTED) {
            verificationStarted = false
            verificationFailed = true
            verificationMessage = when (decision) {
                PontoNeuralSpeechDecision.SUPPRESSED ->
                    "Outra fala ainda está em execução. Tente o teste novamente."
                PontoNeuralSpeechDecision.UNAVAILABLE ->
                    "O motor da voz natural ainda não está disponível. Tente novamente."
                PontoNeuralSpeechDecision.ACCEPTED -> null
            }
        }
    }

    LaunchedEffect(Unit) {
        if (isNaturalVoiceProvisioned(appContext)) {
            completed = true
            onReady()
            return@LaunchedEffect
        }

        // A entrada neste passo inicia automaticamente download, validação,
        // instalação e inicialização. Nada é baixado silenciosamente antes daqui.
        PontoNeuralVoiceRuntime.prewarm(appContext)
        while (!completed) {
            diagnostics = PontoNeuralVoiceRuntime.diagnostics(appContext)
            delay(500L)
        }
    }

    LaunchedEffect(diagnostics.availability, diagnostics.modelInstalled, verificationFailed) {
        if (
            diagnostics.availability == PontoNeuralVoiceAvailability.READY &&
            diagnostics.modelInstalled &&
            !verificationStarted &&
            !verificationFailed &&
            !completed
        ) {
            startPlaybackVerification()
        }
    }

    val title = when {
        completed -> "Voz natural ativada"
        diagnostics.availability == PontoNeuralVoiceAvailability.FAILED -> "Não foi possível instalar a voz natural"
        diagnostics.availability == PontoNeuralVoiceAvailability.READY && verificationStarted -> "Testando voz natural"
        diagnostics.modelInstalled -> "Inicializando voz natural"
        else -> "Baixando voz natural PontoCafe"
    }
    val supporting = when {
        completed -> "A voz neural pt-BR foi reproduzida com sucesso e será usada automaticamente no Ponto."
        diagnostics.availability == PontoNeuralVoiceAvailability.FAILED ->
            diagnostics.lastFailureReason ?: "A preparação da voz natural falhou neste aparelho."
        diagnostics.availability == PontoNeuralVoiceAvailability.READY ->
            verificationMessage ?: "O modelo foi instalado. Estamos validando a reprodução antes de liberar o Ponto."
        diagnostics.modelInstalled ->
            "O modelo já está salvo no aparelho. Inicializando o motor neural para uso offline."
        else ->
            "O modelo pt-BR será baixado, validado e instalado automaticamente. Depois, a própria app fará um teste real de reprodução."
    }

    PontoCafeResponsivePage(maxContentWidth = 640.dp) { responsive ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = responsive.pagePadding, vertical = PontoCafeSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
        ) {
            PontoCafeScreenHeader(title = "Configurar voz do Ponto", eyebrow = "Instalação do aparelho")

            PcSectionSurface(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!completed && diagnostics.availability != PontoNeuralVoiceAvailability.FAILED) {
                        CircularProgressIndicator()
                    }
                    PcHeroCard(
                        title = title,
                        supportingText = supporting,
                        icon = if (diagnostics.modelInstalled) Icons.Default.RecordVoiceOver else Icons.Default.CloudDownload,
                        tone = when {
                            completed -> PontoCafeTone.SUCCESS
                            diagnostics.availability == PontoNeuralVoiceAvailability.FAILED || verificationFailed -> PontoCafeTone.WARNING
                            else -> PontoCafeTone.INFO
                        },
                    )

                    PcFeedbackBanner(
                        message = verificationMessage,
                        tone = if (completed) PontoCafeTone.SUCCESS else PontoCafeTone.INFO,
                        onDismiss = { if (!completed) verificationMessage = null },
                    )

                    if (diagnostics.availability == PontoNeuralVoiceAvailability.FAILED) {
                        PcPrimaryButton(
                            text = "Tentar baixar e instalar novamente",
                            onClick = {
                                verificationStarted = false
                                verificationFailed = false
                                verificationMessage = "Reiniciando instalação da voz natural…"
                                PontoNeuralVoiceRuntime.retryNow(appContext)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.CloudDownload,
                        )
                    } else if (diagnostics.availability == PontoNeuralVoiceAvailability.READY && verificationFailed) {
                        PcPrimaryButton(
                            text = "Testar voz natural novamente",
                            onClick = {
                                verificationFailed = false
                                startPlaybackVerification()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.RecordVoiceOver,
                        )
                    }

                    if (diagnostics.availability == PontoNeuralVoiceAvailability.FAILED || verificationFailed) {
                        PcSecondaryButton(
                            text = "Usar Ponto temporariamente com voz do Android",
                            onClick = onContinueWithAndroidVoice,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(PontoCafeSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                ) {
                    Text("Acesso de gestão", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A instalação da voz não bloqueia o acesso de Administrador ou Supervisor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PcSecondaryButton(
                        text = "Entrar como Administrador",
                        onClick = onAdminClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PcSecondaryButton(
                        text = "Entrar como Supervisor",
                        onClick = onSupervisorClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
