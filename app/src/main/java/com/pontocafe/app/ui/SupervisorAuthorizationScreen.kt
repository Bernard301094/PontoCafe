package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    val filtrados = state.colaboradores
        .filter {
            busca.isBlank() ||
                it.nome.contains(busca, ignoreCase = true) ||
                it.setor?.contains(busca, ignoreCase = true) == true
        }
        .sortedBy { it.nome.lowercase() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Liberações fora do horário")
        Text(
            "Libere a pausa antes de o colaborador ir ao Ponto. Nenhum código precisa ser informado no terminal.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "Como funciona",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Selecione a pessoa, o período e o motivo. Depois de liberar, o reconhecimento facial no Ponto valida a autorização automaticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.erro?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    error,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        if (state.authorizationCode != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = LocalPontoCafeSemanticColors.current.successContainer,
                border = BorderStroke(
                    1.dp,
                    LocalPontoCafeSemanticColors.current.success.copy(alpha = 0.35f),
                ),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = LocalPontoCafeSemanticColors.current.success,
                            modifier = Modifier.size(30.dp),
                        )
                        Column {
                            Text(
                                "Pausa liberada",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                state.authorizationEmployeeName ?: selecionado?.nome ?: "Colaborador",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        "O colaborador já pode ir ao Ponto. Ao reconhecer o rosto, a liberação será encontrada automaticamente.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Período: ${if (periodo == "MANHA") "Manhã" else "Tarde"} · Validade aproximada: ${formatAuthorizationValidity(state.authorizationExpiresSeconds ?: 0)} · Uso único",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = {
                            viewModel.limparAutorizacaoGerada()
                            selecionado = null
                            busca = ""
                            motivo = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Liberar outra pessoa")
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar colaborador") },
                placeholder = { Text("Digite o nome") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )

            selecionado?.let { colaborador ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                colaborador.nome,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val detalhe = listOfNotNull(colaborador.setor, colaborador.turno)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            if (detalhe.isNotBlank()) {
                                Text(
                                    detalhe,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selecionado",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Text(
                if (selecionado == null) "Selecione o colaborador" else "Alterar colaborador",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(filtrados.take(40), key = { it.id }) { colaborador ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selecionado = colaborador },
                        shape = RoundedCornerShape(16.dp),
                        colors = if (selecionado?.id == colaborador.id) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        },
                        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
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
                label = { Text("Motivo da liberação") },
                placeholder = { Text("Ex.: atividade operacional terminou após o horário") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
            )

            Button(
                onClick = { selecionado?.let { viewModel.gerarAutorizacao(it, periodo, motivo) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = selecionado != null && motivo.trim().length >= 2 && !state.carregando,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    if (state.carregando) "Liberando..." else "Liberar pausa",
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        OutlinedButton(
            onClick = viewModel::voltarAoVivo,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Voltar ao acompanhamento")
        }
    }
}

private fun formatAuthorizationValidity(seconds: Int): String = when {
    seconds <= 0 -> "alguns minutos"
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60}s"
}
