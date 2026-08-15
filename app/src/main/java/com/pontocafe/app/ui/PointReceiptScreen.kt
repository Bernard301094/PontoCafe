package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel
import com.pontocafe.app.TipoComprovantePonto
import kotlinx.coroutines.delay

@Composable
fun PointReceiptScreen(viewModel: PontoCafeViewModel) {
    val comprovante = viewModel.state.comprovante ?: return
    val start = comprovante.tipo == TipoComprovantePonto.INICIO
    val success = !comprovante.excedeuLimite

    LaunchedEffect(comprovante) {
        delay(5_000)
        viewModel.concluirComprovante()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(PontoCafeSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PontoCafeSuccessAnimation(Modifier.size(104.dp))

        Text(
            text = if (start) "Pausa iniciada" else "Retorno registrado",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = comprovante.nome,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = PontoCafeSpacing.xs),
        )

        StatusPill(
            text = when {
                comprovante.pendenteSincronizacao -> "Salvo offline"
                !start && comprovante.excedeuLimite -> "Limite excedido"
                else -> "Registro confirmado"
            },
            tone = when {
                comprovante.pendenteSincronizacao -> PontoCafeTone.INFO
                !start && comprovante.excedeuLimite -> PontoCafeTone.WARNING
                else -> PontoCafeTone.SUCCESS
            },
            modifier = Modifier.padding(top = PontoCafeSpacing.md),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PontoCafeSpacing.xl),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(PontoCafeSpacing.lg),
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
                        value = if (start) {
                            comprovante.retornoAte ?: "--:--"
                        } else {
                            viewModel.formatarTempo(comprovante.duracaoSegundos ?: 0)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (start) {
                    Text(
                        "Você tem ${viewModel.formatarTempo(comprovante.limiteSegundos)} para retornar.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (comprovante.foraHorario) {
                        Text(
                            "Pausa iniciada com autorização fora do horário.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val duration = comprovante.duracaoSegundos ?: 0
                    val excess = (duration - comprovante.limiteSegundos).coerceAtLeast(0)
                    Text(
                        if (success) {
                            "Retorno dentro do limite de ${viewModel.formatarTempo(comprovante.limiteSegundos)}."
                        } else {
                            "${viewModel.formatarTempo(excess)} acima do limite permitido."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (success) {
                            LocalPontoCafeSemanticColors.current.success
                        } else {
                            LocalPontoCafeSemanticColors.current.warning
                        },
                    )
                }
            }
        }

        if (comprovante.pendenteSincronizacao) {
            Text(
                "O registro está protegido neste aparelho e será sincronizado automaticamente quando a conexão voltar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = PontoCafeSpacing.md),
            )
        }

        Text(
            "Voltando automaticamente para a câmera...",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PontoCafeSpacing.xl),
        )

        Button(
            onClick = viewModel::concluirComprovante,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PontoCafeSpacing.md),
        ) {
            Text("Concluir")
        }
    }
}

@Composable
private fun ReceiptValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
