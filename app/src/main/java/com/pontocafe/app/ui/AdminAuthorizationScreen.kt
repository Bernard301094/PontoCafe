package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.Colaborador

@Composable
fun AdminAuthorizationScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    var selecionado by remember { mutableStateOf<Colaborador?>(null) }
    var busca by remember { mutableStateOf("") }
    var periodo by remember { mutableStateOf("MANHA") }
    var motivo by remember { mutableStateOf("") }

    val filtrados = state.colaboradores.filter {
        busca.isBlank() || it.nome.contains(busca, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            PontoCafeScreenHeader(
                title = "Autorizar pausa fora do horário",
                onBack = viewModel::voltarHome,
                backLabel = "Painel",
            )
        }
        item(key = "intro") {
            Text(
                "Administrador e Supervisor podem gerar este código temporário. Cada autorização fica registrada na auditoria.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "feedback") {
            AdminFeedback(viewModel)
        }

        state.authorizationCode?.let { codigo ->
            item(key = "generated-code") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Código temporário", fontWeight = FontWeight.SemiBold)
                        SelectionContainer {
                            Text(
                                codigo,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text("Colaborador: ${state.authorizationEmployeeName ?: "-"}")
                        Text("Expira em aproximadamente ${state.authorizationExpiresSeconds ?: 0} segundos.")
                        Text(
                            "O código só pode ser usado uma vez. Um novo código para o mesmo período cancela o anterior.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = viewModel::limparAutorizacaoGerada,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Gerar outra autorização") }
                    }
                }
            }
        } ?: run {
            item(key = "search") {
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar colaborador") },
                    placeholder = { Text("Nome") },
                    singleLine = true,
                )
            }

            item(key = "selected") {
                Text(
                    text = selecionado?.let { "Selecionado: ${it.nome}" } ?: "Selecione o colaborador",
                    fontWeight = FontWeight.SemiBold,
                )
            }

            items(filtrados.take(30), key = { "authorization-${it.id}" }) { colaborador ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selecionado = colaborador },
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(14.dp)) {
                        Text(colaborador.nome, fontWeight = FontWeight.SemiBold)
                        val detalhe = listOfNotNull(colaborador.setor, colaborador.turno)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (detalhe.isNotBlank()) {
                            Text(detalhe, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item(key = "period") {
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
            }

            item(key = "reason") {
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Motivo da autorização") },
                    placeholder = { Text("Ex.: atividade operacional terminou após o horário") },
                )
            }

            item(key = "generate") {
                Button(
                    onClick = {
                        selecionado?.let { viewModel.gerarAutorizacao(it, periodo, motivo) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selecionado != null && motivo.trim().length >= 2 && !state.carregando,
                ) {
                    Text("Gerar código de 6 dígitos")
                }
            }
        }
    }
}
