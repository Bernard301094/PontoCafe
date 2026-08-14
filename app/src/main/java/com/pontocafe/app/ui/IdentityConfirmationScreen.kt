package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel

@Composable
fun IdentityConfirmationScreen(viewModel: PontoCafeViewModel) {
    val state = viewModel.state
    val identificacao = state.identificacao ?: return
    val colaborador = identificacao.colaborador ?: return
    val finalizando = identificacao.acaoSugerida == "FINALIZAR"

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (finalizando) "Retorno identificado" else "Rosto identificado",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = colaborador.nome,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        val detalhe = listOfNotNull(colaborador.setor, colaborador.turno)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (detalhe.isNotBlank()) {
            Text(detalhe, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (finalizando) {
                    Text("Pausa em andamento", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Início registrado às ${identificacao.pausaAberta?.inicioLocal ?: "--:--"}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    identificacao.pausaAberta?.let {
                        Text(
                            "Tempo decorrido: ${viewModel.formatarTempo(it.tempoDecorridoSegundos)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("É você e deseja registrar seu retorno agora?")
                } else {
                    Text("É você?", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (identificacao.dentroHorario == true) {
                            "Ao confirmar, o início da sua pausa será registrado imediatamente."
                        } else {
                            "Ao confirmar, será solicitada a autorização temporária do supervisor porque você está fora do horário normal."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.erro?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::rejeitarIdentidade,
                modifier = Modifier.weight(1f),
                enabled = !state.carregando,
            ) {
                Text("Não sou eu")
            }
            Button(
                onClick = viewModel::confirmarIdentidade,
                modifier = Modifier.weight(1f),
                enabled = !state.carregando,
            ) {
                Text(if (finalizando) "Finalizar pausa" else "Sim, sou eu")
            }
        }
    }
}
