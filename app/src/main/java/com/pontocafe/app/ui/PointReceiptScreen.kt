package com.pontocafe.app.ui

import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel
import com.pontocafe.app.TipoComprovantePonto
import com.pontocafe.app.avatar.PontoAvatarRuntime
import kotlinx.coroutines.delay
import kotlin.math.ceil

private const val RECEIPT_NORMAL_MILLIS = 2_400L
private const val RECEIPT_ATTENTION_MILLIS = 5_000L
private const val RECEIPT_TICK_MILLIS = 100L

@Composable
fun PointReceiptScreen(viewModel: PontoCafeViewModel) {
    val comprovante = viewModel.state.comprovante ?: return
    val start = comprovante.tipo == TipoComprovantePonto.INICIO
    val withinLimit = !comprovante.excedeuLimite
    val needsAttention = comprovante.pendenteSincronizacao || comprovante.excedeuLimite || comprovante.foraHorario
    val totalVisibleMillis = if (needsAttention) RECEIPT_ATTENTION_MILLIS else RECEIPT_NORMAL_MILLIS
    val view = LocalView.current
    val avatarUrl = PontoAvatarRuntime.lastRecognizedAvatarUrl
    var remainingMillis by remember(comprovante) { mutableLongStateOf(totalVisibleMillis) }

    fun conclude() {
        PontoAvatarRuntime.clear()
        viewModel.concluirComprovante()
    }

    LaunchedEffect(comprovante, totalVisibleMillis) {
        view.performHapticFeedback(
            if (withinLimit) HapticFeedbackConstantsCompat.CONFIRM else HapticFeedbackConstantsCompat.REJECT,
        )
        var elapsed = 0L
        while (elapsed < totalVisibleMillis) {
            val step = minOf(RECEIPT_TICK_MILLIS, totalVisibleMillis - elapsed)
            delay(step)
            elapsed += step
            remainingMillis = (totalVisibleMillis - elapsed).coerceAtLeast(0L)
        }
        conclude()
    }

    val secondsLeft = ceil(remainingMillis / 1_000.0).toInt().coerceAtLeast(0)
    val progress = if (totalVisibleMillis <= 0L) 0f else {
        (remainingMillis.toFloat() / totalVisibleMillis.toFloat()).coerceIn(0f, 1f)
    }

    PontoCafeResponsivePage(maxContentWidth = 620.dp) { responsive ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = responsive.pagePadding, vertical = PontoCafeSpacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // O avatar é exclusivamente visual. A identidade já foi confirmada
            // pelo FaceNet antes deste comprovante aparecer.
            CollaboratorAvatar(
                name = comprovante.nome,
                avatarUrl = avatarUrl,
                avatarSize = 92.dp,
            )
            PontoCafeSuccessAnimation(
                modifier = Modifier
                    .padding(top = PontoCafeSpacing.sm)
                    .size(58.dp),
            )

            Text(
                text = if (start) "Pausa iniciada" else "Retorno registrado",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = comprovante.nome,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = PontoCafeSpacing.xs),
            )

            StatusPill(
                text = when {
                    comprovante.pendenteSincronizacao -> "Salvo offline"
                    !start && comprovante.excedeuLimite -> "Registro confirmado · limite excedido"
                    comprovante.foraHorario -> "Registro autorizado"
                    else -> "Registro confirmado"
                },
                tone = when {
                    comprovante.pendenteSincronizacao -> PontoCafeTone.INFO
                    !start && comprovante.excedeuLimite -> PontoCafeTone.WARNING
                    comprovante.foraHorario -> PontoCafeTone.INFO
                    else -> PontoCafeTone.SUCCESS
                },
                modifier = Modifier.padding(top = PontoCafeSpacing.md),
            )

            PcSectionSurface(modifier = Modifier.padding(top = PontoCafeSpacing.xl)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
                    ) {
                        ReceiptValue(
                            label = if (start) "Registrado às" else "Retorno às",
                            value = comprovante.horarioRegistrado,
                            modifier = Modifier.weight(1f),
                        )
                        ReceiptValue(
                            label = if (start) "Volte até" else "Tempo utilizado",
                            value = if (start) comprovante.retornoAte ?: "--:--"
                            else viewModel.formatarTempo(comprovante.duracaoSegundos ?: 0),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (start) {
                        Text(
                            "Você tem ${viewModel.formatarTempo(comprovante.limiteSegundos)} para retornar.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (comprovante.foraHorario) {
                            PcStateBanner(
                                title = "Pausa autorizada fora do horário",
                                supportingText = "A autorização foi validada e o registro foi aceito.",
                                tone = PontoCafeTone.INFO,
                            )
                        }
                    } else {
                        val duration = comprovante.duracaoSegundos ?: 0
                        val excess = (duration - comprovante.limiteSegundos).coerceAtLeast(0)
                        PcStateBanner(
                            title = if (withinLimit) "Retorno dentro do limite" else "Retorno acima do limite",
                            supportingText = if (withinLimit) {
                                "Tempo permitido: ${viewModel.formatarTempo(comprovante.limiteSegundos)}."
                            } else {
                                "Excesso de ${viewModel.formatarTempo(excess)}. O retorno foi registrado normalmente."
                            },
                            tone = if (withinLimit) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                        )
                    }
                }
            }

            if (comprovante.pendenteSincronizacao) {
                PcStateBanner(
                    title = "Registro protegido neste aparelho",
                    supportingText = "Será sincronizado automaticamente quando a conexão voltar.",
                    tone = PontoCafeTone.INFO,
                    modifier = Modifier.padding(top = PontoCafeSpacing.md),
                )
            }

            Text(
                if (needsAttention) {
                    "Voltando para a câmera em ${secondsLeft}s · leia o aviso acima"
                } else {
                    "Registro concluído · voltando para a câmera"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = PontoCafeSpacing.xl),
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = PontoCafeSpacing.xs),
            )

            PcPrimaryButton(
                text = "Concluir agora",
                onClick = ::conclude,
                modifier = Modifier.fillMaxWidth().padding(top = PontoCafeSpacing.md),
            )
        }
    }
}

@Composable
private fun ReceiptValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
