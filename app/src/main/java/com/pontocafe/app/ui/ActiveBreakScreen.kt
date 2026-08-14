package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel

@Composable
fun ActiveBreakScreen(viewModel: PontoCafeViewModel) {
    val state = viewModel.state
    val pausa = state.pausaAtiva ?: return
    val elapsed = state.elapsedSeconds
    val limit = pausa.limiteSegundos
    val remaining = (limit - elapsed).coerceAtLeast(0)
    val exceeded = elapsed >= limit
    val progress = if (limit > 0) (elapsed.toFloat() / limit).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PontoCafeHeader("Pausa em andamento")
        Spacer(Modifier.height(24.dp))
        Text(state.selecionado?.nome.orEmpty(), fontWeight = FontWeight.SemiBold)
        Text(
            formatTime(elapsed),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
        )
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            if (exceeded) {
                "Limite ${formatTime(limit)} · excesso ${formatTime(elapsed - limit)}"
            } else {
                "Restam ${formatTime(remaining)} de ${formatTime(limit)}"
            },
            color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (pausa.foraHorario) Text("Pausa autorizada fora do horário")
        MessageCard(viewModel)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = viewModel::iniciarFinalizacao,
            modifier = Modifier.fillMaxWidth().height(60.dp),
        ) { Text("FINALIZAR PAUSA") }
    }
}
