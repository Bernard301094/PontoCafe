package com.pontocafe.app.ui

import android.view.HapticFeedbackConstants
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
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay

private const val RECEIPT_VISIBLE_SECONDS = 5

@Composable
fun PointReceiptScreen(viewModel: PontoCafeViewModel) {
    val comprovante = viewModel.state.comprovante ?: return
    val start = comprovante.tipo == TipoComprovantePonto.INICIO
    val withinLimit = !comprovante.excedeuLimite
    val view = LocalView.current
    var secondsLeft by remember(comprovante) { mutableIntStateOf(RECEIPT_VISIBLE_SECONDS) }

    LaunchedEffect(comprovante) {
        view.performHapticFeedback(
            if (withinLimit) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT,
        )
        repeat(RECEIPT_VISIBLE_SECONDS) {
            delay(1_000)
            secondsLeft = (secondsLeft - 1).coerceAtLeast(0)
        }
        viewModel.concluirComprovante()
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
            PontoCafeSuccessAnimation(Modifier.size(96.dp))

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
                    else -> "Registro confirmado"
                },
                tone = when {
                    comprovante.pendenteSincronizacao -> PontoCafeTone.INFO
                    !start && comprovante.excedeuLimite -> PontoCafeTone.WARNING
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
                "Voltando para a câmera em ${secondsLeft}s",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = PontoCafeSpacing.xl),
            )
            LinearProgressIndicator(
                progress = { secondsLeft.toFloat() / RECEIPT_VISIBLE_SECONDS.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = PontoCafeSpacing.xs),
            )

            PcPrimaryButton(
                text = "Concluir agora",
                onClick = viewModel::concluirComprovante,
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
