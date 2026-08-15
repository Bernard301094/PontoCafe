package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel
import com.pontocafe.app.TipoComprovantePonto
import kotlinx.coroutines.delay

@Composable
fun PointReceiptScreen(viewModel: PontoCafeViewModel) {
    val comprovante = viewModel.state.comprovante ?: return

    LaunchedEffect(comprovante) {
        delay(5_000)
        viewModel.concluirComprovante()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "✓",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = if (comprovante.tipo == TipoComprovantePonto.INICIO) "Pausa iniciada" else "Pausa finalizada",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = comprovante.nome,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (comprovante.pendenteSincronizacao) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Salvo com segurança neste aparelho",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        "Sem conexão com o servidor. O registro será enviado automaticamente quando a internet voltar.",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (comprovante.tipo == TipoComprovantePonto.INICIO) "Ponto registrado às" else "Retorno registrado às",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        comprovante.horarioRegistrado,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (comprovante.tipo == TipoComprovantePonto.INICIO) {
                        Text("Volte até", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            comprovante.retornoAte ?: "--:--",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text("Tempo utilizado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            viewModel.formatarTempo(comprovante.duracaoSegundos ?: 0),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (comprovante.excedeuLimite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (comprovante.tipo == TipoComprovantePonto.INICIO) {
                    Text(
                        viewModel.formatarTempo(comprovante.limiteSegundos),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Tempo disponível para retornar")
                    if (comprovante.foraHorario) {
                        Text(
                            "Pausa iniciada com autorização do supervisor.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                } else {
                    val duration = comprovante.duracaoSegundos ?: 0
                    val excess = (duration - comprovante.limiteSegundos).coerceAtLeast(0)
                    Text(
                        if (comprovante.excedeuLimite) "Limite excedido" else "Dentro do limite",
                        fontWeight = FontWeight.SemiBold,
                        color = if (comprovante.excedeuLimite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    if (excess > 0) {
                        Text(
                            "${viewModel.formatarTempo(excess)} acima do limite",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        "Limite do período: ${viewModel.formatarTempo(comprovante.limiteSegundos)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Text(
            "A tela voltará automaticamente para a câmera.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp),
        )

        Button(
            onClick = viewModel::concluirComprovante,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) {
            Text("Concluir agora")
        }
    }
}
