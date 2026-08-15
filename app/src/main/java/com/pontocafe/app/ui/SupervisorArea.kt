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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorDestination
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.PausaSupervisor

@Composable
fun SupervisorArea(viewModel: SupervisorViewModel, onClose: () -> Unit) {
    LaunchedEffect(Unit) {
        viewModel.prepararEntrada()
    }

    when (viewModel.state.destination) {
        SupervisorDestination.LOGIN -> SupervisorLoginScreen(viewModel, onClose)
        SupervisorDestination.AO_VIVO -> SupervisorLiveScreen(viewModel, onClose)
        SupervisorDestination.HISTORICO -> SupervisorHistoryScreen(viewModel)
        SupervisorDestination.COLABORADORES -> SupervisorCollaboratorsScreen(viewModel)
        SupervisorDestination.NOVO_COLABORADOR -> SupervisorNewCollaboratorScreen(viewModel)
        SupervisorDestination.BIOMETRIA -> SupervisorBiometricEnrollmentScreen(viewModel)
        SupervisorDestination.RELATORIOS -> SupervisorReportsScreen(viewModel)
    }
}

@Composable
private fun SupervisorLoginScreen(viewModel: SupervisorViewModel, onClose: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PontoCafeHeader("Acesso do Supervisor")
                Text(
                    "Acompanhe pausas, resolva pendências e cadastre biometria facial.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProfilePill("SUPERVISOR")

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("E-mail") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = senha,
                    onValueChange = { senha = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )

                viewModel.state.erro?.let {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Text(
                            it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                Button(
                    onClick = { viewModel.login(email, senha) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = email.isNotBlank() && senha.isNotBlank() && !viewModel.state.carregando,
                ) { Text(if (viewModel.state.carregando) "Entrando..." else "Entrar") }

                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Voltar ao Ponto Café") }
            }
        }
    }
}

@Composable
private fun SupervisorLiveScreen(viewModel: SupervisorViewModel, onClose: () -> Unit) {
    val state = viewModel.state
    val pendentes = state.colaboradores
        .filter { !it.rostoCadastrado }
        .sortedBy { it.nome.lowercase() }
    val acimaDoLimite = state.pausasAtivas.count { pausa ->
        val duracao = pausa.tempoSegundos ?: pausa.duracaoSegundos ?: 0
        pausa.excedeuLimite ?: (duracao > pausa.limiteSegundos)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PontoCafeHeader("Supervisor")
        Text(
            "Acompanhamento operacional em tempo real.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricCard(
                value = pendentes.size.toString(),
                label = "Rostos pendentes",
                modifier = Modifier.weight(1f),
                emphasized = pendentes.isNotEmpty(),
            )
            MetricCard(
                value = state.pausasAtivas.size.toString(),
                label = "Em pausa agora",
                modifier = Modifier.weight(1f),
            )
        }
        MetricCard(
            value = acimaDoLimite.toString(),
            label = "Acima do limite neste momento",
            modifier = Modifier.fillMaxWidth(),
            emphasized = acimaDoLimite > 0,
        )

        if (state.sessaoAdministrativa) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    "Acesso liberado pela sessão administrativa deste dispositivo.",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (pendentes.isNotEmpty()) {
            OperationalAlertCard(
                title = "${pendentes.size} cadastros faciais pendentes",
                text = "Essas pessoas ainda não conseguem utilizar o reconhecimento facial no Ponto Café.",
                actionLabel = "Abrir gestão de rostos",
                onClick = viewModel::abrirColaboradores,
            )
        }

        SectionTitle("Ações rápidas")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::abrirColaboradores, modifier = Modifier.weight(1f)) {
                Text("Colaboradores")
            }
            OutlinedButton(onClick = viewModel::abrirHistorico, modifier = Modifier.weight(1f)) {
                Text("Histórico")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.abrirRelatorios(7) }, modifier = Modifier.weight(1f)) {
                Text("Relatórios")
            }
            OutlinedButton(onClick = viewModel::atualizarAoVivo, modifier = Modifier.weight(1f)) {
                Text(if (state.carregando) "Atualizando..." else "Atualizar")
            }
        }

        state.mensagem?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        state.erro?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item(key = "pending-header") {
                SectionTitle(
                    title = "Pendentes de registro de rosto",
                    subtitle = "${pendentes.size} colaborador(es) aguardando biometria.",
                )
            }

            if (pendentes.isEmpty() && !state.carregando) {
                item(key = "no-pending") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Biometria em dia", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Todos os colaboradores ativos possuem rosto cadastrado.",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            } else {
                items(pendentes, key = { "pending-${it.id}" }) { colaborador ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            InitialAvatar(colaborador.nome)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
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
                                StatusPill("Rosto pendente", positive = false)
                            }
                            Button(onClick = { viewModel.cadastrarOuAtualizarRosto(colaborador) }) {
                                Text("Cadastrar")
                            }
                        }
                    }
                }
            }

            item(key = "active-pause-header") {
                SectionTitle(
                    title = "Pessoas no café agora",
                    subtitle = "${state.pausasAtivas.size} pausa(s) em andamento.",
                )
            }

            if (state.pausasAtivas.isEmpty() && !state.carregando) {
                item(key = "no-active-pause") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Nenhuma pessoa está em pausa neste momento.",
                            modifier = Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(state.pausasAtivas, key = { it.id }) { pausa ->
                    SupervisorPauseCard(viewModel, pausa, ativa = true)
                }
            }
        }

        if (state.sessaoAdministrativa) {
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar ao Ponto")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Voltar ao Ponto") }
                OutlinedButton(onClick = viewModel::sair, modifier = Modifier.weight(1f)) { Text("Sair") }
            }
        }
    }
}

@Composable
private fun SupervisorHistoryScreen(viewModel: SupervisorViewModel) {
    val state = viewModel.state
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PontoCafeHeader("Histórico de hoje")
        Text(
            "Pausas registradas no dia atual, com duração e situação do limite.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MetricCard(
            value = state.historico.size.toString(),
            label = "Registros de pausa hoje",
            modifier = Modifier.fillMaxWidth(),
        )

        state.erro?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(state.historico, key = { it.id }) { pausa ->
                SupervisorPauseCard(viewModel, pausa, ativa = pausa.fimLocal == null)
            }
        }
        Button(onClick = viewModel::voltarAoVivo, modifier = Modifier.fillMaxWidth()) {
            Text("Voltar ao painel")
        }
    }
}

@Composable
private fun SupervisorPauseCard(
    viewModel: SupervisorViewModel,
    pausa: PausaSupervisor,
    ativa: Boolean,
) {
    val duracao = pausa.tempoSegundos ?: pausa.duracaoSegundos ?: 0
    val excedeu = pausa.excedeuLimite ?: (duracao > pausa.limiteSegundos)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (excedeu) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InitialAvatar(pausa.nome)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pausa.nome, fontWeight = FontWeight.SemiBold)
                pausa.setor?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (ativa) {
                        "Saiu às ${pausa.inicioLocal} · ${viewModel.formatarTempo(duracao)} em pausa"
                    } else {
                        "${pausa.inicioLocal} → ${pausa.fimLocal ?: "--:--"} · ${viewModel.formatarTempo(duracao)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                StatusPill(
                    text = if (excedeu) "Acima do limite" else "Dentro do limite",
                    positive = !excedeu,
                )
            }
        }
    }
}
