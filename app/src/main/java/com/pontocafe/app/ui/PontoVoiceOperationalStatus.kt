package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pontocafe.app.voice.PontoNeuralVoiceAvailability
import com.pontocafe.app.voice.PontoNeuralVoiceDiagnostics

@Composable
internal fun PontoVoiceOperationalStatusCard(
    diagnostics: PontoNeuralVoiceDiagnostics,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    val supporting = when (diagnostics.availability) {
        PontoNeuralVoiceAvailability.READY ->
            "Síntese neural pt-BR ativa e disponível offline neste aparelho."
        PontoNeuralVoiceAvailability.PREPARING -> if (diagnostics.modelInstalled) {
            "O modelo já está salvo. O motor neural está sendo carregado em segundo plano."
        } else {
            "O modelo pt-BR está sendo preparado. Até ficar pronto, o Android TTS continua como fallback."
        }
        PontoNeuralVoiceAvailability.FAILED -> buildString {
            append(diagnostics.lastFailureReason ?: "A voz neural ainda não ficou disponível.")
            if (diagnostics.retryAvailableInMillis > 0L) {
                append(" Nova tentativa automática em aproximadamente ")
                append((diagnostics.retryAvailableInMillis / 1_000L).coerceAtLeast(1L))
                append(" s.")
            }
        }
        PontoNeuralVoiceAvailability.IDLE ->
            "A voz será preparada automaticamente quando o modo Ponto precisar falar."
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
            if (diagnostics.availability == PontoNeuralVoiceAvailability.FAILED) {
                PcSecondaryButton(
                    text = "Tentar voz PontoCafe novamente",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.RecordVoiceOver,
                )
            }
        }
    }
}
