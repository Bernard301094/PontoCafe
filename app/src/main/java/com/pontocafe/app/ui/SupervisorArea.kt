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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorDestination
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.PausaSupervisor

@Composable
fun SupervisorArea(viewModel: SupervisorViewModel, onClose: () -> Unit) {
    when (viewModel.state.destination) {
        SupervisorDestination.LOGIN -> SupervisorLoginScreen(viewModel, onClose)
        SupervisorDestination.AO_VIVO -> SupervisorLiveScreen(viewModel, onClose)
        SupervisorDestination.HISTORICO -> SupervisorHistoryScreen(viewModel)
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
        PontoCafeHeader("Acesso do Supervisor")
        Text(
            "Perfil somente leitura para acompanhar as pausas de café.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 18.dp),
        )
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
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        viewModel.state.erro?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
        }
        Button(
            onClick = { viewModel.login(email, senha) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = email.isNotBlank() && senha.isNotBlank() && !viewModel.state.carregando,
        ) { Text("Entrar") }
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Voltar ao Ponto Café") }
    }
}

@Composable
private fun SupervisorLiveScreen(viewModel: SupervisorViewModel, onClose: () -> Unit) {
    val state = viewModel.state
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Pessoas no café")
        Text(
            "Somente consulta. O Supervisor não cadastra, exclui nem altera dados.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::atualizarAoVivo, modifier = Modifier.weight(1f)) {
                Text("Atualizar")
            }
            OutlinedButton(onClick = viewModel::abrirHistorico, modifier = Modifier.weight(1f)) {
                Text("Histórico")
            }
        }
        state.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.pausasAtivas.isEmpty() && !state.carregando) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("Nenhuma pessoa está em pausa neste momento.", modifier = Modifier.padding(18.dp))
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.pausasAtivas, key = { it.id }) { pausa ->
                SupervisorPauseCard(viewModel, pausa, ativa = true)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text("Voltar ao Ponto")
            }
            OutlinedButton(onClick = viewModel::sair, modifier = Modifier.weight(1f)) {
                Text("Sair")
            }
        }
    }
}

@Composable
private fun SupervisorHistoryScreen(viewModel: SupervisorViewModel) {
    val state = viewModel.state
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Histórico de hoje")
        Text(
            "Pessoas que registraram pausa para café hoje.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.historico, key = { it.id }) { pausa ->
                SupervisorPauseCard(viewModel, pausa, ativa = pausa.fimLocal == null)
            }
        }
        Button(onClick = viewModel::voltarAoVivo, modifier = Modifier.fillMaxWidth()) {
            Text("Voltar ao acompanhamento")
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pausa.nome, fontWeight = FontWeight.SemiBold)
            val detalhe = listOfNotNull(pausa.matricula, pausa.setor).filter { it.isNotBlank() }.joinToString(" · ")
            if (detalhe.isNotBlank()) {
                Text(detalhe, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (ativa) {
                    "Saiu às ${pausa.inicioLocal} · ${viewModel.formatarTempo(duracao)} em pausa"
                } else {
                    "${pausa.inicioLocal} → ${pausa.fimLocal ?: "--:--"} · ${viewModel.formatarTempo(duracao)}"
                },
            )
            Text(
                if (excedeu) "Acima do limite de 15 minutos" else "Dentro do limite",
                color = if (excedeu) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
