package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.pontocafe.app.data.AdminCoffeeRule

@Composable
fun AdminRulesScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Horários e tempo de café")
        Text(
            "As alterações entram em vigor no servidor e valem para todos os dispositivos.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AdminFeedback(viewModel)

        state.regrasCafe.forEach { regra ->
            CoffeeRuleEditor(viewModel, regra)
        }

        Button(
            onClick = viewModel::voltarHome,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Voltar ao painel")
        }
    }
}

@Composable
private fun CoffeeRuleEditor(viewModel: AdminViewModel, regra: AdminCoffeeRule) {
    var inicio by remember(regra) { mutableStateOf(regra.inicio) }
    var fim by remember(regra) { mutableStateOf(regra.fim) }
    var limite by remember(regra) { mutableStateOf(regra.limiteMinutos.toString()) }
    var ativo by remember(regra) { mutableStateOf(regra.ativo) }
    var erroLocal by remember(regra) { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (regra.periodo == "MANHA") "Período da manhã" else "Período da tarde",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(checked = ativo, onCheckedChange = { ativo = it })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = inicio,
                    onValueChange = { inicio = it.take(5) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Início") },
                    placeholder = { Text("08:00") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = fim,
                    onValueChange = { fim = it.take(5) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Fim") },
                    placeholder = { Text("10:00") },
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = limite,
                onValueChange = { limite = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tempo permitido em minutos") },
                singleLine = true,
            )

            erroLocal?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val minutos = limite.toIntOrNull()
                    erroLocal = when {
                        !inicio.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) -> "Informe um horário inicial válido."
                        !fim.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) -> "Informe um horário final válido."
                        inicio >= fim -> "O horário final deve ser posterior ao horário inicial."
                        minutos == null || minutos !in 1..120 -> "O tempo deve ficar entre 1 e 120 minutos."
                        else -> null
                    }
                    if (erroLocal == null && minutos != null) {
                        viewModel.salvarRegraCafe(regra.periodo, inicio, fim, minutos, ativo)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.state.carregando,
            ) {
                Text("Salvar alterações")
            }
        }
    }
}
