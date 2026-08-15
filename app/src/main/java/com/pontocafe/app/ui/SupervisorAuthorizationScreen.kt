package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.Colaborador

@Composable
fun SupervisorAuthorizationScreen(viewModel: SupervisorViewModel) {
    val state = viewModel.state
    var selecionado by remember { mutableStateOf<Colaborador?>(null) }
    var busca by remember { mutableStateOf("") }
    var periodo by remember { mutableStateOf("MANHA") }
    var motivo by remember { mutableStateOf("") }

    val filtrados = state.colaboradores.filter {
        busca.isBlank() || it.nome.contains(busca, ignoreCase = true)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Autorizar pausa fora do horário")
        Text(
            "Gere um código temporário de uso único para uma exceção operacional fora da janela normal.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.mensagem?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        state.erro?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    error,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        state.authorizationCode?.let { codigo ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Código temporário", fontWeight = FontWeight.SemiBold)
                    SelectionContainer {
                        Text(
                            codigo,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Text("Colaborador: ${state.authorizationEmployeeName ?: "—"}")
                    Text("Validade: aproximadamente ${state.authorizationExpiresSeconds ?: 0} segundos.")
                    Text(
                        "O código só pode ser usado uma vez. Gerar outro código para o mesmo período cancela o anterior.",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Button(
                        onClick = viewModel::limparAutorizacaoGerada,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Gerar outra autorização") }
                }
            }
        } ?: run {
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar colaborador") },
                placeholder = { Text("Digite o nome") },
                singleLine = true,
            )

            Text(
                text = selecionado?.let { "Selecionado: ${it.nome}" } ?: "Selecione o colaborador",
                fontWeight = FontWeight.SemiBold,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtrados.take(40), key = { it.id }) { colaborador ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selecionado = colaborador },
                        colors = if (selecionado?.id == colaborador.id) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            CardDefaults.cardColors()
                        },
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(colaborador.nome, fontWeight = FontWeight.SemiBold)
                            val detalhe = listOfNotNull(colaborador.setor, colaborador.turno)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            if (detalhe.isNotBlank()) {
                                Text(
                                    detalhe,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = periodo == "MANHA",
                    onClick = { periodo = "MANHA" },
                    label = { Text("Manhã") },
                )
                FilterChip(
                    selected = periodo == "TARDE",
                    onClick = { periodo = "TARDE" },
                    label = { Text("Tarde") },
                )
            }

            OutlinedTextField(
                value = motivo,
                onValueChange = { motivo = it.take(300) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Motivo da exceção") },
                placeholder = { Text("Ex.: atividade operacional terminou após o horário") },
                minLines = 2,
                maxLines = 4,
            )

            Button(
                onClick = { selecionado?.let { viewModel.gerarAutorizacao(it, periodo, motivo) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = selecionado != null && motivo.trim().length >= 2 && !state.carregando,
            ) {
                Text(if (state.carregando) "Gerando..." else "Gerar código de 6 dígitos")
            }
        }

        OutlinedButton(
            onClick = viewModel::voltarAoVivo,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Voltar ao acompanhamento")
        }
    }
}
