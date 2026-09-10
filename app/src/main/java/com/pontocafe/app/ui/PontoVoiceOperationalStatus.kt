package com.pontocafe.app.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pontocafe.app.voice.PontoNeuralSpeechDecision
import com.pontocafe.app.voice.PontoNeuralSpeechEvent
import com.pontocafe.app.voice.PontoNeuralVoiceAvailability
import com.pontocafe.app.voice.PontoNeuralVoiceDiagnostics
import com.pontocafe.app.voice.PontoNeuralVoiceRuntime
import com.pontocafe.app.voice.PontoSpeechBackend
import com.pontocafe.app.voice.PontoVoicePriority
import com.pontocafe.app.voice.PontoVoicePrompt

@Composable
internal fun PontoVoiceOperationalStatusCard(
    diagnostics: PontoNeuralVoiceDiagnostics,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testSucceeded by remember { mutableStateOf(false) }

    val title = when (diagnostics.availability) {
        PontoNeuralVoiceAvailability.READY -> "Voz PontoCafe pronta"
        PontoNeuralVoiceAvailability.PREPARING -> if (diagnostics.modelInstalled) {
            "Inicializando voz PontoCafe"
        } else {
            "Preparando voz PontoCafe"
        }
        PontoNeuralVoiceAvailability.FAILED -> "Usando voz do aparelho"
        PontoNeuralVoiceAvailability.IDLE -> "Voz PontoCafe aguardando inicialização"
    }
    val backendLabel = when (diagnostics.lastSpeechBackend) {
        PontoSpeechBackend.NEURAL_PONTOCAFE -> "Última fala: voz neural PontoCafe."
        PontoSpeechBackend.ANDROID_TTS_FALLBACK -> "Última fala: fallback de voz do Android."
        PontoSpeechBackend.NONE -> "Nenhuma fala concluída nesta sessão."
    }
    val supporting = when (diagnostics.availability) {
        PontoNeuralVoiceAvailability.READY ->
            "Síntese neural pt-BR ativa e disponível offline neste aparelho. $backendLabel"
        PontoNeuralVoiceAvailability.PREPARING -> if (diagnostics.modelInstalled) {
            "O modelo já está salvo. O motor neural está sendo carregado em segundo plano. $backendLabel"
        } else {
            "O modelo pt-BR está sendo preparado. Até ficar pronto, o Android TTS continua como fallback. $backendLabel"
        }
        PontoNeuralVoiceAvailability.FAILED -> buildString {
            append(diagnostics.lastFailureReason ?: "A voz neural ainda não ficou disponível.")
            append(' ')
            append(backendLabel)
            if (diagnostics.retryAvailableInMillis > 0L) {
                append(" Nova tentativa automática em aproximadamente ")
                append((diagnostics.retryAvailableInMillis / 1_000L).coerceAtLeast(1L))
                append(" s.")
            }
        }
        PontoNeuralVoiceAvailability.IDLE ->
            "A voz será preparada automaticamente quando o modo Ponto precisar falar. $backendLabel"
    }
    val tone = when (diagnostics.availability) {
        PontoNeuralVoiceAvailability.READY -> PontoCafeTone.SUCCESS
        PontoNeuralVoiceAvailability.PREPARING -> PontoCafeTone.INFO
        PontoNeuralVoiceAvailability.FAILED -> PontoCafeTone.WARNING
        PontoNeuralVoiceAvailability.IDLE -> PontoCafeTone.NEUTRAL
    }

    PcSectionSurface(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            PcHeroCard(
                title = title,
                supportingText = supporting,
                icon = Icons.Default.RecordVoiceOver,
                tone = tone,
            )

            if (diagnostics.availability == PontoNeuralVoiceAvailability.READY) {
                PcSecondaryButton(
                    text = "Testar voz PontoCafe",
                    onClick = {
                        testSucceeded = false
                        testMessage = "Iniciando teste exclusivamente com a voz neural…"
                        val prompt = PontoVoicePrompt(
                            key = "neural-self-test:${System.nanoTime()}",
                            text = "Voz Ponto Café ativada.",
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
                                            PontoNeuralSpeechEvent.Queued -> {
                                                testSucceeded = false
                                                testMessage = "Teste neural enfileirado. Aguardando síntese…"
                                            }
                                            PontoNeuralSpeechEvent.Synthesizing -> {
                                                testSucceeded = false
                                                testMessage = "Gerando o áudio com a voz neural PontoCafe…"
                                            }
                                            PontoNeuralSpeechEvent.SynthesisCompleted -> {
                                                testSucceeded = false
                                                testMessage = "Áudio neural gerado. Iniciando reprodução…"
                                            }
                                            PontoNeuralSpeechEvent.PlaybackStarted -> {
                                                testSucceeded = false
                                                testMessage = "Reprodução neural iniciada. Aguarde a conclusão do teste."
                                            }
                                            PontoNeuralSpeechEvent.PlaybackCompleted -> {
                                                testSucceeded = true
                                                testMessage = "Teste concluído pela voz neural PontoCafe: ‘Voz Ponto Café ativada.’"
                                            }
                                            is PontoNeuralSpeechEvent.Failed -> {
                                                testSucceeded = false
                                                val latest = PontoNeuralVoiceRuntime.diagnostics(appContext)
                                                testMessage = latest.lastFailureReason
                                                    ?: "O teste neural falhou (${event.stage.name}/${event.diagnosticCode})."
                                            }
                                        }
                                    }
                                },
                            )
                        }.getOrDefault(PontoNeuralSpeechDecision.UNAVAILABLE)

                        when (decision) {
                            PontoNeuralSpeechDecision.ACCEPTED -> {
                                // ACCEPTED means queued, never successful playback.
                                if (testMessage == null) {
                                    testMessage = "Teste neural enfileirado. Aguardando reprodução completa…"
                                }
                            }
                            PontoNeuralSpeechDecision.SUPPRESSED -> {
                                testSucceeded = false
                                testMessage = "O teste foi suprimido porque outra fala neural ainda está em reprodução. Tente novamente."
                            }
                            PontoNeuralSpeechDecision.UNAVAILABLE -> {
                                testSucceeded = false
                                testMessage = "O motor neural deixou de estar disponível. Use ‘Tentar voz PontoCafe novamente’."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.RecordVoiceOver,
                )
            }

            if (diagnostics.availability == PontoNeuralVoiceAvailability.FAILED) {
                PcSecondaryButton(
                    text = "Tentar voz PontoCafe novamente",
                    onClick = {
                        testMessage = null
                        onRetry()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.RecordVoiceOver,
                )
            }

            PcFeedbackBanner(
                message = testMessage,
                tone = if (testSucceeded) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                onDismiss = { testMessage = null },
            )
        }
    }
}
